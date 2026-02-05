package com.hereliesaz.ideaz.ui.delegates

import com.hereliesaz.ideaz.api.*
import com.hereliesaz.ideaz.jules.IJulesApiClient
import com.hereliesaz.ideaz.jules.JulesApiClient
import com.hereliesaz.ideaz.ui.AiModels
import com.hereliesaz.ideaz.ui.SettingsViewModel
import com.hereliesaz.ideaz.services.JsCompilerService
import com.hereliesaz.ideaz.models.ProjectType
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Data class representing a simple chat message structure for the UI history.
 * @param role The role of the sender ("user" or "agent").
 * @param content The text content of the message.
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
 * @param jsCompilerService Optional service for compiling Web projects (Kotlin/JS) after a patch.
 * @param onWebReload Callback to trigger a WebView reload after a successful Web build.
 */
class AIDelegate(
    private val settingsViewModel: SettingsViewModel,
    private val scope: CoroutineScope,
    private val onOverlayLog: (String) -> Unit,
    private val onUnidiffPatchReceived: suspend (String) -> Boolean,
    private val julesApiClient: IJulesApiClient = JulesApiClient,
    private val jsCompilerService: JsCompilerService? = null,
    private val onWebReload: (() -> Unit)? = null
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

        // Resolve Model and Key
        val modelId = settingsViewModel.getAiAssignment(SettingsViewModel.KEY_AI_ASSIGNMENT_OVERLAY)
        val model = AiModels.findById(modelId) ?: AiModels.JULES
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
                    AiModels.GEMINI_FLASH -> {
                        // Gemini fallback (Simple request/response, no session/patching yet)
                        val response = com.hereliesaz.ideaz.api.GeminiApiClient.generateContent(richPrompt, key)
                        onOverlayLog(response)
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

    // --- Private Implementation ---

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
        val branch = settingsViewModel.getBranchName()

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
        var attempts = 0
        while (attempts < 15) { // 45 seconds max wait
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

                                // Special Handling for Web Projects:
                                // If a patch is applied, we must recompile the Kotlin/JS code to see changes.
                                if (settingsViewModel.getProjectType() == ProjectType.WEB.name && jsCompilerService != null) {
                                    val appName = settingsViewModel.getAppName()
                                    if (appName != null) {
                                        onOverlayLog("Compiling Web Project...")
                                        val projectDir = settingsViewModel.getProjectPath(appName)

                                        // Run compiler on IO thread (blocking op)
                                        withContext(Dispatchers.IO) {
                                            val result = jsCompilerService.compileProject(projectDir)
                                            withContext(Dispatchers.Main) {
                                                if (result.success) {
                                                    onOverlayLog("Compilation successful. Reloading...")
                                                    onWebReload?.invoke()
                                                } else {
                                                    onOverlayLog("Compilation failed:\n${result.logs}")
                                                }
                                            }
                                        }
                                    }
                                }
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
