package com.hereliesaz.ideaz.ai

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import okhttp3.OkHttpClient
import okhttp3.Request

object DynamicModelResolver {
    
    /**
     * Fetches models from an OpenAI-compatible /v1/models endpoint, filters them, 
     * sorts them in descending alphanumeric order, and returns the highest (latest) ID.
     */
    fun resolveLatestOpenAiCompat(
        baseUrl: String, 
        apiKey: String, 
        filterRegex: Regex,
        httpClient: OkHttpClient = OpenAiCompatibleAdapter.sharedClient
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
        httpClient: OkHttpClient = OpenAiCompatibleAdapter.sharedClient
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
        httpClient.newCall(request).execute().use { resp ->
            val text = resp.body.string()
            if (!resp.isSuccessful) {
                error("Failed to fetch models: HTTP ${resp.code}: ${text.take(500)}")
            }
            
            val json = OpenAiCompatibleAdapter.JSON.parseToJsonElement(text) as? JsonObject
                ?: error("Unexpected models response shape: ${text.take(200)}")
                
            val dataArray = json["data"] as? JsonArray
                ?: error("No 'data' array in models response: ${text.take(200)}")
                
            val models = dataArray.filterIsInstance<JsonObject>()
                .mapNotNull { (it["id"] as? JsonPrimitive)?.content }
                .filter { filterRegex.containsMatchIn(it) }
                
            if (models.isEmpty()) {
                error("No models matched pattern ${filterRegex.pattern} in response: ${text.take(200)}")
            }
            
            // Descending sort so the highest semantic version or date string comes first.
            return models.sortedDescending().first()
        }
    }
}
