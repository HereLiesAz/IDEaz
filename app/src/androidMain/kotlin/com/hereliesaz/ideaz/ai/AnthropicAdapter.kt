package com.hereliesaz.ideaz.ai

import com.hereliesaz.ideaz.ai.AiEditApproval
import com.hereliesaz.ideaz.ai.AiEditApprovalRequiredException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

private val MUTATING_TOOLS = setOf("write_file", "apply_patch")

class AnthropicAdapter(
    private val apiKey: String,
    private val tools: IdeTools,
    private val modelResolver: () -> String,
    private val httpClient: OkHttpClient = OpenAiCompatibleAdapter.sharedClient
) : ConversationalAiClient {

    private val jsonMediaType = "application/json; charset=utf-8".toMediaType()

    private var resolvedModel: String? = null

    override suspend fun chat(messages: List<ChatMessage>): String = withContext(Dispatchers.IO) {
        if (resolvedModel == null) {
            resolvedModel = modelResolver()
        }
        val currentModel = resolvedModel!!

        val history = messages.map { it.toAnthropicMessage() }.toMutableList()

        // Same checkpoint/review/approval contract LocalLlmAdapter and
        // GeminiAdapter use — see AiEditApproval's doc comment.
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
            throw AiEditApprovalRequiredException(AiEditApproval(review, response, source = "Claude"))
        }

        fun dispatchMutatingTool(name: String, stringArgs: Map<String, String?>): String {
            return try {
                // Creating the checkpoint used to sit outside this try, so a snapshot
                // failure escaped as a raw exception instead of a clean tool result.
                val checkpoint = editCheckpoint
                    ?: tools.createEditCheckpoint("IDEaz: checkpoint before Claude edit").also { editCheckpoint = it }
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
        while (rounds < OpenAiCompatibleAdapter.MAX_TOOL_ROUNDS) {
            val response = try {
                postMessages(history, currentModel)
            } catch (e: CancellationException) {
                // A mutating tool call in an earlier round may have already
                // written to disk; without this, cancelling mid-loop left the
                // edit applied with no review card and an orphaned checkpoint.
                restorePendingEdit()
                throw e
            } catch (e: Exception) {
                // Same failure mode for a network/API error after a successful
                // write in an earlier round (a rate limit here is common).
                restorePendingEdit()
                return@withContext "Error: Anthropic request failed: ${e.message}"
            }

            // An unexpected-but-2xx response shape (no "content" array) used to
            // return here directly, bypassing complete() entirely - if a mutating
            // tool already ran in an earlier round, the edit was left applied
            // with no review and an orphaned checkpoint.
            val contentArray = response["content"] as? JsonArray ?: run {
                restorePendingEdit()
                return@withContext "Error: unexpected response shape from $currentModel."
            }

            // Extract text and tool_use blocks
            var textContent = ""
            val toolUses = mutableListOf<JsonObject>()

            for (block in contentArray) {
                if (block !is JsonObject) continue
                when ((block["type"] as? JsonPrimitive)?.content) {
                    "text" -> textContent += (block["text"] as? JsonPrimitive)?.content.orEmpty()
                    "tool_use" -> toolUses.add(block)
                }
            }

            if (toolUses.isEmpty()) {
                return@withContext complete(textContent.ifBlank { "No response from $currentModel." })
            }

            // Echo assistant turn
            history.add(buildJsonObject {
                put("role", "assistant")
                put("content", contentArray)
            })

            val toolResults = mutableListOf<JsonObject>()
            for (call in toolUses) {
                val toolUseId = (call["id"] as? JsonPrimitive)?.content.orEmpty()
                val name = (call["name"] as? JsonPrimitive)?.content.orEmpty()
                val argsObj = call["input"] as? JsonObject ?: buildJsonObject {}

                val argMap = argsObj.entries.associate { (k, v) ->
                    k to (v as? JsonPrimitive)?.contentOrNullSafe()
                }

                val output = if (name in MUTATING_TOOLS) {
                    withContext(NonCancellable) { dispatchMutatingTool(name, argMap) }
                } else {
                    dispatchIdeTool(name, argMap, tools)
                }

                toolResults.add(buildJsonObject {
                    put("type", "tool_result")
                    put("tool_use_id", toolUseId)
                    put("content", output)
                })
            }

            history.add(buildJsonObject {
                put("role", "user")
                putJsonArray("content") {
                    toolResults.forEach { add(it) }
                }
            })

            rounds++
        }
        restorePendingEdit()
        "Error: Tool-use loop exceeded ${OpenAiCompatibleAdapter.MAX_TOOL_ROUNDS} rounds."
    }

    private fun postMessages(history: List<JsonObject>, currentModel: String): JsonObject {
        // Anthropic requires system prompt to be top-level, not in messages list.
        val systemMessages = history.filter { (it["role"] as? JsonPrimitive)?.content == "system" }
        val userAssistantMessages = history.filter { (it["role"] as? JsonPrimitive)?.content != "system" }
        
        val systemText = systemMessages.joinToString("\n") { 
            val content = it["content"]
            if (content is JsonPrimitive) content.content else content.toString() 
        }

        val body = buildJsonObject {
            put("model", currentModel)
            put("max_tokens", 4096)
            if (systemText.isNotBlank()) {
                put("system", systemText)
            }
            putJsonArray("messages") { userAssistantMessages.forEach { add(it) } }
            putJsonArray("tools") {
                IdeToolSchema.all.forEach { spec -> add(spec.toAnthropicTool()) }
            }
        }.toString()

        val request = Request.Builder()
            .url("https://api.anthropic.com/v1/messages")
            .addHeader("x-api-key", apiKey)
            .addHeader("anthropic-version", "2023-06-01")
            .addHeader("content-type", "application/json")
            .post(body.toRequestBody(jsonMediaType))
            .build()

        httpClient.newCall(request).execute().use { resp ->
            val text = resp.body.string()
            if (!resp.isSuccessful) {
                error("Anthropic call failed: HTTP ${resp.code}: ${text.take(500)}")
            }
            return OpenAiCompatibleAdapter.JSON.parseToJsonElement(text) as? JsonObject
                ?: error("Unexpected response shape from Anthropic: ${text.take(200)}")
        }
    }

    private fun ChatMessage.toAnthropicMessage(): JsonObject = buildJsonObject {
        // Map system role to user for now if we don't extract it, but we extract it in postMessages
        put("role", if (role == "system") "system" else if (role == "model" || role == "assistant") "assistant" else "user")
        
        val hasImage = parts.any { it is ChatPart.Image }
        if (!hasImage) {
            val text = parts.joinToString("\n") { part ->
                when (part) {
                    is ChatPart.Text -> part.text
                    is ChatPart.FileBlob -> "(file ${part.fileName ?: "[unnamed]"}: not forwarded to this provider)"
                    is ChatPart.Image -> ""
                }
            }
            put("content", text)
        } else {
            putJsonArray("content") {
                for (part in parts) {
                    when (part) {
                        is ChatPart.Text -> add(buildJsonObject {
                            put("type", "text")
                            put("text", part.text)
                        })
                        is ChatPart.Image -> {
                            val b64 = android.util.Base64.encodeToString(part.bytes, android.util.Base64.NO_WRAP)
                            add(buildJsonObject {
                                put("type", "image")
                                putJsonObject("source") {
                                    put("type", "base64")
                                    put("media_type", part.mimeType)
                                    put("data", b64)
                                }
                            })
                        }
                        is ChatPart.FileBlob -> add(buildJsonObject {
                            put("type", "text")
                            put("text", "(file ${part.fileName ?: "[unnamed]"}: not forwarded to this provider)")
                        })
                    }
                }
            }
        }
    }

    private fun JsonPrimitive.contentOrNullSafe(): String? = if (isString) content else content

    private fun AiToolSpec.toAnthropicTool(): JsonElement = buildJsonObject {
        put("name", name)
        put("description", description)
        putJsonObject("input_schema") {
            put("type", "object")
            putJsonObject("properties") {
                params.forEach { param ->
                    putJsonObject(param.name) {
                        put("type", param.type)
                        put("description", param.description)
                    }
                }
            }
            putJsonArray("required") {
                params.filter { it.required }.forEach { add(JsonPrimitive(it.name)) }
            }
        }
    }
}
