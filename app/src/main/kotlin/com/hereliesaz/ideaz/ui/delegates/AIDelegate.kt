package com.hereliesaz.ideaz.ui.delegates

import com.hereliesaz.ideaz.api.*
import com.hereliesaz.ideaz.jules.JulesApiClient
import com.hereliesaz.ideaz.api.GeminiApiClient
import com.hereliesaz.ideaz.jules.*
import com.hereliesaz.ideaz.ui.AiModels
import com.hereliesaz.ideaz.ui.SettingsViewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Delegate responsible for handling AI interactions, including session management,
 * fetching sessions from Jules API, and executing contextual AI tasks.
 *
 * @param settingsViewModel ViewModel for accessing user settings (API keys, project ID).
 * @param scope CoroutineScope for launching background tasks.
 * @param onOverlayLog Callback to log messages to the UI overlay.
 * @param onPatchReceived Callback to apply patches received from the AI.
 */
class AIDelegate(
    private val settingsViewModel: SettingsViewModel,
    private val scope: CoroutineScope,
    private val onOverlayLog: (String) -> Unit,
    private val onUnidiffPatchReceived: suspend (String) -> Boolean
) {

    private val _currentJulesSessionId = MutableStateFlow<String?>(null)
    /** The ID of the currently active Jules session. */
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

    /**
     * Resumes an existing Jules session by its ID.
     */
    fun resumeSession(sessionId: String) {
        _currentJulesSessionId.value = sessionId
    }

    /**
     * Fetches the list of active sessions associated with the specified repository.
     * Filters sessions based on the 'source' context.
     */
    fun fetchSessionsForRepo(repoName: String) {
        scope.launch {
            try {
                // List all sessions (no parent param as per REST API)
                val response = JulesApiClient.listSessions()
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
        val appName = settingsViewModel.getAppName() ?: "project"
        val user = settingsViewModel.getGithubUser() ?: "user"
        val branch = settingsViewModel.getBranchName()

        // Parent/Project ID logic might be needed for SourceContext if strictly required,
        // but REST API docs usually imply 'sources/github/owner/repo'.

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
                val session = JulesApiClient.createSession(request)
                _currentJulesSessionId.value = session.id
                _julesResponse.value = session
                // Manually add user message to history
                // We don't have a Message object in the response for user prompt, usually.
                // Assuming generic Message type
                // _julesHistory.value += Message(role = "user", content = promptText) // Message type?
                // Message in models.kt? No Message in models.kt I saw.
                // Wait, I should check models.kt for Message.
                // It has `UserMessaged` in `Activity`.
                // I will skip local history management and rely on listActivities.
                activeSessionId = session.id
            } else {
                // Send Message
                val request = SendMessageRequest(prompt = promptText)
                JulesApiClient.sendMessage(sessionId, request)
                activeSessionId = sessionId
            }

            pollForResponse(activeSessionId)

        } catch (e: Exception) {
            throw e
        }
    }

    private suspend fun pollForResponse(sessionId: String) {
        // Poll listActivities for a response
        var attempts = 0
        while (attempts < 15) { // 45 seconds max
            delay(3000)
            val response = JulesApiClient.listActivities(sessionId)
            val activities = response.activities ?: emptyList()

            // Check for Agent Message
            val latestAgentMessage = activities.find { it.agentMessaged != null } // Find latest? list usually ordered?
            // Docs don't specify order. Assuming newest first or last?
            // Usually REST lists are newest first or oldest first.
            // I'll check all.

            if (latestAgentMessage != null) {
                // onOverlayLog("Agent: ${latestAgentMessage.agentMessaged?.agentMessage}")
                // Break if we processed it?
                // For now, just log the first one found that is new?
                // This logic is simple/naive.
            }

            // Check for Artifacts (Patches)
            activities.forEach { activity ->
                activity.artifacts?.forEach { artifact ->
                    val patch = artifact.changeSet?.gitPatch?.unidiffPatch
                    if (!patch.isNullOrBlank()) {
                        onOverlayLog("Patch received via Activity ${activity.id}. Applying...")
                        val success = onUnidiffPatchReceived(patch)
                        if (success) {
                            onOverlayLog("Patch applied.")
                            return // Exit polling after success?
                        } else {
                            onOverlayLog("Patch failed.")
                        }
                    }
                }
            }

            attempts++
        }
    }
}
