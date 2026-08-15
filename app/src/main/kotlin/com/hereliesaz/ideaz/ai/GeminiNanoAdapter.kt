package com.hereliesaz.ideaz.ai

import android.content.Context
import com.google.ai.edge.aicore.GenerativeAIException
import com.google.ai.edge.aicore.GenerativeModel
import com.google.ai.edge.aicore.generationConfig
import com.hereliesaz.ideaz.ai.local.AiCoreRuntime
import com.hereliesaz.ideaz.ai.local.DeviceCapabilities
import com.hereliesaz.ideaz.ai.local.localInferenceLimits
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * On-device Gemini Nano adapter via the AICore SDK
 * (`com.google.ai.edge.aicore`). Runs entirely on the device — no API key,
 * no network — on supported Pixel/Samsung hardware. Returns a friendly
 * message on devices that lack AICore so the routing layer can fall back.
 *
 * **Limitations vs the hosted adapters:**
 *  - **No tool use.** The on-device API does not expose function-calling, so
 *    [chat] returns plain text only. Tool-requiring prompts (file edits)
 *    must use a different adapter.
 *  - **Limited context.** `maxOutputTokens` is capped low by default to fit
 *    device constraints. Callers should keep prompts short.
 *  - **Single-turn semantics.** The conversation history is flattened into
 *    one prompt; the on-device model is not optimised for multi-turn chat.
 */
class GeminiNanoAdapter(
    private val context: Context,
    @Suppress("unused") private val tools: IdeTools,
) : ConversationalAiClient {

    override suspend fun chat(messages: List<ChatMessage>): String = withContext(Dispatchers.IO) {
        // On-device API is text-only. Flatten parts; if any non-text parts
        // were attached, surface a one-line notice in the prompt so the user
        // sees why the model isn't responding to their image / file.
        val droppedNonText = messages.any { msg -> msg.parts.any { it !is ChatPart.Text } }
        val notice = if (droppedNonText) "[Gemini Nano is text-only. Attached images and files were not forwarded.]\n\n" else ""
        val prompt = notice + messages.joinToString("\n\n") { msg ->
            val speaker = if (msg.role == "user") "User" else "Assistant"
            "$speaker: ${msg.content}"
        } + "\n\nAssistant:"

        try {
            AiCoreRuntime.generate(
                context = context,
                modelFile = context.filesDir,
                prompt = prompt,
                limits = localInferenceLimits(DeviceCapabilities.totalRamBytes(context)),
            )
        } catch (e: CancellationException) {
            throw e
        } catch (e: GenerativeAIException) {
            "Gemini Nano error: ${e.message ?: e::class.simpleName}"
        } catch (e: Throwable) {
            "Gemini Nano error: ${e.message ?: e::class.simpleName}"
        }
    }

    companion object {
        /**
         * Best-effort availability check: try to build and warm a tiny model,
         * close it immediately, report success or failure. The check is
         * cheap on supported devices (just SDK init) and fast-fails on
         * unsupported ones via [GenerativeAIException]. Run on Dispatchers.IO.
         */
        suspend fun isAvailable(context: Context): Boolean = withContext(Dispatchers.IO) {
            try {
                val config = generationConfig {
                    this.context = context.applicationContext
                    maxOutputTokens = 1
                }
                GenerativeModel(generationConfig = config).use { model ->
                    model.prepareInferenceEngine()
                }
                true
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                false
            }
        }
    }
}
