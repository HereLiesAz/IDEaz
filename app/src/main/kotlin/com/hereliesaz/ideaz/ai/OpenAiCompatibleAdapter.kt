package com.hereliesaz.ideaz.ai

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
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
    private val model: String,
    private val tools: IdeTools,
    private val httpClient: OkHttpClient = sharedClient,
) : ConversationalAiClient {

    override suspend fun chat(messages: List<ChatMessage>): String = withContext(Dispatchers.IO) {
        val history = messages.map { it.toOpenAiMessage() }.toMutableList()

        var rounds = 0
        while (rounds < MAX_TOOL_ROUNDS) {
            val response = postChatCompletion(history)
            val choice = (response["choices"] as? JsonArray)?.firstOrNull() as? JsonObject
            val message = choice?.get("message") as? JsonObject
                ?: return@withContext "No response from $model."

            val toolCalls = (message["tool_calls"] as? JsonArray).orEmpty()
            val textContent = (message["content"] as? JsonPrimitive)?.contentOrNullSafe().orEmpty()

            if (toolCalls.isEmpty()) {
                return@withContext textContent.ifBlank { "No response from $model." }
            }

            // Echo the assistant turn (with its tool_calls) back so the model
            // can correlate tool results in the next round.
            history.add(message)

            for (call in toolCalls.filterIsInstance<JsonObject>()) {
                val callId = (call["id"] as? JsonPrimitive)?.contentOrNullSafe().orEmpty()
                val function = call["function"] as? JsonObject ?: continue
                val name = (function["name"] as? JsonPrimitive)?.contentOrNullSafe().orEmpty()
                val argsJson = (function["arguments"] as? JsonPrimitive)?.contentOrNullSafe().orEmpty()
                val argMap = parseToolArgs(argsJson)
                val output = dispatchIdeTool(name, argMap, tools)

                history.add(buildJsonObject {
                    put("role", "tool")
                    put("tool_call_id", callId)
                    put("name", name)
                    put("content", output)
                })
            }
            rounds++
        }
        "Error: Tool-use loop exceeded $MAX_TOOL_ROUNDS rounds."
    }

    private fun postChatCompletion(history: List<JsonObject>): JsonObject {
        val body = buildJsonObject {
            put("model", model)
            putJsonArray("messages") { history.forEach { add(it) } }
            putJsonArray("tools") {
                IdeToolSchema.all.forEach { spec -> add(spec.toOpenAiTool()) }
            }
            put("tool_choice", "auto")
        }.toString()

        val request = Request.Builder()
            .url("${baseUrl.trimEnd('/')}/chat/completions")
            .addHeader("Authorization", "Bearer $apiKey")
            .addHeader("Accept", "application/json")
            .post(body.toRequestBody(jsonMediaType))
            .build()

        httpClient.newCall(request).execute().use { resp ->
            val text = resp.body.string()
            if (!resp.isSuccessful) {
                error("OpenAI-compatible call failed: HTTP ${resp.code}: ${text.take(500)}")
            }
            return JSON.parseToJsonElement(text) as? JsonObject
                ?: error("Unexpected response shape from $baseUrl: ${text.take(200)}")
        }
    }

    companion object {
        const val MAX_TOOL_ROUNDS = 10
        private val jsonMediaType = "application/json; charset=utf-8".toMediaType()
        internal val sharedClient: OkHttpClient = OkHttpClient.Builder().build()
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
                        val b64 = android.util.Base64.encodeToString(part.bytes, android.util.Base64.NO_WRAP)
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

private fun JsonPrimitive.contentOrNullSafe(): String? = if (isString) content else content

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
        .onFailure { android.util.Log.w("OpenAiCompatibleAdapter", "Malformed tool-call arguments JSON; dispatching with no args", it) }
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
