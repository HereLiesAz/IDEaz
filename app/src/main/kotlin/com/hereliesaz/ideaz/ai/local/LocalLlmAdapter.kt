package com.hereliesaz.ideaz.ai.local

import android.content.Context
import com.hereliesaz.ideaz.ai.ChatMessage
import com.hereliesaz.ideaz.ai.ConversationalAiClient
import com.hereliesaz.ideaz.ai.IdeToolSchema
import com.hereliesaz.ideaz.ai.IdeTools
import com.hereliesaz.ideaz.ai.dispatchIdeTool
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentLinkedDeque
import java.util.concurrent.atomic.AtomicLong

private const val MAX_LOCAL_TOOL_ROUNDS = 6
private const val MAX_LOCAL_DIAGNOSTICS = 32

/** Stable categories callers can use for retry and consent-based fallback decisions. */
enum class LocalProviderFailureKind {
    NO_MODEL_SELECTED,
    MODEL_NOT_DOWNLOADED,
    RUNTIME_UNAVAILABLE,
    GENERATION_FAILED,
    TOOL_EXECUTION_FAILED,
    TOOL_ROUND_LIMIT,
}

enum class LocalRecoveryAction { RETRY_LOCAL, CLOUD_ONCE }

/** Structured local-provider failure. It never contains prompts, source, or tool output. */
data class LocalProviderFailure(
    val kind: LocalProviderFailureKind,
    val message: String,
    val retryable: Boolean,
    val cloudFallbackAllowed: Boolean,
    val diagnosticId: String,
) {
    /** Safe UI text: actionable state plus an opaque diagnostic reference. */
    fun displayText(): String = buildString {
        append(message)
        if (retryable) append(" Retry is available.")
        append(" Diagnostic: ").append(diagnosticId).append('.')
    }

    fun permits(action: LocalRecoveryAction, requestedDiagnosticId: String, cloudConfigured: Boolean): Boolean {
        if (requestedDiagnosticId != diagnosticId) return false
        return when (action) {
            LocalRecoveryAction.RETRY_LOCAL -> retryable
            LocalRecoveryAction.CLOUD_ONCE -> cloudFallbackAllowed && cloudConfigured
        }
    }
}

/** Exception boundary used by conversational callers without changing cloud adapters. */
class LocalProviderException(val failure: LocalProviderFailure) : Exception(failure.message)

/** Sanitized, bounded in-process diagnostics for support without retaining user content. */
object LocalProviderDiagnostics {
    data class Entry(
        val id: String,
        val kind: LocalProviderFailureKind,
        val modelId: String?,
        val runtimeId: String?,
        val causeType: String?,
    )

    private val entries = ConcurrentHashMap<String, Entry>()
    private val order = ConcurrentLinkedDeque<String>()
    private val sequence = AtomicLong()

    fun record(
        kind: LocalProviderFailureKind,
        modelId: String? = null,
        runtimeId: String? = null,
        cause: Throwable? = null,
    ): String {
        val id = "L%08x".format(sequence.incrementAndGet())
        entries[id] = Entry(id, kind, modelId, runtimeId, cause?.javaClass?.simpleName)
        order.addLast(id)
        while (order.size > MAX_LOCAL_DIAGNOSTICS) {
            order.pollFirst()?.let { entries.remove(it) }
        }
        return id
    }

    fun find(id: String): Entry? = entries[id]

    internal fun clearForTests() {
        entries.clear()
        order.clear()
        sequence.set(0L)
    }
}

private fun localFailure(
    kind: LocalProviderFailureKind,
    message: String,
    retryable: Boolean,
    cloudFallbackAllowed: Boolean = true,
    modelId: String? = null,
    runtimeId: String? = null,
    cause: Throwable? = null,
): Nothing {
    val id = LocalProviderDiagnostics.record(kind, modelId, runtimeId, cause)
    throw LocalProviderException(
        LocalProviderFailure(
            kind = kind,
            message = message,
            retryable = retryable,
            cloudFallbackAllowed = cloudFallbackAllowed,
            diagnosticId = id,
        )
    )
}

/** Preserves protocol instructions and the newest transcript within a device-tier prompt budget. */
internal fun boundedLocalPrompt(prefix: String, transcript: String, suffix: String, maxChars: Int): String {
    require(maxChars > 0) { "Prompt limit must be positive" }
    val fixed = prefix + suffix
    require(fixed.length <= maxChars) { "Prompt limit cannot hold required protocol text" }
    val available = maxChars - fixed.length
    var start = (transcript.length - available).coerceAtLeast(0)
    if (start in 1 until transcript.length &&
        Character.isLowSurrogate(transcript[start]) &&
        Character.isHighSurrogate(transcript[start - 1])
    ) {
        start++
    }
    return prefix + transcript.substring(start) + suffix
}

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
            ?: localFailure(
                LocalProviderFailureKind.NO_MODEL_SELECTED,
                "No on-device model is selected. Pick one in Settings → On-device models.",
                retryable = false,
            )
        if (!downloads.isDownloaded(model)) {
            localFailure(
                LocalProviderFailureKind.MODEL_NOT_DOWNLOADED,
                "${model.name} is not downloaded. Download it in Settings → On-device models.",
                retryable = false,
                modelId = model.id,
            )
        }
        val runtime = LocalModelRuntimes.byId(model.runtimeId)
            ?: localFailure(
                LocalProviderFailureKind.RUNTIME_UNAVAILABLE,
                "No ${model.runtimeId} runtime is registered for ${model.name}.",
                retryable = false,
                modelId = model.id,
                runtimeId = model.runtimeId,
            )
        if (!runtime.isAvailable(context)) {
            localFailure(
                LocalProviderFailureKind.RUNTIME_UNAVAILABLE,
                "The ${runtime.displayName} backend is unavailable in this build.",
                retryable = false,
                modelId = model.id,
                runtimeId = runtime.id,
            )
        }

        val transcript = StringBuilder().apply {
            messages.forEach { m ->
                val who = if (m.role == "user") "User" else "Assistant"
                append(who).append(": ").append(m.content).append('\n')
            }
        }

        // System-managed runtimes (AICore) ignore the file; pass a harmless path.
        val modelFile = if (model.systemManaged) context.filesDir else downloads.fileFor(model)
        val limits = localInferenceLimits(DeviceCapabilities.totalRamBytes(context))
        var hasExecutedTool = false
        repeat(MAX_LOCAL_TOOL_ROUNDS) {
            val prompt = boundedLocalPrompt(
                prefix = LocalToolProtocol.instruction + "\n\nConversation:\n",
                transcript = transcript.toString(),
                suffix = "Assistant JSON:",
                maxChars = limits.maxPromptChars,
            )
            val raw = try {
                runtime.generate(context, modelFile, prompt, limits)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                localFailure(
                    LocalProviderFailureKind.GENERATION_FAILED,
                    "On-device generation failed.",
                    retryable = true,
                    cloudFallbackAllowed = !hasExecutedTool,
                    modelId = model.id,
                    runtimeId = runtime.id,
                    cause = e,
                )
            }
            when (val reply = LocalToolProtocol.parse(raw)) {
                is LocalModelReply.Final -> return reply.content.ifBlank { "Done." }
                is LocalModelReply.PlainText -> return reply.content
                is LocalModelReply.ToolCall -> {
                    hasExecutedTool = true
                    val output = try {
                        dispatchIdeTool(reply.name, reply.arguments, tools)
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        localFailure(
                            LocalProviderFailureKind.TOOL_EXECUTION_FAILED,
                            "The on-device model could not complete ${reply.name}.",
                            retryable = true,
                            cloudFallbackAllowed = false,
                            modelId = model.id,
                            runtimeId = runtime.id,
                            cause = e,
                        )
                    }
                    transcript.appendLine()
                    transcript.appendLine("Assistant tool call: $raw")
                    transcript.appendLine("Tool result for ${reply.name}: $output")
                }
            }
        }
        localFailure(
            LocalProviderFailureKind.TOOL_ROUND_LIMIT,
            "The on-device model reached the $MAX_LOCAL_TOOL_ROUNDS-round tool limit.",
            retryable = false,
            cloudFallbackAllowed = false,
            modelId = model.id,
            runtimeId = runtime.id,
        )
    }
}
