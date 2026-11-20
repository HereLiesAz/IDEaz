package com.hereliesaz.ideaz.ai

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runInterruptible

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
 * lives in each adapter: [GeminiAdapter.dispatchTool] wraps [dispatchIdeTool]
 * with provider-specific decoding, while [OpenAiCompatibleAdapter] and
 * [AnthropicAdapter] call [dispatchIdeTool] directly, so each adapter can
 * decode args from its provider's native shape.
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

    val generateImage = AiToolSpec(
        name = "generate_image",
        description = "Generate an image from a text prompt (AI Horde, community GPUs; may take a few " +
            "minutes) and save it into the project. Saved as WebP, so the path must end in .webp. " +
            "Use it for illustrations, backgrounds and placeholder art the user asks for.",
        params = listOf(
            AiToolParam("path", "string", "Relative file path ending in .webp, e.g. 'assets/hero.webp'"),
            AiToolParam("prompt", "string", "What the image should show, in plain English"),
            AiToolParam("width", "number", "Width in pixels, 64-768 (default 512)", required = false),
            AiToolParam("height", "number", "Height in pixels, 64-768 (default 512)", required = false),
        ),
    )

    val all: List<AiToolSpec> = listOf(readFile, writeFile, listFiles, applyPatch, generateImage)

    /** Tools that change the working tree: run behind an edit checkpoint and the review gate. */
    val mutating: Set<String> = setOf(writeFile.name, applyPatch.name, generateImage.name)
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
        // A missing content argument (a call cut off by the token limit, or a
        // model that omitted it) used to write "" - truncating the file.
        "write_file"  -> args["content"]?.let { tools.writeFile(args["path"].orEmpty(), it) }
            ?: "Error: write_file needs a 'content' argument; nothing was written."
        "list_files"  -> tools.listFiles(args["path"] ?: ".")
        "apply_patch" -> tools.applyPatch(args["patch"].orEmpty())
        "generate_image" -> args[PREPARE_ERROR]?.let { "Error: $it" }
            ?: tools.saveGeneratedImage(args["path"].orEmpty(), args[PREPARED_IMAGE])
        else          -> "Error: Unknown tool '$name'."
    }

private const val PREPARED_IMAGE = "__prepared_image"
private const val PREPARE_ERROR = "__prepare_error"

/**
 * Does a tool's slow network work before dispatch, where it can still be cancelled: mutating
 * tools run under NonCancellable with the edit checkpoint open, so a minutes-long AI Horde wait
 * must not happen there. Returns the args to dispatch with; other tools pass through unchanged.
 */
internal suspend fun prepareIdeTool(name: String, args: Map<String, String?>, tools: IdeTools): Map<String, String?> {
    if (name != IdeToolSchema.generateImage.name) return args
    return try {
        val token = runInterruptible(Dispatchers.IO) {
            tools.prepareImage(args["path"].orEmpty(), args["prompt"].orEmpty(), args["width"].toPixels(), args["height"].toPixels())
        }
        args + (PREPARED_IMAGE to token)
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        args + (PREPARE_ERROR to (e.message ?: "image generation failed"))
    }
}

/** A model may send "512", "512.0" or nothing. */
private fun String?.toPixels(): Int = this?.toDoubleOrNull()?.toInt() ?: 512
