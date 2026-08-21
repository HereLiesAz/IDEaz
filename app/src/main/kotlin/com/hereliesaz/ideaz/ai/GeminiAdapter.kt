package com.hereliesaz.ideaz.ai

import com.google.genai.Client
import com.google.genai.types.Blob
import com.google.genai.types.Content
import com.google.genai.types.FunctionDeclaration
import com.google.genai.types.GenerateContentConfig
import com.google.genai.types.Part
import com.google.genai.types.Schema
import com.google.genai.types.Tool
import com.google.genai.types.Type
import com.hereliesaz.ideaz.ai.local.LocalEditApproval
import com.hereliesaz.ideaz.ai.local.LocalEditApprovalRequiredException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext

internal const val DEFAULT_GEMINI_MODEL = "gemini-2.0-flash"
private const val MAX_TOOL_ROUNDS = 10
private val MUTATING_TOOLS = setOf("write_file", "apply_patch")

class GeminiAdapter(
    private val apiKey: String,
    private val tools: IdeTools,
    private val model: String = DEFAULT_GEMINI_MODEL,
) : ConversationalAiClient {

    private val client: Client by lazy { Client.builder().apiKey(apiKey).build() }
    private val toolConfig: GenerateContentConfig by lazy { buildToolConfig() }

    override suspend fun chat(messages: List<ChatMessage>): String = withContext(Dispatchers.IO) {
        val contents = messages.map { it.toContent() }.toMutableList()

        // Same checkpoint/review/approval contract LocalLlmAdapter uses, reused
        // as-is (see LocalEditApproval's doc comment): a checkpoint is opened on
        // the first mutating tool call in this turn and spans every round until
        // the model stops calling tools, at which point complete() below either
        // clears a no-op checkpoint or hands it to the UI for explicit approval
        // instead of silently reloading unreviewed edits.
        var editCheckpoint: IdeEditCheckpoint? = null
        var expectedEditFingerprint: String? = null

        fun restorePendingEdit(): Boolean {
            val checkpoint = editCheckpoint ?: return true
            val fingerprint = expectedEditFingerprint ?: return false
            return runCatching { tools.restoreEditCheckpoint(checkpoint, fingerprint) }.isSuccess
        }

        fun complete(response: String): String {
            val checkpoint = editCheckpoint ?: return response
            val review = try {
                tools.reviewEdits(checkpoint)
            } catch (e: Exception) {
                val restored = restorePendingEdit()
                return if (restored) {
                    "Error: the edit could not be validated and was restored."
                } else {
                    "Error: the edit could not be validated or safely restored. Review project files."
                }
            }
            if (review.validationErrors.isNotEmpty()) {
                val restored = restorePendingEdit()
                return if (restored) {
                    "Error: the edit failed validation and was restored."
                } else {
                    "Error: the edit failed validation, but later file changes prevented automatic restore."
                }
            }
            if (review.changedFiles.isEmpty()) {
                runCatching { tools.discardEditCheckpoint(checkpoint) }
                return response
            }
            throw LocalEditApprovalRequiredException(LocalEditApproval(review, response, source = "Gemini"))
        }

        fun dispatchMutatingTool(name: String, stringArgs: Map<String, String?>): String {
            return try {
                // Creating the checkpoint used to sit outside this try, so a snapshot
                // failure (e.g. an unwritable checkpoint directory) escaped as a raw
                // exception instead of the clean "Error: could not complete ..." result
                // every other failure in this function produces.
                val checkpoint = editCheckpoint
                    ?: tools.createEditCheckpoint("IDEaz: checkpoint before Gemini edit").also { editCheckpoint = it }
                tools.captureToolEdit(checkpoint, name, stringArgs)
                expectedEditFingerprint = tools.reviewEdits(checkpoint).contentFingerprint
                    .also { tools.updateEditCheckpointFingerprint(checkpoint, it) }
                tools.markEditMutationStarted(checkpoint)
                dispatchIdeTool(name, stringArgs, tools).also {
                    expectedEditFingerprint = tools.reviewEdits(checkpoint).contentFingerprint
                        .also { fingerprint ->
                            tools.updateEditCheckpointFingerprint(checkpoint, fingerprint)
                            tools.markEditAwaitingReview(checkpoint)
                        }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                restorePendingEdit()
                "Error: could not complete $name: ${e.message}"
            }
        }

        var rounds = 0
        while (rounds < MAX_TOOL_ROUNDS) {
            val response = try {
                client.models.generateContent(model, contents, toolConfig)
            } catch (e: CancellationException) {
                // A mutating tool call in an earlier round may have already
                // written to disk; without this, cancelling mid-loop (e.g. a
                // new overlay prompt cancelling this job) left the edit applied
                // with no review card and an orphaned checkpoint.
                restorePendingEdit()
                throw e
            } catch (e: Exception) {
                // Same failure mode for a network/API error after a successful
                // write in an earlier round (an HTTP error here is common on
                // rate-limited backends) - restore instead of leaving the edit
                // applied and unreviewed.
                restorePendingEdit()
                return@withContext "Error: Gemini request failed: ${e.message}"
            }

            val calls = response.functionCalls()
            if (calls.isNullOrEmpty()) {
                return@withContext complete(response.text() ?: "No response from Gemini.")
            }

            // Add the model's response (with function calls) to history
            response.candidates()
                .orElse(null)
                ?.firstOrNull()
                ?.content()
                ?.orElse(null)
                ?.let { contents.add(it) }

            for (call in calls) {
                val name = call.name().orElse("")
                val stringArgs = toStringArgs(call.args().orElse(emptyMap()))
                val output = if (name in MUTATING_TOOLS) {
                    withContext(NonCancellable) { dispatchMutatingTool(name, stringArgs) }
                } else {
                    dispatchIdeTool(name, stringArgs, tools)
                }
                // Build the function-response turn with the SDK's own helpers.
                // Content.fromParts sets role "user" — which is what the google-genai
                // SDK / Gemini API expect for function responses. The previous manual
                // role("function") was wrong and stalled the tool-use loop until it
                // hit MAX_TOOL_ROUNDS.
                contents.add(
                    Content.fromParts(
                        Part.fromFunctionResponse(name, mapOf<String, Any>("output" to output))
                    )
                )
            }
            rounds++
        }

        restorePendingEdit()
        "Error: Tool-use loop exceeded $MAX_TOOL_ROUNDS rounds."
    }

    private fun toStringArgs(args: Any?): Map<String, String?> {
        @Suppress("UNCHECKED_CAST")
        val map = (args as? Map<String, Any?>).orEmpty()
        return map.mapValues { (_, v) -> v?.toString() }
    }

    internal fun dispatchTool(name: String, args: Any?): String =
        dispatchIdeTool(name, toStringArgs(args), tools)

    internal fun testDispatchTool(name: String, args: Map<String, Any?>): String =
        dispatchTool(name, args)

    private fun buildToolConfig(): GenerateContentConfig {
        val declarations = IdeToolSchema.all.map { spec -> spec.toFunctionDeclaration() }
        val tool = Tool.builder()
            .functionDeclarations(declarations)
            .build()
        return GenerateContentConfig.builder().tools(listOf(tool)).build()
    }
}

/**
 * Tool-less Gemini client for a single, explicitly approved consultation.
 * It receives only the bounded preview shown in consent UI: no repo map,
 * conversation history, images, credentials, or IDE mutation tools.
 */
class GeminiConsultant(
    private val apiKey: String,
    private val model: String = DEFAULT_GEMINI_MODEL,
) {
    private val client: Client by lazy { Client.builder().apiKey(apiKey).build() }
    private val config: GenerateContentConfig by lazy { GenerateContentConfig.builder().build() }

    suspend fun consult(prompt: String): String = withContext(Dispatchers.IO) {
        val response = client.models.generateContent(
            model,
            listOf(ChatMessage("user", prompt).toContent()),
            config,
        )
        response.text() ?: "No response from Gemini consultant."
    }
}

private fun AiToolSpec.toFunctionDeclaration(): FunctionDeclaration {
    val properties = params.associate { param ->
        param.name to Schema.builder()
            .type(param.type.toSchemaType())
            .description(param.description)
            .build()
    }
    val required = params.filter { it.required }.map { it.name }
    val parametersSchema = Schema.builder()
        .type(Type.Known.OBJECT)
        .properties(properties)
        .required(required)
        .build()
    return FunctionDeclaration.builder()
        .name(name)
        .description(description)
        .parameters(parametersSchema)
        .build()
}

private fun String.toSchemaType(): Type.Known = when (this) {
    "string" -> Type.Known.STRING
    "number" -> Type.Known.NUMBER
    "boolean" -> Type.Known.BOOLEAN
    "object" -> Type.Known.OBJECT
    "array" -> Type.Known.ARRAY
    else -> error("Unsupported AiToolParam type '$this'.")
}

private fun ChatMessage.toContent(): Content {
    val sdkRole = when (role) {
        "user", "system", "tool" -> "user"
        "model", "assistant"     -> "model"
        else -> {
            // Gemini only accepts "user"/"model"; never crash the whole chat on an
            // unexpected role — degrade to "user" and log it.
            android.util.Log.w("GeminiAdapter", "Unexpected ChatMessage role '$role'; treating as user.")
            "user"
        }
    }
    val sdkParts = parts.map { part ->
        when (part) {
            is ChatPart.Text -> Part.builder().text(part.text).build()
            is ChatPart.Image -> Part.builder()
                .inlineData(Blob.builder().mimeType(part.mimeType).data(part.bytes).build())
                .build()
            // Gemini natively supports PDFs and other binary blobs via the same
            // inline-data part shape — no special-casing needed beyond mime.
            is ChatPart.FileBlob -> Part.builder()
                .inlineData(Blob.builder().mimeType(part.mimeType).data(part.bytes).build())
                .build()
        }
    }
    return Content.builder()
        .role(sdkRole)
        .parts(sdkParts)
        .build()
}
