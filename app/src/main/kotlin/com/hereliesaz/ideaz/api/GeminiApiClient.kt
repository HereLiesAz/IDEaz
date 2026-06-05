package com.hereliesaz.ideaz.api

import android.util.Base64
import android.util.Log
import com.google.genai.Client
import com.google.genai.types.Blob
import com.google.genai.types.Content
import com.google.genai.types.Part
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

object GeminiApiClient {

    private const val TAG = "GeminiApiClient"
    private const val IMAGE_TAG = "[IMAGE: data:image/png;base64,"
    private const val MODEL = "gemini-2.0-flash"

    /**
     * One-shot Gemini call used by the contextual chat overlay.
     *
     * Accepts either a pure-text prompt or a multimodal prompt encoded as
     * `"text part... [IMAGE: data:image/png;base64,<base64>]"` — the same
     * format produced by [com.hereliesaz.ideaz.ui.MainViewModel] when a
     * screenshot is attached. Returns the response text, or a formatted
     * error string on failure.
     */
    suspend fun generateContent(prompt: String, apiKey: String): String =
        withContext(Dispatchers.IO) {
            try {
                val parts = mutableListOf<Part>()

                if (prompt.contains(IMAGE_TAG)) {
                    val textPrompt = prompt.substringBefore(IMAGE_TAG).trim()
                    val base64Image = prompt.substringAfter(IMAGE_TAG).removeSuffix("]")
                    val imageBytes = try {
                        Base64.decode(base64Image, Base64.DEFAULT)
                    } catch (e: IllegalArgumentException) {
                        Log.e(TAG, "Base64 decoding failed", e)
                        return@withContext "Error: Invalid image format."
                    }
                    if (textPrompt.isNotBlank()) {
                        parts.add(Part.builder().text(textPrompt).build())
                    }
                    parts.add(
                        Part.builder()
                            .inlineData(
                                Blob.builder()
                                    .data(imageBytes)
                                    .mimeType("image/png")
                                    .build()
                            )
                            .build()
                    )
                } else {
                    parts.add(Part.builder().text(prompt).build())
                }

                val content = Content.builder().role("user").parts(parts).build()
                // Client is AutoCloseable and holds an HTTP client; close it after the
                // one-shot call so repeated overlay calls don't leak connections/threads.
                Client.builder().apiKey(apiKey).build().use { client ->
                    val response = client.models.generateContent(MODEL, listOf(content), null)
                    response.text() ?: "Error: Received an empty response from the API."
                }
            } catch (e: Exception) {
                Log.e(TAG, "Gemini API call failed", e)
                "Error: ${e.message}"
            }
        }
}
