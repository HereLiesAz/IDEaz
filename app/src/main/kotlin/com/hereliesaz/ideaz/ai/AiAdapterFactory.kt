package com.hereliesaz.ideaz.ai

import android.content.Context
import com.hereliesaz.ideaz.ai.bridge.FallbackAdapter
import com.hereliesaz.ideaz.ai.bridge.GeminiAppBridgeAdapter
import com.hereliesaz.ideaz.ui.AiModel
import com.hereliesaz.ideaz.ui.AiModels
import com.hereliesaz.ideaz.ui.SettingsViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

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

    /**
     * Build a [ConversationalAiClient] for [model], wrapped so that — regardless of
     * provider — the AI is handed the project and told to study it before helping.
     * Returns null only when the provider can't be built (missing key, or Jules,
     * which the caller handles separately).
     */
    fun create(
        model: AiModel,
        context: Context,
        tools: IdeTools,
        settings: SettingsViewModel,
    ): ConversationalAiClient? {
        val base = createRaw(model, context, tools, settings) ?: return null
        // The app-bridge path delivers the repo itself (snapshot/attachment) and
        // already repo-wraps its API fallback, so don't double-wrap it with the
        // tool-calling RepoAwareClient (whose "use read_file/list_files" preamble
        // is meaningless to the tool-less bridge).
        if (model.id == AiModels.GEMINI_APP_BRIDGE) return base
        val appName = settings.getAppName()?.takeIf { it.isNotBlank() } ?: "this project"
        val projectType = settings.getProjectType()
        return RepoAwareClient(base, tools, appName, projectType)
    }

    private fun createRaw(
        model: AiModel,
        context: Context,
        tools: IdeTools,
        settings: SettingsViewModel,
    ): ConversationalAiClient? {
        return when (model.id) {
            AiModels.GEMINI_NANO -> GeminiNanoAdapter(context.applicationContext, tools)

            AiModels.GEMINI_APP_BRIDGE -> {
                val appName = settings.getAppName()?.takeIf { it.isNotBlank() } ?: "this project"
                val projectType = settings.getProjectType()
                val projectDir = settings.getAppName()
                    ?.takeIf { it.isNotBlank() }
                    ?.let { settings.getProjectPath(it) }
                val primary = GeminiAppBridgeAdapter(
                    context = context.applicationContext,
                    tools = tools,
                    projectDir = projectDir,
                    appName = appName,
                    projectType = projectType,
                )
                // Silent fall-through: if the bridge can't operate (service
                // not granted, Gemini app missing, timeout) AND a Gemini API
                // key is set, use that path instead. The bridge handles its own
                // repo delivery; the API fallback is tool-capable, so give *that*
                // the standard repo-aware wrapper. Without a key, the primary
                // error surfaces.
                val googleKey = settings.getGoogleApiKey().orEmpty()
                if (googleKey.isNotBlank()) {
                    FallbackAdapter(
                        primary = primary,
                        fallback = RepoAwareClient(GeminiAdapter(googleKey, tools), tools, appName, projectType),
                    )
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
                modelResolver = {
                    val key = settings.getApiKey(model.requiredKey).orEmpty()
                    DynamicModelResolver.resolveLatestOpenAiCompat("https://api.groq.com/openai/v1", key, Regex("llama.*70b", RegexOption.IGNORE_CASE))
                },
                settings = settings,
                spec = model,
                tools = tools,
            )
            AiModels.CEREBRAS_LLAMA -> openAiCompat(
                baseUrl = "https://api.cerebras.ai/v1",
                modelResolver = {
                    val key = settings.getApiKey(model.requiredKey).orEmpty()
                    DynamicModelResolver.resolveLatestOpenAiCompat("https://api.cerebras.ai/v1", key, Regex("llama.*70b", RegexOption.IGNORE_CASE))
                },
                settings = settings,
                spec = model,
                tools = tools,
            )
            AiModels.HF_INFERENCE -> openAiCompat(
                baseUrl = "https://router.huggingface.co/v1",
                modelResolver = {
                    val key = settings.getApiKey(model.requiredKey).orEmpty()
                    DynamicModelResolver.resolveLatestOpenAiCompat("https://router.huggingface.co/v1", key, Regex("llama.*70b", RegexOption.IGNORE_CASE))
                },
                settings = settings,
                spec = model,
                tools = tools,
            )
            AiModels.MISTRAL_SMALL -> openAiCompat(
                baseUrl = "https://api.mistral.ai/v1",
                modelResolver = {
                    val key = settings.getApiKey(model.requiredKey).orEmpty()
                    DynamicModelResolver.resolveLatestOpenAiCompat("https://api.mistral.ai/v1", key, Regex("mistral-small", RegexOption.IGNORE_CASE))
                },
                settings = settings,
                spec = model,
                tools = tools,
            )
            
            AiModels.OPENAI_GPT4O -> openAiCompat(
                baseUrl = "https://api.openai.com/v1",
                modelResolver = {
                    val key = settings.getApiKey(model.requiredKey).orEmpty()
                    DynamicModelResolver.resolveLatestOpenAiCompat("https://api.openai.com/v1", key, Regex("gpt-4o", RegexOption.IGNORE_CASE))
                },
                settings = settings,
                spec = model,
                tools = tools,
            )
            
            AiModels.DEEPSEEK_CODER -> openAiCompat(
                baseUrl = "https://api.deepseek.com",
                modelResolver = {
                    val key = settings.getApiKey(model.requiredKey).orEmpty()
                    DynamicModelResolver.resolveLatestOpenAiCompat("https://api.deepseek.com", key, Regex("deepseek-coder", RegexOption.IGNORE_CASE))
                },
                settings = settings,
                spec = model,
                tools = tools,
            )
            
            AiModels.ANTHROPIC_CLAUDE -> {
                val key = settings.getApiKey(model.requiredKey).orEmpty()
                if (key.isBlank()) null else AnthropicAdapter(key, tools, {
                    DynamicModelResolver.resolveLatestAnthropic(key, Regex("sonnet", RegexOption.IGNORE_CASE))
                })
            }

            // On-device model the user downloaded (or AICore, system-managed).
            // The active model + its runtime are resolved at call time.
            AiModels.LOCAL_MODEL -> com.hereliesaz.ideaz.ai.local.LocalLlmAdapter(
                context = context.applicationContext,
                store = com.hereliesaz.ideaz.ai.local.LocalModelStore(context),
                downloads = com.hereliesaz.ideaz.ai.local.ModelDownloadManager(context),
            )

            // Jules has its own session/poll lifecycle outside the
            // ConversationalAiClient contract; callers handle it directly.
            AiModels.JULES_DEFAULT -> null

            else -> null
        }
    }

    private fun openAiCompat(
        baseUrl: String,
        modelResolver: () -> String,
        settings: SettingsViewModel,
        spec: AiModel,
        tools: IdeTools,
    ): ConversationalAiClient? {
        val key = settings.getApiKey(spec.requiredKey).orEmpty()
        if (key.isBlank()) return null
        return OpenAiCompatibleAdapter(baseUrl = baseUrl, apiKey = key, modelResolver = modelResolver, tools = tools)
    }
}

/**
 * Wraps any [ConversationalAiClient] so the model is always given the project and
 * told to study it before helping — independent of provider. The instruction and
 * a compact file tree are merged into the first user message each request (no
 * extra turn, so role alternation stays valid for every backend, including the
 * tool-less on-device / app-bridge paths). Tool-capable backends additionally use
 * read_file / list_files to dig in; tool-less ones at least see the structure.
 */
private class RepoAwareClient(
    private val delegate: ConversationalAiClient,
    private val tools: IdeTools,
    private val appName: String,
    private val projectType: String,
) : ConversationalAiClient {
    override suspend fun chat(messages: List<ChatMessage>): String {
        val preamble = withContext(Dispatchers.IO) {
            AiRepoContext.systemPreamble(appName, projectType, tools.repoMap())
        }
        val enriched = if (messages.isEmpty()) {
            listOf(ChatMessage("user", preamble))
        } else {
            val firstUser = messages.indexOfFirst { it.role == "user" }
            if (firstUser == -1) {
                listOf(ChatMessage("user", preamble)) + messages
            } else {
                messages.mapIndexed { i, m ->
                    if (i == firstUser) {
                        ChatMessage(m.role, listOf(ChatPart.Text(preamble + "\n\n")) + m.parts)
                    } else m
                }
            }
        }
        return delegate.chat(enriched)
    }
}

/** Builds the provider-agnostic "study the project first" system preamble. */
object AiRepoContext {
    fun systemPreamble(appName: String, projectType: String, repoMap: String): String = """
        You are an expert AI pair-programmer embedded in IDEaz, a mobile IDE. You are
        helping the user build their project "$appName" (type: $projectType). The full
        project source is available to you.

        Before answering or changing anything, STUDY THE PROJECT so your help fits how
        it is actually built:
        - Read the relevant files — entry points, configuration, and whatever the
          request touches — using the read_file and list_files tools. The project file
          tree below is your starting map.
        - Understand the language/framework, dependencies, structure, and existing
          conventions, then follow them.
        - Make focused, idiomatic changes that build on the existing code, and briefly
          explain what you changed and why.

        Project file tree:
        $repoMap
    """.trimIndent()
}
