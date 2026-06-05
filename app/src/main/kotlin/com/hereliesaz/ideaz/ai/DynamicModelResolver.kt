package com.hereliesaz.ideaz.ai

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

object DynamicModelResolver {

    // Model resolution runs on the first AI call of a session. It must FAIL FAST
    // rather than hang the user behind a spinner, so it gets its own short
    // timeouts — the shared chat client intentionally has none (completions can
    // legitimately run long). No fallback model id is guessed: if a provider's
    // /models endpoint can't be reached, the call surfaces a clear, actionable
    // error instead of pretending to know the model name.
    private val resolverClient: OkHttpClient by lazy {
        OpenAiCompatibleAdapter.sharedClient.newBuilder()
            .connectTimeout(5, TimeUnit.SECONDS)
            .readTimeout(8, TimeUnit.SECONDS)
            .callTimeout(12, TimeUnit.SECONDS)
            .build()
    }

    /**
     * Fetches models from an OpenAI-compatible /v1/models endpoint, filters them,
     * sorts them in descending alphanumeric order, and returns the highest (latest) ID.
     */
    fun resolveLatestOpenAiCompat(
        baseUrl: String,
        apiKey: String,
        filterRegex: Regex,
        httpClient: OkHttpClient = resolverClient
    ): String {
        val request = Request.Builder()
            .url("${baseUrl.trimEnd('/')}/models")
            .addHeader("Authorization", "Bearer $apiKey")
            .addHeader("Accept", "application/json")
            .get()
            .build()

        return resolveLatestFromRequest(request, filterRegex, httpClient)
    }

    /**
     * Fetches models from Anthropic /v1/models endpoint, filters them,
     * sorts them descending, and returns the highest (latest) ID.
     */
    fun resolveLatestAnthropic(
        apiKey: String,
        filterRegex: Regex,
        httpClient: OkHttpClient = resolverClient
    ): String {
        val request = Request.Builder()
            .url("https://api.anthropic.com/v1/models")
            .addHeader("x-api-key", apiKey)
            .addHeader("anthropic-version", "2023-06-01")
            .addHeader("Accept", "application/json")
            .get()
            .build()

        return resolveLatestFromRequest(request, filterRegex, httpClient)
    }

    private fun resolveLatestFromRequest(request: Request, filterRegex: Regex, httpClient: OkHttpClient): String {
        val host = request.url.host
        val resp = try {
            httpClient.newCall(request).execute()
        } catch (e: Exception) {
            // Network failure or timeout — surface something the user can act on
            // instead of a raw exception bubbling up as the AI's reply.
            error("Couldn't reach $host to pick a model (${e.message ?: "network error / timeout"}). Check your connection and that this provider's API key is set in Settings.")
        }
        resp.use { r ->
            val text = r.body.string()
            if (!r.isSuccessful) {
                error("$host rejected the model request (HTTP ${r.code}). Verify this provider's API key in Settings. ${text.take(200)}")
            }

            val json = OpenAiCompatibleAdapter.JSON.parseToJsonElement(text) as? JsonObject
                ?: error("Unexpected model-list response from $host: ${text.take(200)}")

            val dataArray = json["data"] as? JsonArray
                ?: error("No model list returned by $host: ${text.take(200)}")

            val models = dataArray.filterIsInstance<JsonObject>()
                .mapNotNull { (it["id"] as? JsonPrimitive)?.content }
                .filter { filterRegex.containsMatchIn(it) }

            if (models.isEmpty()) {
                error("$host returned no model matching '${filterRegex.pattern}'. The provider may have renamed it.")
            }

            // Descending sort so the highest semantic version or date string comes first.
            return models.sortedDescending().first()
        }
    }
}
