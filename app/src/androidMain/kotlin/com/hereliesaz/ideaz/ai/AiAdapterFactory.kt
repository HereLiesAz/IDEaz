package com.hereliesaz.ideaz.ai

import android.content.Context
import com.hereliesaz.ideaz.BuildConfig
import com.hereliesaz.ideaz.ai.aive.AiveAdapter
import com.hereliesaz.ideaz.ai.aive.AiveTransports
import com.hereliesaz.ideaz.ai.bridge.FallbackAdapter
import com.hereliesaz.ideaz.ai.bridge.GeminiAppBridgeAdapter
import com.hereliesaz.ideaz.ui.AiModel
import com.hereliesaz.ideaz.ui.AiModels
import com.hereliesaz.ideaz.ui.SettingsViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Maps a registered [AiModel] to a concrete [ConversationalAiClient].
 *
 * Play builds use only documented provider APIs. GitHub builds may additionally
 * route the Gemini assignment through the installed Gemini app; when an AI
 * Studio key exists the API client is retained as an automatic fallback.
 */
object AiAdapterFactory {

    fun create(
        model: AiModel,
        context: Context,
        tools: IdeTools,
        settings: SettingsViewModel,
    ): ConversationalAiClient? {
        val appName = settings.getAppName()?.takeIf { it.isNotBlank() } ?: "this project"

        if (model.id == AiModels.GEMINI_FLASH && BuildConfig.EXTERNAL_AI_AUTOMATION) {
            val bridge: ConversationalAiClient = GeminiAppBridgeAdapter(context, tools)
            val key = settings.getApiKey(model.providerKey).orEmpty()
            if (key.isBlank()) return bridge

            val wireModel = settings.getWireModelOverride(model.id) ?: model.defaultWireModel
            val apiFallback = RepoAwareClient(
                GeminiAdapter(key, tools, wireModel),
                tools,
                appName,
            )
            return FallbackAdapter(bridge, apiFallback)
        }

        if (model.id == AiModels.THE_AIVE) {
            // The Aive injects no repo preamble of its own; AiveAdapter sends a project snapshot.
            return AiveAdapter(AiveTransports.create(context, settings), tools)
        }

        val base = createRaw(model, tools, settings) ?: return null
        return RepoAwareClient(base, tools, appName)
    }

    /**
     * Wraps [existing] (the client already built for [model]) so a quota or rate-limit failure
     * moves the same request to the next provider: keyless ones first, then any the user has a
     * key for, skipping providers cooling down. Each is tried at most once per request.
     */
    fun withFailover(
        model: AiModel,
        existing: ConversationalAiClient,
        context: Context,
        tools: IdeTools,
        settings: SettingsViewModel,
        needsImages: Boolean,
        onSwitch: (String) -> Unit,
    ): FailoverClient {
        val catalog = AiModels.defaultRanking.map { m ->
            FailoverCandidate(
                id = m.id,
                label = m.displayName,
                keyless = m.keyless,
                usable = m.keyless || (m.providerKey.isNotEmpty() && !settings.getApiKey(m.providerKey).isNullOrBlank()),
                supportsImages = m.supportsImages,
            )
        }
        return FailoverClient(
            order = ProviderFailover.order(model.id, catalog, needsImages),
            clientFor = { candidate ->
                if (candidate.id == model.id) existing
                else AiModels.findById(candidate.id)?.let { create(it, context, tools, settings) }
            },
            cooldowns = ProviderCooldowns.process,
            onSwitch = onSwitch,
        )
    }

    private fun createRaw(
        model: AiModel,
        tools: IdeTools,
        settings: SettingsViewModel,
    ): ConversationalAiClient? {
        val key = model.providerKey.takeIf { it.isNotEmpty() }?.let { settings.getApiKey(it) }.orEmpty()
        if (key.isBlank() && !model.keyless) return null
        val wireModel = settings.getWireModelOverride(model.id) ?: model.defaultWireModel

        return when (model.id) {
            AiModels.GEMINI_FLASH -> GeminiAdapter(key, tools, wireModel)
            AiModels.ANTHROPIC_CLAUDE -> AnthropicAdapter(key, tools, { wireModel })
            AiModels.OPENAI_GPT4O -> openAiCompat("https://api.openai.com/v1", key, wireModel, tools)
            AiModels.DEEPSEEK_CODER -> openAiCompat("https://api.deepseek.com", key, wireModel, tools)
            AiModels.GROQ_LLAMA -> openAiCompat("https://api.groq.com/openai/v1", key, wireModel, tools)
            AiModels.CEREBRAS_LLAMA -> openAiCompat("https://api.cerebras.ai/v1", key, wireModel, tools)
            AiModels.HF_INFERENCE -> openAiCompat("https://router.huggingface.co/v1", key, wireModel, tools)
            AiModels.MISTRAL_SMALL -> openAiCompat("https://api.mistral.ai/v1", key, wireModel, tools)
            AiModels.KILO_FREE -> openAiCompat("https://api.kilo.ai/api/gateway", key, wireModel, tools)
            AiModels.LLM7_DEFAULT -> openAiCompat("https://api.llm7.io/v1", key, wireModel, tools)
            AiModels.OVHCLOUD_FREE -> openAiCompat("https://oai.endpoints.kepler.ai.cloud.ovh.net/v1", key, wireModel, tools)
            else -> null
        }
    }

    private fun openAiCompat(
        baseUrl: String,
        apiKey: String,
        wireModel: String,
        tools: IdeTools,
    ): ConversationalAiClient = OpenAiCompatibleAdapter(
        baseUrl = baseUrl,
        apiKey = apiKey,
        modelResolver = { wireModel },
        tools = tools,
    )
}

private class RepoAwareClient(
    private val delegate: ConversationalAiClient,
    private val tools: IdeTools,
    private val appName: String,
) : ConversationalAiClient {
    override suspend fun chat(messages: List<ChatMessage>): String {
        val preamble = withContext(Dispatchers.IO) {
            AiRepoContext.systemPreamble(appName, tools.repoMap())
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

object AiRepoContext {
    fun systemPreamble(appName: String, repoMap: String): String = """
        You are an expert AI pair-programmer embedded in IDEaz, a visual IDE. You are
        helping the user build their web project "$appName". The full project source is
        available to you.

        HOW THE PROJECT RUNS: IDEaz mounts the working tree in a WebView and transpiles
        JSX/TS in the browser with Babel. There is no build step and no dev server —
        what is on disk is what renders, so your edits take effect on the next reload.
        Do not add a bundler step or expect one to run. Bare specifiers (react,
        react-dom, and the common ecosystem libraries) resolve through a bundled import
        map; adding a dependency to package.json does not make it importable.

        HOW THE USER TALKS TO YOU: IDEaz renders their project live. The user taps an
        element in that running preview and then types a request about it. When they
        do, their message is prefixed with an ELEMENT CONTEXT block describing exactly
        what they tapped. That block may carry a `source` field naming the file and
        line the element was produced from — when it does, that is the authoritative
        place to make the change. Go straight there and read it; do not search for it.
        When there is no `source` field, use the selector and surrounding HTML to work
        out which file renders that element.

        Before changing anything, STUDY THE PROJECT so your help fits how it is built:
        - Read the relevant files with read_file and list_files. The tree below is your
          starting map.
        - Follow the existing language, framework, and conventions.
        - Make focused, idiomatic edits, then briefly say what you changed and why.

        Project file tree:
        $repoMap
    """.trimIndent()
}
