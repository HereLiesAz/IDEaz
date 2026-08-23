package com.hereliesaz.ideaz.ai

import android.content.Context
import com.hereliesaz.ideaz.ui.AiModel
import com.hereliesaz.ideaz.ui.AiModels
import com.hereliesaz.ideaz.ui.SettingsViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Maps a registered [AiModel] to a concrete [ConversationalAiClient].
 *
 * Three adapters serve every provider: [GeminiAdapter], [AnthropicAdapter], and
 * the single [OpenAiCompatibleAdapter] behind every `/chat/completions` endpoint.
 * Wire model ids are pinned in [AiModels] and overridable per provider in
 * Settings — nothing is discovered at runtime.
 *
 * Returns null only when the provider's key is missing.
 */
object AiAdapterFactory {

    /**
     * Build a client for [model], wrapped so the AI is handed the project and
     * told to study it before helping.
     */
    fun create(
        model: AiModel,
        context: Context,
        tools: IdeTools,
        settings: SettingsViewModel,
    ): ConversationalAiClient? {
        val base = createRaw(model, tools, settings) ?: return null
        val appName = settings.getAppName()?.takeIf { it.isNotBlank() } ?: "this project"
        val projectType = settings.readProjectType()
        return RepoAwareClient(base, tools, appName, projectType)
    }

    private fun createRaw(
        model: AiModel,
        tools: IdeTools,
        settings: SettingsViewModel,
    ): ConversationalAiClient? {
        val key = settings.getApiKey(model.requiredKey).orEmpty()
        if (key.isBlank()) return null
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

/**
 * Wraps any [ConversationalAiClient] so the model is always given the project and
 * told to study it before helping — independent of provider. The instruction and
 * a compact file tree are merged into the first user message each request (no
 * extra turn, so role alternation stays valid for every backend).
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
        You are an expert AI pair-programmer embedded in IDEaz, a visual IDE. You are
        helping the user build their project "$appName" (type: $projectType). The full
        project source is available to you.

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
