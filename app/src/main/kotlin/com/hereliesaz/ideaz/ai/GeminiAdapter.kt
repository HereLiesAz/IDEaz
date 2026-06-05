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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private const val DEFAULT_MODEL = "gemini-2.0-flash"
private const val MAX_TOOL_ROUNDS = 10

class GeminiAdapter(
    private val apiKey: String,
    private val tools: IdeTools,
    private val model: String = DEFAULT_MODEL,
) : ConversationalAiClient {

    private val client: Client by lazy { Client.builder().apiKey(apiKey).build() }
    private val toolConfig: GenerateContentConfig by lazy { buildToolConfig() }

    override suspend fun chat(messages: List<ChatMessage>): String = withContext(Dispatchers.IO) {
        val contents = messages.map { it.toContent() }.toMutableList()

        var rounds = 0
        while (rounds < MAX_TOOL_ROUNDS) {
            val response = client.models.generateContent(model, contents, toolConfig)

            val calls = response.functionCalls()
            if (calls.isNullOrEmpty()) {
                return@withContext response.text() ?: "No response from Gemini."
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
                val args = call.args().orElse(emptyMap())
                val output = dispatchTool(name, args)
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

        "Error: Tool-use loop exceeded $MAX_TOOL_ROUNDS rounds."
    }

    internal fun dispatchTool(name: String, args: Any?): String {
        @Suppress("UNCHECKED_CAST")
        val map = (args as? Map<String, Any?>).orEmpty()
        val stringArgs = map.mapValues { (_, v) -> v?.toString() }
        return dispatchIdeTool(name, stringArgs, tools)
    }

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
