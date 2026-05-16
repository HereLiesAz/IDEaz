package com.hereliesaz.ideaz.ui.delegates

import com.hereliesaz.ideaz.ai.ChatMessage
import com.hereliesaz.ideaz.ai.GeminiAdapter
import com.hereliesaz.ideaz.ai.IdeTools
import com.hereliesaz.ideaz.api.*
import com.hereliesaz.ideaz.jules.IJulesApiClient
import com.hereliesaz.ideaz.jules.JulesApiClient
import com.hereliesaz.ideaz.ui.AiModels
import com.hereliesaz.ideaz.ui.SettingsViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
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
    private val onFilesChanged: () -> Unit = {}
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

    /**
     * Set of Activity IDs that have already been processed in the current session.
     * Prevents re-applying the same patch or showing the same message multiple times during polling.
     */
    private val processedActivityIds = mutableSetOf<String>()

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
        processedActivityIds.clear()
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
                    val source = session.sourceContext?.source
                    source?.equals(targetSource, ignoreCase = true) == true
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

        // Resolve Model and Key. Phase 1 default is Gemini; Jules requires explicit
        // assignment via Settings → AI Assignments.
        val modelId = settingsViewModel.getAiAssignment(SettingsViewModel.KEY_AI_ASSIGNMENT_OVERLAY)
        val model = AiModels.findById(modelId) ?: AiModels.GEMINI
        val key = settingsViewModel.getApiKey(model.requiredKey)

        if (key.isNullOrBlank()) {
            onOverlayLog("Error: API Key missing for ${model.displayName}")
            _isLoadingJulesResponse.value = false
            return
        }

        contextualTaskJob = scope.launch {
            try {
                when (model.id) {
                    AiModels.JULES_DEFAULT -> runJulesTask(richPrompt)
                    AiModels.GEMINI_FLASH -> runGeminiTask(richPrompt, key)
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
     * Handles the interaction with the Jules API.
     *
     * **Logic:**
     * - If no session is active, creates a new one ([CreateSessionRequest]).
     * - If a session exists, sends a message to it ([SendMessageRequest]).
     * - Initiates [pollForResponse] to wait for the agent's reply and actions.
     */
    private suspend fun runJulesTask(promptText: String) {
        val appName = settingsViewModel.getAppName() ?: "project"
        val user = settingsViewModel.getGithubUser() ?: "user"

        // Refresh the branch from GitHub before handing off to Jules. The
        // stored value defaults to "main" but real repos may use "master"
        // or anything else; if we send the wrong name Jules' clone fails
        // with "Remote branch <X> not found in upstream origin" and the
        // session never starts. Fall back to whatever we have locally on
        // any API failure (offline, missing token, rate-limited).
        val branch = resolveDefaultBranch(user, appName)

        // Construct SourceContext for the API
        val currentSourceContext = SourceContext(
            source = "sources/github/$user/$appName",
            githubRepoContext = GitHubRepoContext(branch)
        )

        val sessionId = _currentJulesSessionId.value

        try {
            val activeSessionId: String
            if (sessionId == null) {
                // CREATE new session
                val request = CreateSessionRequest(
                    prompt = promptText,
                    sourceContext = currentSourceContext,
                    title = "Session ${System.currentTimeMillis()}"
                )
                val session = julesApiClient.createSession(request = request)
                _currentJulesSessionId.value = session.id
                _julesResponse.value = session
                activeSessionId = session.id
            } else {
                // SEND to existing session
                val request = SendMessageRequest(prompt = promptText)
                julesApiClient.sendMessage(sessionId, request)
                activeSessionId = sessionId
            }

            pollForResponse(activeSessionId)

        } catch (e: Exception) {
            throw e
        }
    }

    /**
     * Polls the Jules API for activities (responses) associated with the session.
     *
     * **Strategy:**
     * - Polls every 3 seconds, up to 15 times (45 seconds timeout).
     * - Fetches *all* activities via pagination ([getAllActivities]).
     * - Checks for `agentMessage` to update the UI chat.
     * - Checks for `artifacts` containing `gitPatch` to apply code changes.
     */
    private suspend fun pollForResponse(sessionId: String) {
        val maxAttempts = 15
        var attempts = 0
        while (attempts < maxAttempts) { // 45 seconds max wait
            delay(3000)

            // Retrieve full activity history
            val activities = getAllActivities(sessionId)

            // 1. Update Chat UI
            // Find the most recent agent message to display
            val latestAgentMessage = activities.firstOrNull { it.agentMessaged != null }
            if (latestAgentMessage != null) {
                val msg = latestAgentMessage.agentMessaged?.agentMessage
                if (!msg.isNullOrBlank()) {
                     onOverlayLog("Jules: $msg")
                }
            }

            // 2. Process Artifacts (Patches)
            activities.forEach { activity ->
                // Only process each activity once
                if (activity.id !in processedActivityIds) {
                    var activityProcessed = false

                    activity.artifacts?.forEach { artifact ->
                        val patch = artifact.changeSet?.gitPatch?.unidiffPatch
                        if (!patch.isNullOrBlank()) {
                            onOverlayLog("Patch received via Activity ${activity.id}. Applying...")

                            // Apply Patch
                            val success = onUnidiffPatchReceived(patch)

                            if (success) {
                                onOverlayLog("Patch applied.")
                                activityProcessed = true
                            } else {
                                onOverlayLog("Patch failed to apply.")
                            }
                        }
                    }
                    if (activityProcessed) {
                        processedActivityIds.add(activity.id)
                    }
                }
            }
            attempts++
        }
        // Loop exited without finding a final agent message. Surface this to the
        // user instead of leaving them with a silent stuck spinner.
        onOverlayLog("Jules: no response after ${maxAttempts * 3}s. Try resending or switching to Gemini.")
    }

    /**
     * Helper to fetch all pages of activities for a session.
     */
    private suspend fun getAllActivities(sessionId: String): List<Activity> {
        val allActivities = mutableListOf<Activity>()
        var pageToken: String? = null
        do {
            val response = julesApiClient.listActivities(sessionId, pageToken = pageToken)
            response.activities?.let { allActivities.addAll(it) }
            pageToken = response.nextPageToken
        } while (pageToken != null)
        return allActivities
    }
}
