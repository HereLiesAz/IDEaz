package com.hereliesaz.ideaz.ui

import android.app.Application
import android.content.Intent
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.hereliesaz.ideaz.ui.delegates.*
import com.hereliesaz.ideaz.ui.Dependency
import com.hereliesaz.ideaz.models.ProjectType
import com.hereliesaz.ideaz.models.ProjectItem
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.SharingStarted
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
    val gitDelegate = GitDelegate(settingsViewModel, viewModelScope, logHandler::onBuildLog, logHandler::onProgress)
    val aiDelegate = AIDelegate(settingsViewModel, viewModelScope, logHandler::onAiLog, gitDelegate::applyUnidiffPatch)
    val overlayDelegate = OverlayDelegate(application, settingsViewModel, viewModelScope, logHandler::onAiLog)
    val buildDelegate = BuildDelegate(application, settingsViewModel, viewModelScope, logHandler::onBuildLog, logHandler::onAiLog, { overlayDelegate.sourceMap = it }, { aiDelegate.startContextualAITask("Error: $it") }, { stateDelegate.setCurrentWebUrl(it) }, gitDelegate)
    val repoDelegate = RepoDelegate(application, settingsViewModel, viewModelScope, logHandler::onBuildLog, logHandler::onAiLog, logHandler::onProgress, logHandler::onGitProgress)
    val updateDelegate = UpdateDelegate(application, settingsViewModel, viewModelScope, logHandler::onAiLog)
    val systemEventDelegate = SystemEventDelegate(application, aiDelegate, overlayDelegate, stateDelegate)

    // --- State Exposure ---
    val loadingProgress = stateDelegate.loadingProgress
    val buildLog = stateDelegate.buildLog
    val ownedRepos = repoDelegate.ownedRepos
    val dependencies = MutableStateFlow<List<Dependency>>(emptyList()).asStateFlow()

    // --- Compatibility UI State Wrapper for older UI components ---
    data class MainUiState(
        val projects: List<ProjectItem> = emptyList(),
        val currentProjectPath: String? = null,
        val projectType: ProjectType = ProjectType.ANDROID,
        val targetPackageName: String? = null
    )

    // Combine flows to create a unified UI state
    val uiState = combine(settingsViewModel.currentAppName) { (appName) ->
        MainUiState(
            currentProjectPath = if (appName != null) "/projects/$appName" else null,
            targetPackageName = settingsViewModel.getAppName() // fallback
        )
    }.stateIn(viewModelScope, SharingStarted.Lazily, MainUiState())

    // --- Methods ---
    fun setScreenCapturePermission(code: Int, data: Intent) = overlayDelegate.setScreenCapturePermission(code, data)
    fun hasScreenCapturePermission() = overlayDelegate.hasScreenCapturePermission()
    fun requestScreenCapturePermission() = overlayDelegate.requestScreenCapturePermission()
    fun onPreviewLaunched() { _autoLaunchApk.value = null }

    // Stub methods for UI compatibility
    fun loadProject(path: String, callback: () -> Unit) { callback() }
    fun createProject(name: String, path: String, type: ProjectType, pkg: String) {}

    fun fetchGitHubRepos() = repoDelegate.fetchGitHubRepos()
    fun selectRepositoryForSetup(repo: com.hereliesaz.ideaz.models.GitHubRepoResponse, onSuccess: () -> Unit) {
        repoDelegate.selectRepositoryForSetup(repo) { _, _ -> onSuccess() }
    }

    fun createGitHubRepository(name: String, desc: String, private: Boolean, type: ProjectType, pkg: String, ctx: android.content.Context, callback: (String, String) -> Unit) {
        repoDelegate.createGitHubRepository(name, desc, private, type, pkg, ctx, callback)
    }

    fun forkRepository(owner: String, repo: String, callback: (String, String, String) -> Unit) {
        repoDelegate.forkRepository(owner, repo, callback)
    }
}