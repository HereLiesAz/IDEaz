package com.hereliesaz.ideaz.ai.local

import android.content.Context
import com.hereliesaz.ideaz.ai.ChatMessage
import com.hereliesaz.ideaz.ai.ConversationalAiClient
import com.hereliesaz.ideaz.ai.IdeToolSchema
import com.hereliesaz.ideaz.ai.IdeTools
import com.hereliesaz.ideaz.ai.dispatchIdeTool
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

private const val MAX_LOCAL_TOOL_ROUNDS = 6

/** A structured response emitted by a local model during the constrained tool loop. */
internal sealed interface LocalModelReply {
    data class ToolCall(val name: String, val arguments: Map<String, String?>) : LocalModelReply
    data class Final(val content: String) : LocalModelReply
    data class PlainText(val content: String) : LocalModelReply
}

/**
 * Small-model-friendly JSON protocol for local tool calling.
 *
 * Local runtimes expose text generation rather than native function calling. The
 * protocol therefore asks for exactly one JSON object per turn and accepts fenced
 * JSON as well as a bare object. Anything else degrades to ordinary chat text; a
 * model should remain useful even when it forgets its ceremonial braces.
 */
internal object LocalToolProtocol {
    private val json = Json { ignoreUnknownKeys = true }

    val instruction: String = buildString {
        appendLine("You can use project tools. Reply with exactly one JSON object and no commentary.")
        appendLine("To call a tool: {\"type\":\"tool\",\"name\":\"read_file\",\"arguments\":{\"path\":\"index.html\"}}")
        appendLine("To finish: {\"type\":\"final\",\"content\":\"Brief answer describing the result.\"}")
        appendLine("Available tools:")
        IdeToolSchema.all.forEach { spec ->
            append("- ").append(spec.name).append(": ").append(spec.description)
            if (spec.params.isNotEmpty()) {
                append(" Arguments: ")
                append(spec.params.joinToString { "${it.name}:${it.type}" })
            }
            appendLine()
        }
        append("Use one tool per turn. Read relevant files before editing. Never invent tool results.")
    }

    fun parse(raw: String): LocalModelReply {
        val trimmed = raw.trim()
        val candidate = when {
            trimmed.startsWith("```") -> trimmed
                .removePrefix("```json")
                .removePrefix("```JSON")
                .removePrefix("```")
                .substringBeforeLast("```")
                .trim()
            else -> trimmed
        }
        val start = candidate.indexOf('{')
        val end = candidate.lastIndexOf('}')
        if (start < 0 || end <= start) return LocalModelReply.PlainText(trimmed)

        val obj = runCatching {
            json.parseToJsonElement(candidate.substring(start, end + 1)).jsonObject
        }.getOrNull() ?: return LocalModelReply.PlainText(trimmed)

        return when (runCatching { obj["type"]?.jsonPrimitive?.contentOrNull }.getOrNull()) {
            "tool" -> {
                val name = runCatching {
                    obj["name"]?.jsonPrimitive?.contentOrNull
                }.getOrNull().orEmpty()
                if (name.isBlank()) return LocalModelReply.PlainText(trimmed)
                val arguments = runCatching {
                    obj["arguments"]?.jsonObject.orEmpty().mapValues { (_, value) ->
                        value.jsonPrimitive.contentOrNull
                    }
                }.getOrDefault(emptyMap())
                LocalModelReply.ToolCall(name, arguments)
            }
            "final" -> LocalModelReply.Final(
                runCatching { obj["content"]?.jsonPrimitive?.contentOrNull }
                    .getOrNull()
                    .orEmpty()
            )
            else -> LocalModelReply.PlainText(trimmed)
        }
    }
}

/**
 * [ConversationalAiClient] backed by the user's selected on-device model. Flattens
 * conversation history into a prompt and runs a bounded JSON tool loop so local
 * models can inspect and edit the project despite text-only inference runtimes.
 *
 * When it can't run, it returns a clear, actionable message rather than throwing —
 * no model selected, the file isn't downloaded, or the runtime backend isn't in
 * this build yet — so the UI shows guidance instead of a crash.
 */
class LocalLlmAdapter(
    private val context: Context,
    private val store: LocalModelStore,
    private val downloads: ModelDownloadManager,
    private val tools: IdeTools,
) : ConversationalAiClient {

    override suspend fun chat(messages: List<ChatMessage>): String {
        val model = store.activeModel()
            ?: return "Error: No on-device model selected. Pick one in Settings → On-device models."
        if (!downloads.isDownloaded(model)) {
            return "Error: \"${model.name}\" isn't downloaded yet. Download it in Settings → On-device models."
        }
        val runtime = LocalModelRuntimes.byId(model.runtimeId)
            ?: return "Error: No runtime '${model.runtimeId}' is registered for ${model.name}."
        if (!runtime.isAvailable(context)) {
            return "Error: The ${runtime.displayName} backend isn't included in this build yet."
        }

        val transcript = StringBuilder().apply {
            appendLine(LocalToolProtocol.instruction)
            appendLine()
            appendLine("Conversation:")
            messages.forEach { m ->
                val who = if (m.role == "user") "User" else "Assistant"
                append(who).append(": ").append(m.content).append('\n')
            }
            append("Assistant JSON:")
        }

        // System-managed runtimes (AICore) ignore the file; pass a harmless path.
        val modelFile = if (model.systemManaged) context.filesDir else downloads.fileFor(model)
        repeat(MAX_LOCAL_TOOL_ROUNDS) {
            val raw = try {
                runtime.generate(context, modelFile, transcript.toString())
            } catch (e: Exception) {
                return "Error: On-device generation failed: ${e.message}"
            }
            when (val reply = LocalToolProtocol.parse(raw)) {
                is LocalModelReply.Final -> return reply.content.ifBlank { "Done." }
                is LocalModelReply.PlainText -> return reply.content
                is LocalModelReply.ToolCall -> {
                    val output = dispatchIdeTool(reply.name, reply.arguments, tools)
                    transcript.appendLine()
                    transcript.appendLine("Assistant tool call: $raw")
                    transcript.appendLine("Tool result for ${reply.name}: $output")
                    transcript.append("Assistant JSON:")
                }
            }
        }
        return "Error: On-device tool-use loop exceeded $MAX_LOCAL_TOOL_ROUNDS rounds."
    }
}
