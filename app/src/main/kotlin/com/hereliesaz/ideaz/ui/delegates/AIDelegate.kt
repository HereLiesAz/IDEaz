package com.hereliesaz.ideaz.ui.delegates

import com.hereliesaz.ideaz.ai.ChatMessage
import com.hereliesaz.ideaz.ai.AgenticAiClient
import com.hereliesaz.ideaz.ai.ConversationalAiClient
import com.hereliesaz.ideaz.ai.GeminiAdapter
import com.hereliesaz.ideaz.ai.IdeTools
import com.hereliesaz.ideaz.ai.TaskEvent
import com.hereliesaz.ideaz.ui.AiModel
import com.hereliesaz.ideaz.api.*
import com.hereliesaz.ideaz.jules.IJulesApiClient
import com.hereliesaz.ideaz.jules.JulesAdapter
import com.hereliesaz.ideaz.jules.JulesApiClient
import com.hereliesaz.ideaz.models.ProjectType
import com.hereliesaz.ideaz.ui.AiModels
import com.hereliesaz.ideaz.ui.SettingsViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.flow.updateAndGet
import kotlinx.coroutines.launch

/**
 * Chat message for Jules session history.
 * @see com.hereliesaz.ideaz.ai.ChatMessage for the Gemini conversational adapter equivalent.
 * TODO(Phase3): Unify with ChatMessage when multiple providers are supported.
 */
data class Message(val role: String, val content: String)

/**
 * Delegate responsible for handling AI interactions.
 *
 * This class abstracts the complexity of:
 * 1.  **Session Management:** Fetching, creating, and resuming Jules sessions.
 * 2.  **API Communication:** Interacting with [IJulesApiClient] or [GeminiApiClient].
 * 3.  **Polling:** Waiting for asynchronous AI responses and processing activities (messages, artifacts).
 * 4.  **Patch Application:** Detecting code patches in AI responses and triggering their application.
 *
 * @param settingsViewModel Access to user settings (API keys, project info).
 * @param scope CoroutineScope for executing background API calls.
 * @param onOverlayLog Callback to send status updates/logs to the UI overlay.
 * @param onUnidiffPatchReceived Callback invoked when a Git patch is received. Returns true if application succeeded.
 * @param julesApiClient Client for the Jules REST API. Defaults to the singleton [JulesApiClient].
 */
class AIDelegate(
    private val settingsViewModel: SettingsViewModel,
    private val scope: CoroutineScope,
    private val onOverlayLog: (String) -> Unit,
    private val onUnidiffPatchReceived: suspend (String) -> Boolean,
    private val julesApiClient: IJulesApiClient = JulesApiClient,
    /**
     * Factory for the Gemini agent client. Shared with MainViewModel's chat
     * tab so the same cached client (and its lazily-built google-genai
     * Client) backs both call sites; we don't want to spin up a second
     * adapter for every contextual prompt.
     *
     * Default builds a fresh adapter per call — fine for tests that don't
     * exercise the Gemini path, but production callers should supply
     * MainViewModel.geminiAdapterFor.
     */
    private val geminiAdapterFactory: (apiKey: String, appName: String) -> GeminiAdapter = { key, name ->
        GeminiAdapter(key, IdeTools(settingsViewModel.getProjectPath(name)))
    },
    /**
     * Called after Gemini's tool-use loop completes, so the UI can refresh
     * any view that mirrors the project tree (e.g. the WebView, file
     * explorer). Default is a no-op for tests.
     */
    private val onFilesChanged: () -> Unit = {},
    /**
     * Resolves a [ConversationalAiClient] for the given model id. Built by
     * [AiAdapterFactory] in production callers (MainViewModel); tests can
     * supply a stub. Returns null when the model isn't a chat-style
     * provider — Jules in particular has its own session/poll lifecycle
     * outside the ConversationalAiClient contract, so AIDelegate falls back
     * to [runJulesTask] when the provider is null and the requested model
     * is Jules.
     */
    private val aiClientProvider: (model: AiModel) -> ConversationalAiClient? = { null },
    /**
     * Agentic provider for the Android target loop. Defaults to a [JulesAdapter]
     * over the same [julesApiClient], so production and tests share one Jules path.
     */
    private val julesAdapter: AgenticAiClient = JulesAdapter(julesApiClient),
    /**
     * Called with a pull-request URL when the agent opens one (the terminal event
     * of the PR-based Android loop). Production wires this to
     * BuildDelegate.installFromMergedPr (auto-merge → rebuild → re-sideload).
     * Default is a no-op for tests / the conversational paths.
     */
    private val onAgentPullRequest: (url: String) -> Unit = {},
) {

    // --- StateFlows ---

    private val _currentJulesSessionId = MutableStateFlow<String?>(null)
    /** The Resource Name (ID) of the currently active Jules session. Null if no session is active. */
    val currentJulesSessionId = _currentJulesSessionId.asStateFlow()

    private val _julesResponse = MutableStateFlow<Session?>(null)
    /** The most recent full [Session] object received from the API. */
    val julesResponse = _julesResponse.asStateFlow()

    private val _julesHistory = MutableStateFlow<List<Message>>(emptyList())
    /** The linear history of messages in the current conversation, mapped for UI display. */
    val julesHistory = _julesHistory.asStateFlow()

    private val _isLoadingJulesResponse = MutableStateFlow(false)
    /** Boolean flag indicating if an AI request is currently in flight (shows loading spinner). */
    val isLoadingJulesResponse = _isLoadingJulesResponse.asStateFlow()

    private val _julesError = MutableStateFlow<String?>(null)
    /** Contains the error message if the last AI operation failed, or null otherwise. */
    val julesError = _julesError.asStateFlow()

    private val _sessions = MutableStateFlow<List<Session>>(emptyList())
    /** The list of available (historical) Jules sessions associated with the current repository. */
    val sessions = _sessions.asStateFlow()

    private val _geminiHistory = MutableStateFlow<List<ChatMessage>>(emptyList())
    /**
     * The conversation history Gemini sees for contextual/overlay prompts.
     * Distinct from MainViewModel's chat-tab history so the overlay path
     * keeps its own coherent thread; cleared on project switch via
     * [clearSession]. (Phase 3: unify with the chat-tab history — see
     * the TODO above [Message].)
     */
    val geminiHistory = _geminiHistory.asStateFlow()

    // --- Internal State ---

    /** The Job for the currently running contextual task. Allows cancellation. */
    private var contextualTaskJob: Job? = null

    // --- Public Methods ---

    /**
     * Resumes an existing Jules session by its ID.
     * Does not immediately fetch history (history is fetched upon interaction),
     * but sets the context for the next prompt.
     */
    fun resumeSession(sessionName: String) {
        _currentJulesSessionId.value = sessionName
    }

    /**
     * Clears the current session state (ID, history, errors).
     * Should be called when switching projects to prevent context leakage between repos.
     */
    fun clearSession() {
        _currentJulesSessionId.value = null
        _julesResponse.value = null
        _julesHistory.value = emptyList()
        _julesError.value = null
        _geminiHistory.value = emptyList()
    }

    /**
     * Fetches the list of active sessions associated with the specified repository.
     *
     * **Logic:**
     * 1. Calls `listSessions` (no args).
     * 2. Filters the result locally to match the `source` context of the current repo.
     *    Format: `sources/github/{owner}/{repo}`.
     *
     * @param repoName The repository name (or "owner/repo").
     */
    fun fetchSessionsForRepo(repoName: String) {
        scope.launch {
            try {
                val response = julesApiClient.listSessions()
                val allSessions = response.sessions ?: emptyList()
                val user = settingsViewModel.getGithubUser() ?: ""

                // Construct the expected source string
                val fullRepo = if (repoName.contains("/")) repoName else "$user/$repoName"
                val targetSource = "sources/github/$fullRepo"

                val filtered = allSessions.filter { session ->
                    val source = session.sourceContext.source
                    source.equals(targetSource, ignoreCase = true)
                }
                _sessions.value = filtered
            } catch (e: Exception) {
                // Silently fail or log debug
                _sessions.value = emptyList()
            }
        }
    }

    /**
     * Starts a contextual AI task with the given prompt.
     *
     * **Flow:**
     * 1. Save prompt to history.
     * 2. Determine selected AI model (Jules vs Gemini).
     * 3. Validate API Key.
     * 4. Launch coroutine to execute request.
     *
     * @param richPrompt The full prompt text (may include context info like "File: Main.kt Line: 10...").
     */
    fun startContextualAITask(richPrompt: String) {
        settingsViewModel.saveLastPrompt(richPrompt)
        onOverlayLog("Thinking...")
        _isLoadingJulesResponse.value = true
        _julesError.value = null

        // Resolve Model. An explicit Settings → AI Assignments choice always wins;
        // otherwise the default is project-type-aware — Android targets use Jules
        // (agentic, PR-based), Web/PWA use Gemini (conversational, local tool-use).
        val modelId = settingsViewModel.getAiAssignment(SettingsViewModel.KEY_AI_ASSIGNMENT_OVERLAY)
        val projectType = ProjectType.fromString(settingsViewModel.getProjectType())
        var model = defaultOverlayModel(modelId, projectType)

        // Jules is GitHub-anchored and pointless for Web/PWA projects (it needs a
        // sourceContext and produces unidiff patches — Web/PWA edits run locally
        // through the Gemini tool-use loop and ProjectFileObserver-driven reload).
        val isWebOrPwa = projectType.isWebLike()
        if (isWebOrPwa && model.id == AiModels.JULES_DEFAULT) {
            onOverlayLog("Jules is not used for Web/PWA projects. Routing through Gemini.")
            model = AiModels.GEMINI
        }

        // Models with empty requiredKey (e.g. Gemini Nano) need no key check.
        if (model.requiredKey.isNotEmpty()) {
            val key = settingsViewModel.getApiKey(model.requiredKey)
            if (key.isNullOrBlank()) {
                onOverlayLog("Error: API Key missing for ${model.displayName}")
                _isLoadingJulesResponse.value = false
                return
            }
        }

        contextualTaskJob = scope.launch {
            try {
                // Try the multi-provider factory first. If it returns a client,
                // route through the unified runConversationalTask path. Jules
                // and providers without configured keys return null and fall
                // through to their dedicated paths.
                val client = aiClientProvider(model)
                when {
                    client != null -> runConversationalTask(model, client, richPrompt)
                    model.id == AiModels.JULES_DEFAULT -> runJulesTask(richPrompt)
                    else -> {
                        // No factory-provided client and not Jules — legacy fallback
                        // for the explicit Gemini path used in older tests/wiring.
                        val key = settingsViewModel.getApiKey(model.requiredKey).orEmpty()
                        runGeminiTask(richPrompt, key)
                    }
                }
            } catch (e: Exception) {
                onOverlayLog("Error: ${e.message}")
                _julesError.value = e.message
            } finally {
                _isLoadingJulesResponse.value = false
            }
        }
    }

    /**
     * Multi-provider conversational path. Sends the user prompt against the
     * given [client]'s chat history, appends the response to [_geminiHistory]
     * (shared overlay-thread history), and fires [onFilesChanged] so the UI
     * and WebView refresh after any tool-induced file writes.
     */
    private suspend fun runConversationalTask(
        model: AiModel,
        client: ConversationalAiClient,
        richPrompt: String,
    ) {
        val historyForChat = _geminiHistory.updateAndGet { it + ChatMessage("user", richPrompt) }
        val response = client.chat(historyForChat)
        _geminiHistory.update { it + ChatMessage("model", response) }
        onOverlayLog("${model.displayName}: $response")
        onFilesChanged()
    }

    /**
     * Runs the Gemini agent loop against the current project directory.
     *
     * Unlike the simple text-only [GeminiApiClient.generateContent] this drives
     * [GeminiAdapter], which exposes `read_file`, `write_file`, `list_files`,
     * and `apply_patch` to Gemini via function-calling. Files Gemini decides to
     * write land directly in the project's local directory (sandboxed by
     * [IdeTools] to that subtree).
     *
     * Maintains a multi-turn history in [_geminiHistory] so follow-up prompts
     * carry context. After the tool-use loop completes, fires [onFilesChanged]
     * so the UI can refresh anything mirroring the project tree.
     */
    private suspend fun runGeminiTask(richPrompt: String, apiKey: String) {
        val appName = settingsViewModel.getAppName()
        if (appName.isNullOrBlank()) {
            onOverlayLog("Error: No project selected. Open or create a project before asking Gemini.")
            return
        }
        val adapter = geminiAdapterFactory(apiKey, appName)

        // updateAndGet returns the post-update snapshot so we can hand the
        // exact history we appended to into chat(). Using atomic update
        // ops on both sides means a second prompt that lands while
        // chat() is in flight can't overwrite the user turn we just
        // recorded — its messages slot in around ours instead of replacing
        // them. Conversation order across concurrent prompts is therefore
        // non-deterministic, but no message is lost.
        val historyForChat = _geminiHistory.updateAndGet { it + ChatMessage("user", richPrompt) }

        val response = adapter.chat(historyForChat)

        _geminiHistory.update { it + ChatMessage("model", response) }
        onOverlayLog("Gemini: $response")
        onFilesChanged()
    }

    // --- Private Implementation ---

    /**
     * Returns the repo's current default branch from GitHub, persisting the
     * result so the UI catches up. Falls back to the stored value (which
     * itself defaults to "main") if no token is set or the API call fails —
     * we'd rather try a slightly stale branch than block the prompt
     * entirely on a network hiccup.
     */
    private suspend fun resolveDefaultBranch(user: String, appName: String): String {
        val stored = settingsViewModel.getBranchName()
        val token = settingsViewModel.getGithubToken() ?: return stored
        return try {
            val remote = GitHubApiClient.createService(token)
                .getRepo(user, appName)
                .defaultBranch
                ?.takeIf { it.isNotBlank() }
                ?: return stored
            if (remote != stored) {
                settingsViewModel.saveBranchName(remote)
            }
            remote
        } catch (e: CancellationException) {
            // Structured-concurrency contract: never swallow cancellation.
            throw e
        } catch (e: Exception) {
            stored
        }
    }

    /**
     * Hands the prompt to Jules via [julesAdapter] and reacts to its [TaskEvent]s:
     * tracks the session, logs agent messages, and applies returned patches. The
     * session/poll lifecycle lives in [JulesAdapter] now; this just wires events to
     * the UI and the patch-apply callback.
     *
     * (PR-based loop — auto-merge + Actions rebuild — is the next Phase-2 increment;
     * for now patches are applied to the working tree as before.)
     */
    private suspend fun runJulesTask(promptText: String) {
        val appName = settingsViewModel.getAppName() ?: "project"
        val user = settingsViewModel.getGithubUser() ?: "user"

        // Refresh the branch from GitHub before handing off to Jules. The stored
        // value defaults to "main" but real repos may use "master" or anything else;
        // if we send the wrong name Jules' clone fails with "Remote branch <X> not
        // found in upstream origin" and the session never starts. Fall back to
        // whatever we have locally on any API failure (offline, missing token, etc.).
        val branch = resolveDefaultBranch(user, appName)
        val sourceContext = SourceContext(
            source = "sources/github/$user/$appName",
            githubRepoContext = GitHubRepoContext(branch)
        )

        julesAdapter.dispatchTask(promptText, sourceContext, _currentJulesSessionId.value)
            .collect { event -> handleJulesEvent(event) }
    }

    /**
     * Reacts to a single [TaskEvent] from the Jules loop. Extracted from the
     * collector so it can be unit-tested directly without standing up the whole
     * session/branch-resolution path. `suspend` because applying a patch
     * ([onUnidiffPatchReceived]) is a suspend call.
     */
    internal suspend fun handleJulesEvent(event: TaskEvent) {
        when (event) {
            is TaskEvent.SessionStarted -> {
                _currentJulesSessionId.value = event.session.id
                _julesResponse.value = event.session
            }
            is TaskEvent.Message -> onOverlayLog("Jules: ${event.text}")
            is TaskEvent.Patch -> {
                onOverlayLog("Patch received. Applying...")
                val success = onUnidiffPatchReceived(event.unidiff)
                onOverlayLog(if (success) "Patch applied." else "Patch failed to apply.")
            }
            is TaskEvent.PullRequest -> {
                onOverlayLog("Jules opened a PR (${event.title}). Merging and rebuilding...")
                onAgentPullRequest(event.url)
            }
            TaskEvent.TimedOut ->
                onOverlayLog("Jules: no response yet. Try resending or switching to Gemini.")
        }
    }

    companion object {
        /**
         * The overlay model to use given the stored [assignmentId] and [projectType].
         * An explicit assignment always wins; otherwise the default is type-aware —
         * Android targets get Jules (agentic/PR-based), web-like projects get Gemini.
         */
        fun defaultOverlayModel(assignmentId: String?, projectType: ProjectType): AiModel =
            AiModels.findById(assignmentId)
                ?: if (projectType.isWebLike()) AiModels.GEMINI else AiModels.JULES
    }
}
