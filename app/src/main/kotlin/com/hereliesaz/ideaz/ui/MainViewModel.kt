package com.hereliesaz.ideaz.ui

import android.app.Application
import android.content.Intent
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.hereliesaz.ideaz.api.GitHubApiClient
import com.hereliesaz.ideaz.ui.delegates.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

class MainViewModel(application: Application) : AndroidViewModel(application) {

    val settingsViewModel = SettingsViewModel(application)

    private val _autoLaunchApk = MutableStateFlow<String?>(null)
    val autoLaunchApk = _autoLaunchApk.asStateFlow()

    private val logHandler = object {
        fun onBuildLog(msg: String) = stateDelegate.appendBuildLog(msg)
        fun onAiLog(msg: String) = stateDelegate.appendAiLog(msg)
        fun onProgress(p: Int?) = stateDelegate.setLoadingProgress(p)
        fun onGitProgress(p: Int, task: String) = stateDelegate.setLoadingProgress(p)
    }

    val stateDelegate = StateDelegate()

    val gitDelegate = GitDelegate(
        settingsViewModel,
        viewModelScope,
        logHandler::onBuildLog,
        logHandler::onProgress
    )

    val aiDelegate = AIDelegate(
        settingsViewModel,
        viewModelScope,
        logHandler::onAiLog,
        gitDelegate::applyUnidiffPatch
    )

    val overlayDelegate = OverlayDelegate(
        application,
        settingsViewModel,
        viewModelScope,
        logHandler::onAiLog
    )

    val buildDelegate = BuildDelegate(
        application,
        settingsViewModel,
        viewModelScope,
        logHandler::onBuildLog,
        logHandler::onAiLog,
        { sourceMap -> overlayDelegate.sourceMap = sourceMap },
        { errorLog -> aiDelegate.startContextualAITask("Build failed with error:\n$errorLog") },
        { apkPath -> stateDelegate.setCurrentWebUrl(apkPath) },
        gitDelegate
    )

    val repoDelegate = RepoDelegate(
        application,
        settingsViewModel,
        viewModelScope,
        logHandler::onBuildLog,
        logHandler::onAiLog,
        logHandler::onProgress,
        logHandler::onGitProgress
    )

    val updateDelegate = UpdateDelegate(
        application,
        settingsViewModel,
        viewModelScope,
        logHandler::onAiLog
    )

    val systemEventDelegate = SystemEventDelegate(
        application,
        aiDelegate,
        overlayDelegate,
        stateDelegate
    )

    // --- State ---
    val loadingProgress = stateDelegate.loadingProgress
    val isTargetAppVisible = stateDelegate.isTargetAppVisible
    val currentWebUrl = stateDelegate.currentWebUrl
    val buildLog = stateDelegate.buildLog
    val filteredLog = stateDelegate.filteredLog
    val pendingRoute = stateDelegate.pendingRoute

    val isSelectMode = overlayDelegate.isSelectMode
    val activeSelectionRect = overlayDelegate.activeSelectionRect
    val isContextualChatVisible = overlayDelegate.isContextualChatVisible

    val gitStatus = gitDelegate.gitStatus
    val commitHistory = gitDelegate.commitHistory
    val branches = gitDelegate.branches
    val sessions = aiDelegate.sessions
    
    // UI Flows
    val ownedRepos = repoDelegate.ownedRepos
    val updateVersion = updateDelegate.updateVersion
    val dependencies = MutableStateFlow<List<Dependency>>(emptyList()).asStateFlow()

    // --- Methods ---

    fun setPendingRoute(route: String?) = stateDelegate.setPendingRoute(route)

    fun toggleSelectMode() = overlayDelegate.toggleSelectMode(!overlayDelegate.isSelectMode.value)
    fun clearSelection() = overlayDelegate.clearSelection()
    fun closeContextualChat() = overlayDelegate.clearSelection() 

    fun setScreenCapturePermission(code: Int, data: Intent) = overlayDelegate.setScreenCapturePermission(code, data)
    fun hasScreenCapturePermission() = overlayDelegate.hasScreenCapturePermission()
    fun requestScreenCapturePermission() = overlayDelegate.requestScreenCapturePermission()

    fun sendPrompt(prompt: String, context: String? = null) {
        aiDelegate.startContextualAITask(prompt)
    }

    fun submitContextualPrompt(prompt: String) {
        aiDelegate.startContextualAITask(prompt)
        closeContextualChat()
    }

    fun resumeSession(id: String) = aiDelegate.resumeSession(id)

    fun refreshGitData() = gitDelegate.refreshGitData()
    fun gitFetch() = gitDelegate.fetch()
    fun gitPull() = gitDelegate.pull()
    fun gitPush() = gitDelegate.push()
    fun gitStash(msg: String) = gitDelegate.stash(msg)
    fun gitUnstash() = gitDelegate.unstash()
    fun switchBranch(branch: String) = gitDelegate.switchBranch(branch)
    fun forceUpdateInitFiles() = repoDelegate.forceUpdateInitFiles()

    fun loadProject(path: String, callback: () -> Unit) {
        settingsViewModel.setAppName(File(path).name)
        refreshGitData()
        callback()
    }
    
    fun scanLocalProjects() = repoDelegate.scanLocalProjects()
    fun getLocalProjectsWithMetadata() = repoDelegate.getLocalProjectsWithMetadata()
    fun deleteProject(name: String) {
        val dir = File(getApplication<Application>().filesDir, name)
        if (dir.exists()) dir.deleteRecursively()
        settingsViewModel.removeProject(name)
    }

    fun createGitHubRepository(name: String, desc: String, private: Boolean, type: com.hereliesaz.ideaz.models.ProjectType, pkg: String, ctx: android.content.Context, callback: (String, String) -> Unit) {
        repoDelegate.createGitHubRepository(name, desc, private, type, pkg, ctx, callback)
    }
    
    fun forkRepository(owner: String, repo: String, callback: (String, String, String) -> Unit) {
        repoDelegate.forkRepository(owner, repo, callback)
    }
    
    fun fetchGitHubRepos() = repoDelegate.fetchGitHubRepos()
    fun uploadProjectSecrets(owner: String, repo: String) = repoDelegate.uploadProjectSecrets(owner, repo)

    fun fetchSessionsForRepo(repoName: String) = aiDelegate.fetchSessionsForRepo(repoName)

    fun checkRequiredKeys() = settingsViewModel.checkRequiredKeys()

    fun saveAndInitialize(name: String, user: String, branch: String) {
        settingsViewModel.saveProjectConfig(name, user, branch)
        refreshGitData()
    }
    
    fun loadDependencies() {
        // Mocked
    }
    
    fun addDependencyViaAI(dep: String) {
        aiDelegate.startContextualAITask("Add dependency: $dep")
    }

    // Updates
    fun checkForExperimentalUpdates() = updateDelegate.checkForExperimentalUpdates()
    fun downloadBuildTools() = buildDelegate.downloadDependencies()
    fun clearBuildCaches() { /* No-op or delegate call */ }

    val updateStatus = updateDelegate.updateStatus
    val showUpdateWarning = updateDelegate.showUpdateWarning
    val updateMessage = updateDelegate.updateMessage

    fun confirmUpdate() = updateDelegate.confirmUpdate()
    fun dismissUpdateWarning() = updateDelegate.dismissUpdateWarning()

    fun flushNonFatalErrors() {
        // Placeholder
    }

    fun selectRepositoryForSetup(repo: com.hereliesaz.ideaz.api.GitHubRepoResponse, onSuccess: () -> Unit) {
        repoDelegate.selectRepositoryForSetup(repo) { _, _ -> onSuccess() }
    }

    // --- Container ---

    fun onPreviewLaunched() {
        _autoLaunchApk.value = null
    }

    private var pollingJob: Job? = null

    fun startRemoteBuildPoll() {
        if (!settingsViewModel.isLocalBuildEnabled()) {
            pollRemoteBuild()
        }
    }

    private fun pollRemoteBuild() {
        pollingJob?.cancel()
        pollingJob = viewModelScope.launch(Dispatchers.IO) {
            val token = settingsViewModel.getGithubToken() ?: return@launch
            val owner = settingsViewModel.getGithubUser() ?: ""
            val repo = settingsViewModel.getAppName() ?: ""

            try {
                val service = GitHubApiClient.createService(token)
                val runs = service.getWorkflowRuns(owner, repo)
                val latest = runs.workflowRuns.firstOrNull()

                if (latest?.status == "completed" && latest.conclusion == "success") {
                    val artifacts = service.getRunArtifacts(owner, repo, latest.id)
                    val apkArtifact = artifacts.artifacts.find { it.name.contains("apk") }

                    if (apkArtifact != null) {
                        // Download logic
                    }
                }
            } catch (e: Exception) {
                // Ignore
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        buildDelegate.unbindService(getApplication())
    }
}