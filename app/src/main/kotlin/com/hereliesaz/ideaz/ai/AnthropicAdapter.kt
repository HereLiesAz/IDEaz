package com.hereliesaz.ideaz.ai

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

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

        var rounds = 0
        while (rounds < OpenAiCompatibleAdapter.MAX_TOOL_ROUNDS) {
            val response = postMessages(history, currentModel)
            
            val contentArray = response["content"] as? JsonArray ?: return@withContext "No response from $currentModel."
            
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
                return@withContext textContent.ifBlank { "No response from $currentModel." }
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
                
                val output = dispatchIdeTool(name, argMap, tools)
                
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
