package com.hereliesaz.ideaz.ai

import com.hereliesaz.ideaz.platform.Platform

import com.hereliesaz.ideaz.ai.AiEditApproval
import com.hereliesaz.ideaz.ai.AiEditApprovalRequiredException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.TimeUnit

private val MUTATING_TOOLS = setOf("write_file", "apply_patch")

/**
 * Adapter for any OpenAI-compatible /chat/completions endpoint. Covers:
 *  - Groq (`https://api.groq.com/openai/v1`)
 *  - Cerebras Cloud (`https://api.cerebras.ai/v1`)
 *  - Hugging Face Inference Providers (`https://router.huggingface.co/v1`)
 *  - Mistral La Plateforme (`https://api.mistral.ai/v1`)
 *  - OpenAI (`https://api.openai.com/v1`)
 *  - Any other server speaking the same wire format.
 *
 * Implements the same tool-use loop shape as [GeminiAdapter]: send the
 * conversation, dispatch any `tool_calls`, append the results as `tool`
 * role messages, and loop until the model returns a final text response.
 *
 * Notes on variance:
 *  - Some free-tier providers (HF Inference, certain Mistral models) may not
 *    support `tool_choice: "auto"` or function-calling at all. If a turn
 *    completes with no tool calls and no message, we return whatever content
 *    we received as plain text.
 *  - Tool-call argument JSON is parsed lazily into a `Map<String, String?>`
 *    by stringifying each value — same coercion [GeminiAdapter] uses.
 */
class OpenAiCompatibleAdapter(
    private val baseUrl: String,
    private val apiKey: String,
    private val modelResolver: () -> String,
    private val tools: IdeTools,
    private val httpClient: OkHttpClient = sharedClient,
    /** First backoff after a 429/5xx; doubles per retry. Tests shrink it. */
    private val retryBaseDelayMs: Long = RETRY_BASE_DELAY_MS,
) : ConversationalAiClient {

    private var resolvedModel: String? = null

    override suspend fun chat(messages: List<ChatMessage>): String = withContext(Dispatchers.IO) {
        if (resolvedModel == null) {
            resolvedModel = modelResolver()
        }
        val currentModel = resolvedModel!!
        
        val history = messages.map { it.toOpenAiMessage() }.toMutableList()

        // Same checkpoint/review/approval contract GeminiAdapter and
        // AnthropicAdapter use — see AiEditApproval's doc comment.
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
            throw AiEditApprovalRequiredException(AiEditApproval(review, response, source = currentModel))
        }

        fun dispatchMutatingTool(name: String, stringArgs: Map<String, String?>): String {
            return try {
                // Creating the checkpoint used to sit outside this try, so a snapshot
                // failure escaped as a raw exception instead of a clean tool result.
                val checkpoint = editCheckpoint
                    ?: tools.createEditCheckpoint("IDEaz: checkpoint before $currentModel edit").also { editCheckpoint = it }
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
                // Checked every round, and the in-flight call is cancelled with the coroutine
                // (see executeOnce), so a cancel really does reach this handler.
                ensureActive()
                postChatCompletion(history, currentModel)
            } catch (e: CancellationException) {
                // A mutating tool call in an earlier round may have already
                // written to disk; without this, cancelling mid-loop left the
                // edit applied with no review card and an orphaned checkpoint.
                restorePendingEdit()
                throw e
            } catch (e: Exception) {
                // Same failure mode for a network/API error after a successful
                // write in an earlier round - an HTTP 429 that outlasted the
                // retries is common on rate-limited free-tier backends.
                restorePendingEdit()
                // Cancelling the call surfaces as an IOException; report it as the cancellation.
                ensureActive()
                return@withContext "Error: $currentModel request failed: ${e.message}"
            }
            val choice = (response["choices"] as? JsonArray)?.firstOrNull() as? JsonObject
            // An unexpected-but-2xx shape (no "message", e.g. a proxy emitting
            // "delta" instead, or an empty choices array) used to return here
            // directly, bypassing complete() - if a mutating tool already ran in
            // an earlier round, the edit was left applied with no review.
            val message = choice?.get("message") as? JsonObject ?: run {
                restorePendingEdit()
                return@withContext "Error: unexpected response shape from $currentModel."
            }

            val toolCalls = (message["tool_calls"] as? JsonArray).orEmpty()
            val textContent = (message["content"] as? JsonPrimitive)?.contentOrNullSafe().orEmpty()

            if (toolCalls.isEmpty()) {
                return@withContext complete(textContent.ifBlank { "No response from $currentModel." })
            }

            // Echo the assistant turn (with its tool_calls) back so the model
            // can correlate tool results in the next round.
            history.add(message)

            for (call in toolCalls.filterIsInstance<JsonObject>()) {
                val callId = (call["id"] as? JsonPrimitive)?.contentOrNullSafe().orEmpty()
                val function = call["function"] as? JsonObject
                if (function == null) {
                    // Every tool_call_id must be answered or the next request is rejected (HTTP 400).
                    history.add(buildJsonObject {
                        put("role", "tool")
                        put("tool_call_id", callId)
                        put("content", "Error: malformed tool call (no function).")
                    })
                    continue
                }
                val name = (function["name"] as? JsonPrimitive)?.contentOrNullSafe().orEmpty()
                val argsJson = (function["arguments"] as? JsonPrimitive)?.contentOrNullSafe().orEmpty()
                val argMap = parseToolArgs(argsJson)
                val output = if (name in MUTATING_TOOLS) {
                    withContext(NonCancellable) { dispatchMutatingTool(name, argMap) }
                } else {
                    dispatchIdeTool(name, argMap, tools)
                }

                history.add(buildJsonObject {
                    put("role", "tool")
                    put("tool_call_id", callId)
                    put("name", name)
                    put("content", output)
                })
            }
            rounds++
        }
        restorePendingEdit()
        "Error: Tool-use loop exceeded $MAX_TOOL_ROUNDS rounds."
    }

    private suspend fun postChatCompletion(history: List<JsonObject>, currentModel: String): JsonObject {
        val body = buildJsonObject {
            put("model", currentModel)
            putJsonArray("messages") { history.forEach { add(it) } }
            putJsonArray("tools") {
                IdeToolSchema.all.forEach { spec -> add(spec.toOpenAiTool()) }
            }
            put("tool_choice", "auto")
        }.toString()

        val request = Request.Builder()
            .url("${baseUrl.trimEnd('/')}/chat/completions")
            // Keyless providers reject an empty bearer token; send none.
            .apply { if (apiKey.isNotBlank()) addHeader("Authorization", "Bearer $apiKey") }
            .addHeader("Accept", "application/json")
            .post(body.toRequestBody(jsonMediaType))
            .build()

        // Rate limits and transient 5xx are routine on free tiers. Retrying a completion is safe:
        // no tool has run for this request yet, and the retry interceptor skips POST.
        var attempt = 1
        while (true) {
            val reply = executeOnce(request)
            if (reply.code in 200..299) {
                return JSON.parseToJsonElement(reply.body) as? JsonObject
                    ?: error("Unexpected response shape from $baseUrl: ${reply.body.take(200)}")
            }
            if (!isRetryableStatus(reply.code) || attempt >= MAX_HTTP_ATTEMPTS) {
                error("OpenAI-compatible call failed: HTTP ${reply.code}: ${reply.body.take(500)}")
            }
            delay(retryDelayMs(attempt, reply.retryAfter, retryBaseDelayMs))
            attempt++
        }
    }

    private class HttpReply(val code: Int, val body: String, val retryAfter: String?)

    /** One blocking call, cancelled when the calling coroutine is. */
    private suspend fun executeOnce(request: Request): HttpReply {
        val call = httpClient.newCall(request)
        val hook = currentCoroutineContext()[Job]?.invokeOnCompletion { cause ->
            if (cause != null) call.cancel()
        }
        try {
            val reply = call.execute().use { resp ->
                HttpReply(resp.code, resp.body.string(), resp.header("Retry-After"))
            }
            return reply
        } finally {
            hook?.dispose()
        }
    }

    companion object {
        const val MAX_TOOL_ROUNDS = 10
        const val MAX_HTTP_ATTEMPTS = 4
        const val RETRY_BASE_DELAY_MS = 1_000L
        private const val MAX_RETRY_DELAY_MS = 30_000L

        /** 429 and server errors are worth retrying; 501 (not implemented) never changes. */
        internal fun isRetryableStatus(code: Int): Boolean = code == 429 || (code in 500..599 && code != 501)

        /**
         * Wait before the retry that follows failed attempt number [attempt] (1-based): the
         * server's Retry-After seconds when given, else [baseMs] doubling each time, capped.
         */
        internal fun retryDelayMs(attempt: Int, retryAfter: String?, baseMs: Long): Long {
            val seconds = retryAfter?.trim()?.toLongOrNull()
            if (seconds != null && seconds >= 0) return (seconds * 1000).coerceAtMost(MAX_RETRY_DELAY_MS)
            return (baseMs * (1L shl (attempt - 1).coerceIn(0, 10))).coerceAtMost(MAX_RETRY_DELAY_MS)
        }
        private val jsonMediaType = "application/json; charset=utf-8".toMediaType()
        // The calls are not streamed: nothing arrives until the whole
        // completion (a full write_file body included) is generated, which
        // routinely takes longer than OkHttp's 10 s default read timeout. That
        // timeout failed the round and rolled back earlier rounds' edits.
        // Shared by AnthropicAdapter.
        internal val sharedClient: OkHttpClient = OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(60, TimeUnit.SECONDS)
            .readTimeout(5, TimeUnit.MINUTES)
            .callTimeout(6, TimeUnit.MINUTES)
            .build()
        internal val JSON = Json { ignoreUnknownKeys = true; isLenient = true }
    }
}

private fun String.toOpenAiRole(): String = when (this) {
    "user"  -> "user"
    "model", "assistant" -> "assistant"
    "system" -> "system"
    "tool" -> "tool"
    else -> error("Unsupported ChatMessage role '$this'.")
}

/**
 * Build the per-message JSON object for the OpenAI chat-completions wire
 * format. If the message has only [ChatPart.Text] parts, emit `content` as a
 * plain string (the common case — most non-vision providers refuse the array
 * shape). If any [ChatPart.Image] is present, switch to the array form with
 * `image_url` entries holding base64 data URIs.
 *
 * [ChatPart.FileBlob] is not supported by the OpenAI vision schema, so we
 * inline a short notice as text — the model will see "(file <name>: not
 * forwarded to this provider)" rather than the bytes. Vision-capable Gemini
 * is the path for PDFs.
 */
private fun ChatMessage.toOpenAiMessage(): JsonObject = buildJsonObject {
    put("role", role.toOpenAiRole())
    val hasImage = parts.any { it is ChatPart.Image }
    if (!hasImage) {
        // Concatenate text parts + any file-blob notices so non-vision
        // providers (most free-tier models) get a usable string.
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
                        val b64 = Platform.base64Encode(part.bytes)
                        add(buildJsonObject {
                            put("type", "image_url")
                            putJsonObject("image_url") {
                                put("url", "data:${part.mimeType};base64,$b64")
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

// JsonNull is a JsonPrimitive with isString == false and content == "null" -
// without this explicit check, a JSON null argument value silently became
// the literal string "null" instead of Kotlin null, both in chat display
// and in AI-written file content.
private fun JsonPrimitive.contentOrNullSafe(): String? = if (this is JsonNull) null else content

private fun AiToolSpec.toOpenAiTool(): JsonElement = buildJsonObject {
    put("type", "function")
    putJsonObject("function") {
        put("name", name)
        put("description", description)
        putJsonObject("parameters") {
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

/**
 * Robustly decode an OpenAI tool-call `arguments` payload. The payload may be
 * a JSON object string or a stringified JSON object. Returns a flat string-map
 * for [dispatchIdeTool].
 */
private fun parseToolArgs(raw: String): Map<String, String?> {
    if (raw.isBlank()) return emptyMap()
    val element = runCatching { OpenAiCompatibleAdapter.JSON.parseToJsonElement(raw) }
        .onFailure { Platform.logWarn("OpenAiCompatibleAdapter", "Malformed tool-call arguments JSON; dispatching with no args", it) }
        .getOrNull()
        ?: return emptyMap()
    val obj = element as? JsonObject ?: return emptyMap()
    return obj.entries.associate { (k, v) ->
        k to when (v) {
            is JsonPrimitive -> v.content
            else -> v.toString()
        }
    }
}
