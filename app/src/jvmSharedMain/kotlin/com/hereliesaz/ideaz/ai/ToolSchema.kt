package com.hereliesaz.ideaz.ai

/**
 * Provider-neutral description of a single tool argument.
 *
 * Adapters convert this into their provider's native schema (Gemini's
 * [com.google.genai.types.Schema], OpenAI-compatible JSON Schema, etc.).
 */
data class AiToolParam(
    val name: String,
    val type: String, // JSON Schema type: "string", "number", "boolean", "object", "array"
    val description: String,
    val required: Boolean = true,
)

/**
 * Provider-neutral description of one callable tool.
 *
 * @param name canonical tool name; must match the [IdeTools] dispatch key.
 * @param description model-facing description; what the tool does and when to use it.
 * @param params ordered parameter list.
 */
data class AiToolSpec(
    val name: String,
    val description: String,
    val params: List<AiToolParam>,
)

/**
 * The set of tools the AI can invoke against the project sandbox. Adapters
 * read from [all] and translate to their wire format. Tool dispatch itself
 * lives in each adapter (currently [GeminiAdapter.dispatchTool] and
 * [OpenAiCompatibleAdapter.dispatchTool]) so each adapter can decode args
 * from its provider's native shape.
 */
object IdeToolSchema {
    val readFile = AiToolSpec(
        name = "read_file",
        description = "Read the full text content of a project file. Path is relative to project root.",
        params = listOf(
            AiToolParam("path", "string", "Relative file path, e.g. 'index.html'"),
        ),
    )

    val writeFile = AiToolSpec(
        name = "write_file",
        description = "Overwrite a project file. Creates parent directories. Returns 'OK' or 'Error: ...'.",
        params = listOf(
            AiToolParam("path", "string", "Relative file path"),
            AiToolParam("content", "string", "Full text content to write"),
        ),
    )

    val listFiles = AiToolSpec(
        name = "list_files",
        description = "List files and subdirectories at the given path. Use '.' for the project root.",
        params = listOf(
            AiToolParam("path", "string", "Relative directory path"),
        ),
    )

    val applyPatch = AiToolSpec(
        name = "apply_patch",
        description = "Apply a unified diff patch to the working tree. Returns 'OK' or 'Error: ...'.",
        params = listOf(
            AiToolParam("patch", "string", "Unified diff patch string"),
        ),
    )

    val all: List<AiToolSpec> = listOf(readFile, writeFile, listFiles, applyPatch)
}

/**
 * Shared tool-dispatch entrypoint. Adapters decode the model's tool call,
 * extract arg values into a plain string map, and call this function. Keeps
 * the dispatch table in one place so adding a tool only touches [IdeTools]
 * + [IdeToolSchema] + this function.
 */
internal fun dispatchIdeTool(name: String, args: Map<String, String?>, tools: IdeTools): String =
    when (name) {
        "read_file"   -> tools.readFile(args["path"].orEmpty())
        "write_file"  -> tools.writeFile(args["path"].orEmpty(), args["content"].orEmpty())
        "list_files"  -> tools.listFiles(args["path"] ?: ".")
        "apply_patch" -> tools.applyPatch(args["patch"].orEmpty())
        else          -> "Error: Unknown tool '$name'."
    }
