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

            val candidates = dataArray.filterIsInstance<JsonObject>()
                .filter { (it["id"] as? JsonPrimitive)?.content?.let { id -> filterRegex.containsMatchIn(id) } == true }

            if (candidates.isEmpty()) {
                error("$host returned no model matching '${filterRegex.pattern}'. The provider may have renamed it.")
            }

            // Rank by actual recency, not the model id string. A raw descending
            // string sort is not a version comparison - e.g. for Groq's
            // "llama.*70b" filter it picks "llama3-70b-8192" over the newer
            // "llama-3.3-70b-versatile" (lexicographically '3' > '-'), and for a
            // broad "gpt-4o" filter it can pick a non-chat variant like
            // "gpt-4o-transcribe" over "gpt-4o" itself. Every OpenAI-compatible
            // /v1/models entry carries a unix `created` timestamp, and Anthropic's
            // carries an ISO-8601 `created_at` - both are genuine recency signals
            // the provider itself supplies, not a guess derived from the name.
            val ranked = candidates.map { obj ->
                val id = (obj["id"] as? JsonPrimitive)?.content.orEmpty()
                val createdEpochSeconds = (obj["created"] as? JsonPrimitive)?.content?.toLongOrNull()
                    ?: (obj["created_at"] as? JsonPrimitive)?.content?.let(::parseIso8601EpochSeconds)
                id to createdEpochSeconds
            }

            val newestByTimestamp = ranked.filter { it.second != null }.maxByOrNull { it.second!! }
            if (newestByTimestamp != null) {
                return newestByTimestamp.first
            }

            // No provider-supplied timestamp on any candidate - fall back to the
            // old heuristic rather than failing outright.
            return ranked.map { it.first }.sortedDescending().first()
        }
    }

    private fun parseIso8601EpochSeconds(value: String): Long? = runCatching {
        java.time.Instant.parse(value).epochSecond
    }.getOrNull()
}
