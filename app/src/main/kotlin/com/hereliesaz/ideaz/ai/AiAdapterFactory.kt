package com.hereliesaz.ideaz.ai

import android.content.Context
import com.hereliesaz.ideaz.ai.bridge.FallbackAdapter
import com.hereliesaz.ideaz.ai.bridge.GeminiAppBridgeAdapter
import com.hereliesaz.ideaz.ui.AiModel
import com.hereliesaz.ideaz.ui.AiModels
import com.hereliesaz.ideaz.ui.SettingsViewModel

/**
 * Centralised factory that maps a registered [AiModel] to a concrete
 * [ConversationalAiClient]. Call sites no longer instantiate adapters
 * directly — `AIDelegate.startContextualAITask`, `MainViewModel.sendChatMessage`,
 * and any future entrypoint route through here.
 *
 * Returns `null` when the model id corresponds to a provider that does not yet
 * implement [ConversationalAiClient] (today: Jules — its stateful session API
 * runs through [com.hereliesaz.ideaz.ui.delegates.AIDelegate.runJulesTask]).
 * Callers that get null fall back to the legacy code path for that provider.
 */
object AiAdapterFactory {

    fun create(
        model: AiModel,
        context: Context,
        tools: IdeTools,
        settings: SettingsViewModel,
    ): ConversationalAiClient? {
        return when (model.id) {
            AiModels.GEMINI_NANO -> GeminiNanoAdapter(context.applicationContext, tools)

            AiModels.GEMINI_APP_BRIDGE -> {
                val primary = GeminiAppBridgeAdapter(context.applicationContext)
                // Silent fall-through: if the bridge can't operate (service
                // not granted, Gemini app missing, timeout) AND a Gemini API
                // key is set, use that path instead. Without a key, the
                // primary error surfaces.
                val googleKey = settings.getGoogleApiKey().orEmpty()
                if (googleKey.isNotBlank()) {
                    FallbackAdapter(primary = primary, fallback = GeminiAdapter(googleKey, tools))
                } else {
                    primary
                }
            }

            AiModels.GEMINI_FLASH, AiModels.GEMINI_PRO, AiModels.GEMINI_CLI -> {
                val key = settings.getApiKey(model.requiredKey).orEmpty()
                if (key.isBlank()) null else GeminiAdapter(key, tools)
            }

            AiModels.GROQ_LLAMA -> openAiCompat(
                baseUrl = "https://api.groq.com/openai/v1",
                model = "llama-3.3-70b-versatile",
                settings = settings,
                spec = model,
                tools = tools,
            )
            AiModels.CEREBRAS_LLAMA -> openAiCompat(
                baseUrl = "https://api.cerebras.ai/v1",
                model = "llama3.1-70b",
                settings = settings,
                spec = model,
                tools = tools,
            )
            AiModels.HF_INFERENCE -> openAiCompat(
                baseUrl = "https://router.huggingface.co/v1",
                model = "meta-llama/Llama-3.3-70B-Instruct",
                settings = settings,
                spec = model,
                tools = tools,
            )
            AiModels.MISTRAL_SMALL -> openAiCompat(
                baseUrl = "https://api.mistral.ai/v1",
                model = "mistral-small-latest",
                settings = settings,
                spec = model,
                tools = tools,
            )

            // Jules has its own session/poll lifecycle outside the
            // ConversationalAiClient contract; callers handle it directly.
            AiModels.JULES_DEFAULT -> null

            else -> null
        }
    }

    private fun openAiCompat(
        baseUrl: String,
        model: String,
        settings: SettingsViewModel,
        spec: AiModel,
        tools: IdeTools,
    ): ConversationalAiClient? {
        val key = settings.getApiKey(spec.requiredKey).orEmpty()
        if (key.isBlank()) return null
        return OpenAiCompatibleAdapter(baseUrl = baseUrl, apiKey = key, model = model, tools = tools)
    }
}
