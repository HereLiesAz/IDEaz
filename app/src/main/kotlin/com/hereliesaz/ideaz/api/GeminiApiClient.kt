package com.hereliesaz.ideaz.api

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Base64
import android.util.Log
import com.google.ai.client.generativeai.GenerativeModel
import com.google.ai.client.generativeai.type.content
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

object GeminiApiClient {

    private const val IMAGE_TAG = "[IMAGE: data:image/png;base64,"

    /**
     * Calls the Gemini API with the given prompt and API key on an IO thread.
     * The prompt can be purely text, or multimodal with a text part and an image part.
     * Multimodal prompts are expected to have the format:
     * "text part... [IMAGE: data:image/png;base64,base64_encoded_image]"
     * Returns the response text, including a formatted error message if one occurs.
     */
    suspend fun generateContent(prompt: String, apiKey: String): String {
        return withContext(Dispatchers.IO) {
            try {
                // Use a multimodal model that can handle both text and images.
                val generativeModel = GenerativeModel(
                    modelName = "gemini-1.5-flash-latest",
                    apiKey = apiKey
                )

                val textPrompt: String
                var image: Bitmap? = null

                // Parse prompt for text and image
                if (prompt.contains(IMAGE_TAG)) {
                    textPrompt = prompt.substringBefore(IMAGE_TAG).trim()
                    val base64Image = prompt.substringAfter(IMAGE_TAG).removeSuffix("]")
                    try {
                        val decodedString = Base64.decode(base64Image, Base64.DEFAULT)
                        image = BitmapFactory.decodeByteArray(decodedString, 0, decodedString.size)
                    } catch (e: IllegalArgumentException) {
                        Log.e("GeminiApiClient", "Base64 decoding failed", e)
                        return@withContext "Error: Invalid image format."
                    }
                } else {
                    textPrompt = prompt
                }

                val response = if (image != null) {
                    val inputContent = content {
                        image(image)
                        text(textPrompt)
                    }
                    generativeModel.generateContent(inputContent)
                } else {
                    generativeModel.generateContent(textPrompt)
                }

                response.text ?: "Error: Received an empty response from the API."
            } catch (e: Exception) {
                Log.e("GeminiApiClient", "Gemini API call failed", e)
                "Error: ${e.message}"
            }
        }
    }
}