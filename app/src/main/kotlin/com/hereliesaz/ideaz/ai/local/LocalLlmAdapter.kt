package com.hereliesaz.ideaz.ai.local

import android.content.Context
import com.hereliesaz.ideaz.ai.ChatMessage
import com.hereliesaz.ideaz.ai.ConversationalAiClient
import com.hereliesaz.ideaz.ai.IdeEditCheckpoint
import com.hereliesaz.ideaz.ai.IdeEditReview
import com.hereliesaz.ideaz.ai.IdeToolSchema
import com.hereliesaz.ideaz.ai.IdeTools
import com.hereliesaz.ideaz.ai.dispatchIdeTool
import com.hereliesaz.ideaz.ai.DEFAULT_GEMINI_MODEL
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentLinkedDeque
import java.util.concurrent.atomic.AtomicLong
import java.security.MessageDigest
import java.util.UUID

private const val MAX_LOCAL_TOOL_ROUNDS = 6
private const val MAX_LOCAL_DIAGNOSTICS = 32
private const val MAX_CLOUD_QUESTION_CHARS = 2_000
private const val MAX_CLOUD_CONTEXT_CHARS = 6_000
internal const val CLOUD_CONSULT_USED_MARKER = "[IDEAZ_CLOUD_CONSULT_USED]"

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

/** Pending, validated local-model edit awaiting an explicit user decision. */
data class LocalEditApproval(
    val review: IdeEditReview,
    val response: String,
)

/** Control-flow boundary preventing a local edit from being reloaded before approval. */
class LocalEditApprovalRequiredException(
    val approval: LocalEditApproval,
) : Exception("On-device edits require approval")

/** Exact, bounded payload proposed for a single consent-gated cloud consultation. */
data class LocalCloudConsultRequest(
    val requestId: String,
    val projectPath: String,
    val conversationFingerprint: String,
    val conversationMessageCount: Int,
    val provider: String,
    val model: String,
    val prompt: String,
    val payloadHash: String,
    val byteCount: Int,
) {
    fun matches(requestedId: String, currentProjectPath: String, messages: List<ChatMessage>): Boolean {
        if (requestedId != requestId || currentProjectPath != projectPath) return false
        val currentConversation = localConversationFingerprint(messages)
        if (messages.size != conversationMessageCount || currentConversation != conversationFingerprint) return false
        val payload = listOf(projectPath, conversationFingerprint, provider, model, prompt).joinToString("\u0000")
        return sha256(payload) == payloadHash &&
            prompt.toByteArray(Charsets.UTF_8).size == byteCount
    }

    /** Rebinds factory-enriched adapter history to the durable UI conversation used for consent. */
    fun boundTo(messages: List<ChatMessage>): LocalCloudConsultRequest {
        val fingerprint = localConversationFingerprint(messages)
        val hash = sha256(listOf(projectPath, fingerprint, provider, model, prompt).joinToString("\u0000"))
        return copy(
            conversationFingerprint = fingerprint,
            conversationMessageCount = messages.size,
            payloadHash = hash,
        )
    }
}

/** Stops local inference before any network request and transfers control to consent UI. */
class LocalCloudConsultApprovalRequiredException(
    val request: LocalCloudConsultRequest,
) : Exception("Cloud consultation requires approval")

internal fun localConversationFingerprint(messages: List<ChatMessage>): String = sha256(
    messages.joinToString("\u0000") { message ->
        message.role + "\u0000" + message.parts.joinToString("\u0000") { part ->
            when (part) {
                is com.hereliesaz.ideaz.ai.ChatPart.Text -> part.text
                is com.hereliesaz.ideaz.ai.ChatPart.Image ->
                    "<image:${part.mimeType}:${part.bytes.size}:${sha256(part.bytes)}>"
                is com.hereliesaz.ideaz.ai.ChatPart.FileBlob ->
                    "<file:${part.mimeType}:${part.bytes.size}:${sha256(part.bytes)}>"
            }
        }
    }
)

internal fun createLocalCloudConsultRequest(
    projectPath: String,
    messages: List<ChatMessage>,
    question: String?,
    context: String?,
): LocalCloudConsultRequest {
    val boundedQuestion = question.orEmpty().trim()
    val boundedContext = context.orEmpty().trim()
    require(boundedQuestion.isNotEmpty()) { "Cloud question is required" }
    require(boundedQuestion.length <= MAX_CLOUD_QUESTION_CHARS) { "Cloud question exceeds 2000 characters" }
    require(boundedContext.length <= MAX_CLOUD_CONTEXT_CHARS) { "Cloud context exceeds 6000 characters" }
    val conversation = localConversationFingerprint(messages)
    val prompt = cloudConsultPrompt(boundedQuestion, boundedContext)
    val provider = "Google Gemini"
    val model = DEFAULT_GEMINI_MODEL
    val payload = listOf(projectPath, conversation, provider, model, prompt).joinToString("\u0000")
    return LocalCloudConsultRequest(
        requestId = UUID.randomUUID().toString(),
        projectPath = projectPath,
        conversationFingerprint = conversation,
        conversationMessageCount = messages.size,
        provider = provider,
        model = model,
        prompt = prompt,
        payloadHash = sha256(payload),
        byteCount = prompt.toByteArray(Charsets.UTF_8).size,
    )
}

internal fun cloudConsultPrompt(question: String, context: String): String = buildString {
    appendLine("You are a read-only coding consultant. Give concise technical advice.")
    appendLine("You cannot inspect other files, call tools, or change the project.")
    appendLine("Question:")
    appendLine(question)
    if (context.isNotBlank()) {
        appendLine("Context explicitly approved by the user:")
        append(context)
    }
}

private fun sha256(value: String): String = MessageDigest.getInstance("SHA-256")
    .digest(value.toByteArray(Charsets.UTF_8))
    .joinToString("") { "%02x".format(it) }

private fun sha256(value: ByteArray): String = MessageDigest.getInstance("SHA-256")
    .digest(value)
    .joinToString("") { "%02x".format(it) }

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
        appendLine("- ask_cloud: Ask the Gemini cloud consultant for read-only advice. " +
            "Arguments: question:string, context:string. Use only before every other tool and at most once.")
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

/** Internal signal proving the configured generation bound was exhausted. */
internal class LocalToolRoundLimitExceeded(val rounds: Int) : Exception()

/**
 * Android-free coordinator for the text tool protocol. Keeping the bound and
 * cancellation boundary here makes the dangerous parts executable in JVM tests.
 */
internal suspend fun runBoundedLocalToolLoop(
    transcript: StringBuilder,
    maxRounds: Int = MAX_LOCAL_TOOL_ROUNDS,
    buildPrompt: (String) -> String,
    generate: suspend (String) -> String,
    dispatch: suspend (LocalModelReply.ToolCall) -> String,
    onCancellation: suspend () -> Unit,
): String {
    require(maxRounds > 0) { "Tool-round limit must be positive" }
    try {
        repeat(maxRounds) {
            val raw = generate(buildPrompt(transcript.toString()))
            when (val reply = LocalToolProtocol.parse(raw)) {
                is LocalModelReply.Final -> return reply.content.ifBlank { "Done." }
                is LocalModelReply.PlainText -> return reply.content
                is LocalModelReply.ToolCall -> {
                    val output = dispatch(reply)
                    transcript.appendLine()
                    transcript.appendLine("Assistant tool call: $raw")
                    transcript.appendLine("Tool result for ${reply.name}: $output")
                }
            }
        }
    } catch (e: CancellationException) {
        withContext(NonCancellable) {
            runCatching { onCancellation() }
        }
        throw e
    }
    throw LocalToolRoundLimitExceeded(maxRounds)
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
        val cloudConsultAlreadyUsed = messages.any { message ->
            message.parts.any { part ->
                part is com.hereliesaz.ideaz.ai.ChatPart.Text && part.text.contains(CLOUD_CONSULT_USED_MARKER)
            }
        }
        var editCheckpoint: IdeEditCheckpoint? = null
        var expectedEditFingerprint: String? = null

        suspend fun restorePendingEdit(): Boolean {
            val checkpoint = editCheckpoint ?: return true
            val fingerprint = expectedEditFingerprint ?: return false
            return withContext(Dispatchers.IO) {
                runCatching { tools.restoreEditCheckpoint(checkpoint, fingerprint) }.isSuccess
            }
        }

        suspend fun complete(response: String): String {
            val checkpoint = editCheckpoint ?: return response
            val review = try {
                withContext(Dispatchers.IO) { tools.reviewEdits(checkpoint) }
            } catch (e: Exception) {
                val restored = restorePendingEdit()
                localFailure(
                    LocalProviderFailureKind.TOOL_EXECUTION_FAILED,
                    if (restored) {
                        "The on-device edit could not be validated and was restored."
                    } else {
                        "The on-device edit could not be validated or safely restored. Review project files."
                    },
                    retryable = true,
                    cloudFallbackAllowed = false,
                    modelId = model.id,
                    runtimeId = runtime.id,
                    cause = e,
                )
            }
            if (review.validationErrors.isNotEmpty()) {
                val restored = restorePendingEdit()
                localFailure(
                    LocalProviderFailureKind.TOOL_EXECUTION_FAILED,
                    if (restored) {
                        "The on-device edit failed validation and was restored."
                    } else {
                        "The on-device edit failed validation, but later file changes prevented automatic restore."
                    },
                    retryable = true,
                    cloudFallbackAllowed = false,
                    modelId = model.id,
                    runtimeId = runtime.id,
                )
            }
            if (review.changedFiles.isEmpty()) {
                withContext(Dispatchers.IO) { tools.discardEditCheckpoint(checkpoint) }
                return response
            }
            throw LocalEditApprovalRequiredException(LocalEditApproval(review, response))
        }

        suspend fun executeTool(reply: LocalModelReply.ToolCall): String {
            if (reply.name == "ask_cloud") {
                if (hasExecutedTool || editCheckpoint != null) {
                    return "Error: Cloud consultation is available only before every other tool."
                }
                if (cloudConsultAlreadyUsed) {
                    return "Error: The one cloud consultation for this turn has already been used."
                }
                val request = try {
                    createLocalCloudConsultRequest(
                        projectPath = tools.projectPath(),
                        messages = messages,
                        question = reply.arguments["question"],
                        context = reply.arguments["context"],
                    )
                } catch (e: IllegalArgumentException) {
                    return "Error: ${e.message}"
                }
                throw LocalCloudConsultApprovalRequiredException(request)
            }
            hasExecutedTool = true
            val mutating = reply.name == "write_file" || reply.name == "apply_patch"
            if (mutating) {
                editCheckpoint = editCheckpoint ?: try {
                    withContext(Dispatchers.IO) {
                        tools.createEditCheckpoint("IDEaz: checkpoint before on-device edit")
                    }
                } catch (e: Exception) {
                    localFailure(
                        LocalProviderFailureKind.TOOL_EXECUTION_FAILED,
                        "The on-device edit could not create an undo checkpoint.",
                        retryable = true,
                        cloudFallbackAllowed = false,
                        modelId = model.id,
                        runtimeId = runtime.id,
                        cause = e,
                    )
                }
                try {
                    expectedEditFingerprint = withContext(Dispatchers.IO) {
                        tools.captureToolEdit(editCheckpoint!!, reply.name, reply.arguments)
                        tools.reviewEdits(editCheckpoint!!).contentFingerprint.also { fingerprint ->
                            tools.updateEditCheckpointFingerprint(editCheckpoint!!, fingerprint)
                        }
                    }
                    withContext(Dispatchers.IO) { tools.markEditMutationStarted(editCheckpoint!!) }
                } catch (e: Exception) {
                    editCheckpoint?.let { checkpoint ->
                        withContext(Dispatchers.IO) {
                            runCatching { tools.discardEditCheckpoint(checkpoint) }
                        }
                    }
                    localFailure(
                        LocalProviderFailureKind.TOOL_EXECUTION_FAILED,
                        "The on-device edit could not create a safe undo snapshot.",
                        retryable = true,
                        cloudFallbackAllowed = false,
                        modelId = model.id,
                        runtimeId = runtime.id,
                        cause = e,
                    )
                }
            }
            return try {
                if (mutating) {
                    withContext(NonCancellable + Dispatchers.IO) {
                        dispatchIdeTool(reply.name, reply.arguments, tools).also {
                            expectedEditFingerprint = tools.reviewEdits(editCheckpoint!!)
                                .contentFingerprint
                                .also { fingerprint ->
                                    tools.updateEditCheckpointFingerprint(editCheckpoint!!, fingerprint)
                                    tools.markEditAwaitingReview(editCheckpoint!!)
                                }
                        }
                    }
                } else {
                    withContext(Dispatchers.IO) {
                        dispatchIdeTool(reply.name, reply.arguments, tools)
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                restorePendingEdit()
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
        }

        val response = try {
            runBoundedLocalToolLoop(
                transcript = transcript,
                buildPrompt = { currentTranscript ->
                    boundedLocalPrompt(
                        prefix = LocalToolProtocol.instruction + "\n\nConversation:\n",
                        transcript = currentTranscript,
                        suffix = "Assistant JSON:",
                        maxChars = limits.maxPromptChars,
                    )
                },
                generate = { prompt ->
                    try {
                        runtime.generate(context, modelFile, prompt, limits)
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        restorePendingEdit()
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
                },
                dispatch = { reply -> executeTool(reply) },
                onCancellation = { restorePendingEdit() },
            )
        } catch (e: LocalToolRoundLimitExceeded) {
            restorePendingEdit()
            localFailure(
                LocalProviderFailureKind.TOOL_ROUND_LIMIT,
                "The on-device model reached the ${e.rounds}-round tool limit.",
                retryable = false,
                cloudFallbackAllowed = false,
                modelId = model.id,
                runtimeId = runtime.id,
            )
        }
        return complete(response)
    }
}
