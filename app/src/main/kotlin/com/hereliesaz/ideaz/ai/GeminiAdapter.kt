package com.hereliesaz.ideaz.ai

import com.google.genai.Client
import com.google.genai.types.Content
import com.google.genai.types.FunctionDeclaration
import com.google.genai.types.FunctionResponse
import com.google.genai.types.GenerateContentConfig
import com.google.genai.types.Part
import com.google.genai.types.Schema
import com.google.genai.types.Tool
import com.google.genai.types.Type
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private const val MODEL = "gemini-2.0-flash"
private const val MAX_TOOL_ROUNDS = 10

class GeminiAdapter(
    private val apiKey: String,
    private val tools: IdeTools
) : ConversationalAiClient {

    private val client: Client by lazy { Client.builder().apiKey(apiKey).build() }
    private val toolConfig: GenerateContentConfig by lazy { buildToolConfig() }

    override suspend fun chat(messages: List<ChatMessage>): String = withContext(Dispatchers.IO) {
        val contents = messages.map { it.toContent() }.toMutableList()

        var rounds = 0
        while (rounds < MAX_TOOL_ROUNDS) {
            val response = client.models.generateContent(MODEL, contents, toolConfig)

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
                val funcResponse = FunctionResponse.builder()
                    .name(name)
                    .response(mapOf("output" to output))
                    .build()
                // Role for function-response content in the google-genai SDK.
                // The older generativeai SDK uses "function"; if the tool-use loop does not
                // advance after providing results, try "tool" here instead.
                contents.add(
                    Content.builder()
                        .role("function")
                        .parts(listOf(Part.builder().functionResponse(funcResponse).build()))
                        .build()
                )
            }
            rounds++
        }

        "Error: Tool-use loop exceeded $MAX_TOOL_ROUNDS rounds."
    }

    internal fun dispatchTool(name: String, args: Any?): String {
        @Suppress("UNCHECKED_CAST")
        val map = args as? Map<String, Any?> ?: emptyMap()
        return when (name) {
            "read_file"   -> tools.readFile(map["path"] as? String ?: "")
            "write_file"  -> tools.writeFile(
                                map["path"]    as? String ?: "",
                                map["content"] as? String ?: ""
                            )
            "list_files"  -> tools.listFiles(map["path"] as? String ?: ".")
            "apply_patch" -> tools.applyPatch(map["patch"] as? String ?: "")
            else          -> "Error: Unknown tool '$name'."
        }
    }

    internal fun testDispatchTool(name: String, args: Map<String, Any?>): String =
        dispatchTool(name, args)

    private fun buildToolConfig(): GenerateContentConfig {
        fun str(description: String): Schema = Schema.builder()
            .type(Type.Known.STRING)
            .description(description)
            .build()

        fun obj(vararg props: Pair<String, Schema>, required: List<String>): Schema =
            Schema.builder()
                .type(Type.Known.OBJECT)
                .properties(props.toMap())
                .required(required)
                .build()

        val readFile = FunctionDeclaration.builder()
            .name("read_file")
            .description("Read the full text content of a project file. Path is relative to project root.")
            .parameters(obj("path" to str("Relative file path, e.g. 'index.html'"), required = listOf("path")))
            .build()

        val writeFile = FunctionDeclaration.builder()
            .name("write_file")
            .description("Overwrite a project file. Creates parent directories. Returns 'OK' or 'Error: ...'.")
            .parameters(obj(
                "path"    to str("Relative file path"),
                "content" to str("Full text content to write"),
                required = listOf("path", "content")
            ))
            .build()

        val listFiles = FunctionDeclaration.builder()
            .name("list_files")
            .description("List files and subdirectories at the given path. Use '.' for the project root.")
            .parameters(obj("path" to str("Relative directory path"), required = listOf("path")))
            .build()

        val applyPatch = FunctionDeclaration.builder()
            .name("apply_patch")
            .description("Apply a unified diff patch to the working tree. Returns 'OK' or 'Error: ...'.")
            .parameters(obj("patch" to str("Unified diff patch string"), required = listOf("patch")))
            .build()

        val tool = Tool.builder()
            .functionDeclarations(listOf(readFile, writeFile, listFiles, applyPatch))
            .build()

        return GenerateContentConfig.builder().tools(listOf(tool)).build()
    }
}

private fun ChatMessage.toContent(): Content {
    val sdkRole = when (role) {
        "user"  -> "user"
        "model" -> "model"
        else    -> error("Unsupported ChatMessage role '$role'. Expected \"user\" or \"model\".")
    }
    return Content.builder()
        .role(sdkRole)
        .parts(listOf(Part.builder().text(content).build()))
        .build()
}
