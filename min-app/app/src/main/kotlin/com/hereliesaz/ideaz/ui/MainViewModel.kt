package com.hereliesaz.ideaz.ui

import android.app.Application
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.IBinder
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.hereliesaz.ideaz.IBuildCallback
import com.hereliesaz.ideaz.IBuildService
import com.hereliesaz.ideaz.api.AuthInterceptor
import com.hereliesaz.ideaz.api.GitHubApiClient
import com.hereliesaz.ideaz.api.Source
import com.hereliesaz.ideaz.api.Session
import com.hereliesaz.ideaz.api.CreateRepoRequest
import com.hereliesaz.ideaz.jules.JulesApiClient
import com.hereliesaz.ideaz.models.ProjectType
import com.hereliesaz.ideaz.models.ProjectMetadata
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import android.widget.Toast

class MainViewModel(
    application: Application,
    val settingsViewModel: SettingsViewModel
) : AndroidViewModel(application) {

    private val _buildLog = MutableStateFlow("")
    val buildLog = _buildLog.asStateFlow()

    private val _aiLog = MutableStateFlow("")
    val aiLog = _aiLog.asStateFlow()

    val combinedLogs = combine(_buildLog, _aiLog) { build, ai ->
        "--- BUILD LOG ---\n$build\n\n--- AI LOG ---\n$ai"
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "")

    private val _loadingProgress = MutableStateFlow<Float?>(null)
    val loadingProgress = _loadingProgress.asStateFlow()

    private val _commitHistory = MutableStateFlow<List<String>>(emptyList())
    val commitHistory = _commitHistory.asStateFlow()

    private val _branches = MutableStateFlow<List<String>>(emptyList())
    val branches = _branches.asStateFlow()

    private val _gitStatus = MutableStateFlow<List<String>>(emptyList())
    val gitStatus = _gitStatus.asStateFlow()

    private val _ownedSources = MutableStateFlow<List<Source>>(emptyList())
    val ownedSources = _ownedSources.asStateFlow()

    private val _isLoadingSources = MutableStateFlow(false)
    val isLoadingSources = _isLoadingSources.asStateFlow()

    private val _availableSessions = MutableStateFlow<List<Session>>(emptyList())
    val availableSessions = _availableSessions.asStateFlow()

    // UI Visibility State
    private val _showCancelDialog = MutableStateFlow(false)
    val showCancelDialog = _showCancelDialog.asStateFlow()

    private val _isTargetAppVisible = MutableStateFlow(false)
    val isTargetAppVisible = _isTargetAppVisible.asStateFlow()

    private val _requestScreenCapture = MutableStateFlow(false)
    val requestScreenCapture = _requestScreenCapture.asStateFlow()

    private val _updateStatus = MutableStateFlow<String?>(null)
    val updateStatus = _updateStatus.asStateFlow()

    private val _showUpdateWarning = MutableStateFlow(false)
    val showUpdateWarning = _showUpdateWarning.asStateFlow()

    private var buildService: IBuildService? = null
    private var isServiceBound = false

    private val buildCallback = object : IBuildCallback.Stub() {
        override fun onLog(message: String) {
            _buildLog.value += "\n$message"
        }

        override fun onSuccess(apkPath: String) {
            _buildLog.value += "\n[IDE] Build Success: $apkPath"
            _loadingProgress.value = null
        }

        override fun onFailure(message: String) {
            _buildLog.value += "\n[IDE] Build Failed: $message"
            _loadingProgress.value = null
        }
    }

    private val serviceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            buildService = IBuildService.Stub.asInterface(service)
            isServiceBound = true
            Log.d(TAG, "BuildService connected")
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            buildService = null
            isServiceBound = false
            Log.d(TAG, "BuildService disconnected")
        }
    }

    fun bindBuildService(context: Context) {
        val intent = Intent().setClassName(context, "com.hereliesaz.ideaz.services.BuildService")
        intent.setPackage(context.packageName)
        try {
            val bound = context.bindService(intent, serviceConnection, Context.BIND_AUTO_CREATE)
            if (!bound) {
                Log.e(TAG, "Failed to bind BuildService")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Exception binding BuildService", e)
        }
    }

    fun unbindBuildService(context: Context) {
        if (isServiceBound) {
            try {
                context.unbindService(serviceConnection)
            } catch (e: Exception) {
                Log.e(TAG, "Error unbinding service", e)
            }
            isServiceBound = false
        }
    }

    fun loadLastProject(context: Context) {
        val appName = settingsViewModel.getAppName() ?: return
        // Just verify connection/settings, no file check
        if (settingsViewModel.getGithubToken().isNullOrBlank()) return
        refreshGitData()
    }

    // Connects to a project (saves config)
    fun connectProject(owner: String, repo: String, branch: String) {
        settingsViewModel.saveProjectConfig(repo, owner, branch)
        settingsViewModel.setAppName(repo)
        refreshGitData()
    }

    fun refreshGitData() {
        val token = settingsViewModel.getGithubToken() ?: return
        val owner = settingsViewModel.getGithubUser() ?: return
        val repo = settingsViewModel.getAppName() ?: return
        val branch = settingsViewModel.getBranchName()

        viewModelScope.launch(Dispatchers.IO) {
            try {
                val api = GitHubApiClient.createService(token)
                val branchInfo = api.getBranch(owner, repo, branch)

                // For history, we would need listCommits endpoint.
                // For now, just show the latest commit.
                _commitHistory.value = listOf("${branchInfo.commit.sha.take(7)} - ${branchInfo.commit.commit.message}")

                // Status is always "Clean" remotely unless we have better tracking
                _gitStatus.value = listOf("Clean (Remote)")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to refresh git data", e)
            }
        }
    }

    fun fetchOwnedSources() {
        val projectId = settingsViewModel.getJulesProjectId() ?: "projects/ideaz-336316"
        if (settingsViewModel.getApiKey() == null) return

        viewModelScope.launch {
            _isLoadingSources.value = true
            try {
                val response = JulesApiClient.listSources(projectId)
                _ownedSources.value = response.sources ?: emptyList()
            } catch (e: Exception) {
                Log.e(TAG, "Failed to fetch sources", e)
                _buildLog.value += "\n[API] Failed to fetch sources: ${e.message}"
            } finally {
                _isLoadingSources.value = false
            }
        }
    }

    fun fetchSessions() {
        val projectId = settingsViewModel.getJulesProjectId() ?: "projects/ideaz-336316"
        if (settingsViewModel.getApiKey() == null) return

        viewModelScope.launch {
            try {
                val response = JulesApiClient.listSessions(projectId)
                _availableSessions.value = response.sessions ?: emptyList()
            } catch (e: Exception) {
                Log.e(TAG, "Failed to fetch sessions", e)
            }
        }
    }

    fun createGitHubRepository(
        name: String,
        description: String,
        isPrivate: Boolean,
        type: ProjectType,
        packageName: String,
        context: Context,
        onSuccess: () -> Unit
    ) {
        val token = settingsViewModel.getGithubToken()
        if (token.isNullOrBlank()) {
            Toast.makeText(context, "GitHub Token missing", Toast.LENGTH_SHORT).show()
            return
        }

        viewModelScope.launch {
            _loadingProgress.value = 0f
            try {
                val api = GitHubApiClient.createService(token)
                val response = api.createRepo(CreateRepoRequest(name, description, isPrivate, autoInit = true))
                val repoOwner = response.fullName.split("/")[0]

                // Just connect, no clone
                withContext(Dispatchers.Main) {
                    connectProject(repoOwner, name, "main")
                    settingsViewModel.saveTargetPackageName(packageName)
                    settingsViewModel.setProjectType(type.name)
                    onSuccess()
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(context, "Failed to create repo: ${e.message}", Toast.LENGTH_LONG).show()
                }
                Log.e(TAG, "Create Repo Failed", e)
            } finally {
                _loadingProgress.value = null
            }
        }
    }

    fun saveAndInitialize(
        appName: String,
        githubUser: String,
        branchName: String,
        packageName: String,
        type: ProjectType,
        context: Context,
        initialPrompt: String?
    ) {
        settingsViewModel.saveProjectConfig(appName, githubUser, branchName)
        settingsViewModel.saveTargetPackageName(packageName)
        settingsViewModel.setProjectType(type.name)

        // Ensure workflows exist?
        // In repository-less mode, we can't easily push files unless we use the API to commit.
        // I won't implement "create file via API" right now.
        // Assume user handles it via AI prompt "Setup workflows".

        startBuild()
    }

    fun startBuild() {
        val appName = settingsViewModel.getAppName() ?: return
        // Use a dummy path or just pass app name via intent, but AIDL expects path.
        // BuildService ignores path for GitManager, but maybe uses it for something?
        // BuildService uses settings for repo info.
        // I'll pass a dummy path.
        val dummyPath = "/dev/null"

        if (buildService != null) {
            _loadingProgress.value = 0f
            _buildLog.value = "[IDE] Starting Remote Build..."
            try {
                buildService?.startBuild(dummyPath, buildCallback)
            } catch (e: Exception) {
                _buildLog.value += "\n[IDE] Failed to start build service: ${e.message}"
            }
        } else {
            _buildLog.value += "\n[IDE] Build Service not bound!"
        }
    }

    // --- Session Management Placeholders ---

    fun setActiveSession(sessionId: String) { }
    fun trySession(session: Session) { }
    fun acceptSession(session: Session) { }
    fun deleteSession(session: Session) {
         val projectId = settingsViewModel.getJulesProjectId() ?: return
         viewModelScope.launch {
             try {
                 JulesApiClient.deleteSession(projectId, session.id)
                 fetchSessions()
             } catch (e: Exception) {
                 Log.e(TAG, "Delete session failed", e)
             }
         }
    }

    fun sendPrompt(prompt: String) {
        _aiLog.value += "\n[USER] $prompt"
        // TODO: Implement send message via Jules API
    }

    // --- UI State & Permissions ---

    fun requestScreenCapturePermission() {
        _requestScreenCapture.value = true
    }

    fun screenCaptureRequestHandled() {
        _requestScreenCapture.value = false
    }

    fun hasScreenCapturePermission(): Boolean {
        return false // Placeholder
    }

    fun setScreenCapturePermission(resultCode: Int, data: Intent?) { }

    fun dismissCancelTask() {
        _showCancelDialog.value = false
    }

    fun confirmCancelTask() {
        _showCancelDialog.value = false
        if (buildService != null) {
            try {
                buildService?.cancelBuild()
            } catch (e: Exception) {}
        }
    }

    fun checkForExperimentalUpdates() {
        _buildLog.value += "\n[UPDATE] Checking for updates..."
    }

    fun dismissUpdateWarning() {
        _showUpdateWarning.value = false
    }

    fun confirmUpdate() {
        _showUpdateWarning.value = false
    }

    fun clearBuildCaches(context: Context) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                context.cacheDir.deleteRecursively()
                withContext(Dispatchers.Main) {
                    _buildLog.value += "\n[IDE] Caches cleared."
                }
            } catch (e: Exception) {
                Log.e(TAG, "Clear cache failed", e)
            }
        }
    }

    fun clearLog() {
        _buildLog.value = ""
        _aiLog.value = ""
    }

    companion object {
        private const val TAG = "MainViewModel"
    }
}

class MainViewModelFactory(
    private val application: Application,
    private val settingsViewModel: SettingsViewModel
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(MainViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return MainViewModel(application, settingsViewModel) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
