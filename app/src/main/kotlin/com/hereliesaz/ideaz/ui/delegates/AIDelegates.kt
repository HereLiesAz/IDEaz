package com.hereliesaz.ideaz.ui.delegates

import com.hereliesaz.ideaz.api.GeminiApiClient
import com.hereliesaz.ideaz.jules.*
import com.hereliesaz.ideaz.ui.AiModels
import com.hereliesaz.ideaz.ui.SettingsViewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class AIDelegate(
    private val settingsViewModel: SettingsViewModel,
    private val scope: CoroutineScope,
    private val onOverlayLog: (String) -> Unit,
    private val onPatchReceived: suspend (Patch) -> Boolean
) {

    private val _currentJulesSessionId = MutableStateFlow<String?>(null)
    val currentJulesSessionId = _currentJulesSessionId.asStateFlow()

    private val _julesResponse = MutableStateFlow<GenerateResponseResponse?>(null)
    val julesResponse = _julesResponse.asStateFlow()

    private val _julesHistory = MutableStateFlow<List<Message>>(emptyList())
    val julesHistory = _julesHistory.asStateFlow()

    private val _isLoadingJulesResponse = MutableStateFlow(false)
    val isLoadingJulesResponse = _isLoadingJulesResponse.asStateFlow()

    private val _julesError = MutableStateFlow<String?>(null)
    val julesError = _julesError.asStateFlow()

    private val _sessions = MutableStateFlow<List<com.hereliesaz.ideaz.api.Session>>(emptyList())
    val sessions = _sessions.asStateFlow()

    private var contextualTaskJob: Job? = null

    fun resumeSession(sessionId: String) {
        _currentJulesSessionId.value = sessionId
    }

    fun fetchSessionsForRepo(repoName: String) {
        scope.launch {
            try {
                val rawParent = settingsViewModel.getJulesProjectId()
                val parent = if (rawParent.isNullOrBlank()) null else {
                    val trimmed = rawParent.trim()
                    if (trimmed.all { it.isDigit() }) "projects/$trimmed" else trimmed
                }

                if (parent == null) {
                    _sessions.value = emptyList()
                    return@launch
                }

                val response = JulesApiClient.listSessions(parent)
                val allSessions = response.sessions ?: emptyList()
                val user = settingsViewModel.getGithubUser() ?: ""
                val fullRepo = if (repoName.contains("/")) repoName else "$user/$repoName"
                val targetSource = "sources/github/$fullRepo"

                val filtered = allSessions.filter { session ->
                    val source = session.sourceContext.source
                    source.equals(targetSource, ignoreCase = true)
                }
                _sessions.value = filtered
            } catch (e: Exception) {
                _sessions.value = emptyList()
            }
        }
    }

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
                        val response = GeminiApiClient.generateContent(richPrompt, key)
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
        val rawParent = settingsViewModel.getJulesProjectId()
        val parent = if (rawParent.isNullOrBlank()) null else {
            val trimmed = rawParent.trim()
            if (trimmed.all { it.isDigit() }) "projects/$trimmed" else trimmed
        }

        if (parent.isNullOrBlank()) {
            onOverlayLog("Error: Jules Project ID not configured.")
            return
        }

        val currentSourceContext = Prompt.SourceContext(
            name = "sources/github/$user/$appName",
            gitHubRepoContext = Prompt.GitHubRepoContext(branch)
        )

        val currentSessionDetails = _currentJulesSessionId.value?.let { sessionId ->
            SessionDetails(id = sessionId)
        }

        val prompt = Prompt(
            parent = parent,
            session = currentSessionDetails,
            query = promptText,
            sourceContext = currentSourceContext,
            history = _julesHistory.value.takeLast(10)
        )

        val response = JulesApiClient.generateResponse(prompt)
        _julesResponse.value = response
        _julesHistory.value += response.message
        _currentJulesSessionId.value = response.session.id

        response.patch?.let { patch ->
            onOverlayLog("Patch received. Applying...")
            val patchSuccess = onPatchReceived(patch)
            if (patchSuccess) {
                onOverlayLog("Patch applied successfully.")
            } else {
                onOverlayLog("Error applying patch.")
            }
        }
    }
}