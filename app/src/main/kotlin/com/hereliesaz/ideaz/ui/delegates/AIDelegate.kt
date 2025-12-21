package com.hereliesaz.ideaz.ui.delegates

import com.hereliesaz.ideaz.api.*
import com.hereliesaz.ideaz.jules.IJulesApiClient
import com.hereliesaz.ideaz.jules.JulesApiClient
import com.hereliesaz.ideaz.ui.AiModels
import com.hereliesaz.ideaz.ui.SettingsViewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class Message(val role: String, val content: String)

/**
 * Delegate responsible for handling AI interactions, including session management,
 * fetching sessions from Jules API, and executing contextual AI tasks.
 *
 * Key Responsibilities:
 * - [fetchSessionsForRepo]: Retrieves active sessions for the current repository from the Jules API.
 * - [startContextualAITask]: Initiates a new AI task based on user input or overlay selection.
 * - [runJulesTask]: Handles the specific logic for communicating with the Jules API (create session / send message).
 * - [pollForResponse]: Polls the API for activities (messages, artifacts) after a request is sent.
 */
class AIDelegate(
    private val settingsViewModel: SettingsViewModel,
    private val scope: CoroutineScope,
    private val onOverlayLog: (String) -> Unit,
    private val onUnidiffPatchReceived: suspend (String) -> Boolean,
    private val julesApiClient: IJulesApiClient = JulesApiClient
) {

    private val _currentJulesSessionId = MutableStateFlow<String?>(null)
    /** The Resource Name of the currently active Jules session. */
    val currentJulesSessionId = _currentJulesSessionId.asStateFlow()

    private val _julesResponse = MutableStateFlow<Session?>(null)
    /** The most recent session state from the Jules API. */
    val julesResponse = _julesResponse.asStateFlow()

    private val _julesHistory = MutableStateFlow<List<Message>>(emptyList())
    /** The history of messages in the current AI conversation. */
    val julesHistory = _julesHistory.asStateFlow()

    private val _isLoadingJulesResponse = MutableStateFlow(false)
    /** Indicates whether an AI request is currently in progress. */
    val isLoadingJulesResponse = _isLoadingJulesResponse.asStateFlow()

    private val _julesError = MutableStateFlow<String?>(null)
    /** Holds any error message from the last AI operation. */
    val julesError = _julesError.asStateFlow()

    private val _sessions = MutableStateFlow<List<Session>>(emptyList())
    /** The list of available Jules sessions for the current repository. */
    val sessions = _sessions.asStateFlow()

    private var contextualTaskJob: Job? = null
    private val processedActivityIds = mutableSetOf<String>()

    /**
     * Resumes an existing Jules session by its Name.
     */
    fun resumeSession(sessionName: String) {
        _currentJulesSessionId.value = sessionName
    }

    /**
     * Fetches the list of active sessions associated with the specified repository.
     * Filters sessions based on the 'source' context.
     */
    fun fetchSessionsForRepo(repoName: String) {
        scope.launch {
            try {
                // List all sessions (API change: no arguments)
                val response = julesApiClient.listSessions()
                val allSessions = response.sessions ?: emptyList()
                val user = settingsViewModel.getGithubUser() ?: ""
                val fullRepo = if (repoName.contains("/")) repoName else "$user/$repoName"
                val targetSource = "sources/github/$fullRepo"

                val filtered = allSessions.filter { session ->
                    val source = session.sourceContext?.source
                    source?.equals(targetSource, ignoreCase = true) == true
                }
                _sessions.value = filtered
            } catch (e: Exception) {
                _sessions.value = emptyList()
            }
        }
    }

    /**
     * Starts a contextual AI task with the given prompt.
     * Uses the AI model selected in settings (Jules or Gemini).
     */
    fun startContextualAITask(richPrompt: String) {
        onOverlayLog("Thinking...")
        _isLoadingJulesResponse.value = true
        _julesError.value = null

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

    private suspend fun runJulesTask(promptText: String) {
        // Project ID is no longer required for API calls (implied by API key),
        // but we might check it if needed for other reasons.
        // The SDK doesn't use it in calls.

        val appName = settingsViewModel.getAppName() ?: "project"
        val user = settingsViewModel.getGithubUser() ?: "user"
        val branch = settingsViewModel.getBranchName()

        val currentSourceContext = SourceContext(
            source = "sources/github/$user/$appName",
            githubRepoContext = GitHubRepoContext(branch)
        )

        val sessionId = _currentJulesSessionId.value

        try {
            val activeSessionId: String
            if (sessionId == null) {
                // Create Session
                val request = CreateSessionRequest(
                    prompt = promptText,
                    sourceContext = currentSourceContext,
                    title = "Session ${System.currentTimeMillis()}"
                )
                // API Change: No projectId/location args
                val session = julesApiClient.createSession(request = request)
                _currentJulesSessionId.value = session.id
                _julesResponse.value = session
                activeSessionId = session.id
            } else {
                // Send Message
                val request = SendMessageRequest(prompt = promptText)
                julesApiClient.sendMessage(sessionId, request)
                activeSessionId = sessionId
            }

            pollForResponse(activeSessionId)

        } catch (e: Exception) {
            throw e
        }
    }

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

    private suspend fun pollForResponse(sessionId: String) {
        var attempts = 0
        while (attempts < 15) { // 45 seconds max
            delay(3000)
            val activities = getAllActivities(sessionId)

            val latestAgentMessage = activities.firstOrNull { it.agentMessaged != null }

            if (latestAgentMessage != null) {
                val msg = latestAgentMessage.agentMessaged?.agentMessage
                if (!msg.isNullOrBlank()) {
                     onOverlayLog("Jules: $msg")
                }
            }

            activities.forEach { activity ->
                if (activity.id !in processedActivityIds) {
                    var activityProcessed = false
                    activity.artifacts?.forEach { artifact ->
                        val patch = artifact.changeSet?.gitPatch?.unidiffPatch
                        if (!patch.isNullOrBlank()) {
                            onOverlayLog("Patch received via Activity ${activity.id}. Applying...")
                            val success = onUnidiffPatchReceived(patch)
                            if (success) {
                                onOverlayLog("Patch applied.")
                                activityProcessed = true
                            } else {
                                onOverlayLog("Patch failed.")
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
}
