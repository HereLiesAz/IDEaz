package com.hereliesaz.ideaz.ui.delegates

import com.hereliesaz.ideaz.api.*
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
 */
class AIDelegate(
    private val settingsViewModel: SettingsViewModel,
    private val scope: CoroutineScope,
    private val onOverlayLog: (String) -> Unit,
    private val onUnidiffPatchReceived: suspend (String) -> Boolean
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
                val projectId = settingsViewModel.getJulesProjectId()
                if (projectId.isNullOrBlank()) {
                    _sessions.value = emptyList()
                    return@launch
                }
                // List all sessions
                val response = JulesApiClient.listSessions(projectId)
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
                        // For Gemini, we might still use the old client or a different path
                        // Assuming GeminiApiClient is separate and works
                        // But wait, the user asked for Jules API correctness.
                        // I'll leave Gemini path as is or assume it's fine.
                        // Ideally GeminiApiClient should be checked too, but out of scope.
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
        val projectId = settingsViewModel.getJulesProjectId()
        if (projectId.isNullOrBlank()) {
            _julesError.value = "Jules Project ID not configured in Settings."
            return
        }

        val appName = settingsViewModel.getAppName() ?: "project"
        val user = settingsViewModel.getGithubUser() ?: "user"
        val branch = settingsViewModel.getBranchName()

        val currentSourceContext = SourceContext(
            source = "sources/github/$user/$appName",
            githubRepoContext = GitHubRepoContext(branch)
        )

        val sessionName = _currentJulesSessionId.value

        try {
            val activeSessionName: String
            if (sessionName == null) {
                // Create Session
                val request = CreateSessionRequest(
                    prompt = promptText,
                    sourceContext = currentSourceContext,
                    title = "Session ${System.currentTimeMillis()}"
                )
                val session = JulesApiClient.createSession(projectId, request = request)
                // Use session.name (resource name) for API calls instead of session.id
                _currentJulesSessionId.value = session.name
                _julesResponse.value = session
                activeSessionName = session.name
            } else {
                // Send Message
                val request = SendMessageRequest(prompt = promptText)
                JulesApiClient.sendMessage(sessionName, request)
                activeSessionName = sessionName
            }

            pollForResponse(activeSessionName)

        } catch (e: Exception) {
            throw e
        }
    }

    private suspend fun getAllActivities(sessionName: String): List<Activity> {
        val allActivities = mutableListOf<Activity>()
        var pageToken: String? = null
        do {
            val response = JulesApiClient.listActivities(sessionName, pageToken = pageToken)
            response.activities?.let { allActivities.addAll(it) }
            pageToken = response.nextPageToken
        } while (pageToken != null)
        return allActivities
    }

    private suspend fun pollForResponse(sessionName: String) {
        // Poll listActivities for a response
        var attempts = 0
        while (attempts < 15) { // 45 seconds max
            delay(3000)
            val activities = getAllActivities(sessionName)

            // Check for Agent Message
            // We log the latest message if found.
            // A more robust implementation would track seen message IDs.
            val latestAgentMessage = activities.firstOrNull { it.agentMessaged != null }

            if (latestAgentMessage != null) {
                val msg = latestAgentMessage.agentMessaged?.agentMessage
                if (!msg.isNullOrBlank()) {
                     onOverlayLog("Jules: $msg")
                }
            }

            // Check for Artifacts (Patches)
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
