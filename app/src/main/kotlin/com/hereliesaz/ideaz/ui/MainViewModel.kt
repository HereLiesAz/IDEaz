package com.hereliesaz.ideaz.ui

import android.app.Application
import android.content.Context
import android.content.Intent
import android.graphics.Rect
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.hereliesaz.ideaz.api.GitHubRepoResponse
import com.hereliesaz.ideaz.jules.Patch
import com.hereliesaz.ideaz.models.ProjectType
import com.hereliesaz.ideaz.ui.delegates.*
import com.hereliesaz.ideaz.utils.ToolManager
import com.hereliesaz.ideaz.api.GitHubApiClient
import com.hereliesaz.ideaz.git.GitManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL

class MainViewModel(
    application: Application,
    val settingsViewModel: SettingsViewModel
) : AndroidViewModel(application) {

    // --- DELEGATES ---
    val stateDelegate = StateDelegate()

    // Helper to pipe logs to UI and State
    private val logHandler = object : LogHandler {
        override fun onBuildLog(msg: String) { stateDelegate.appendBuildLog(msg) }
        override fun onAiLog(msg: String) {
            stateDelegate.appendAiLog(msg)
            // Broadcast for overlay logs
            application.sendBroadcast(Intent("com.hereliesaz.ideaz.AI_LOG").apply { putExtra("MESSAGE", msg) })
        }
        override fun onProgress(p: Int?) { stateDelegate.setLoadingProgress(p) }
        override fun onGitProgress(p: Int, t: String) {
            stateDelegate.setLoadingProgress(if (p >= 100) null else p)
            stateDelegate.appendBuildLog("[GIT] $t\n")
        }
    }

    val aiDelegate = AIDelegate(settingsViewModel, viewModelScope, logHandler::onAiLog) { patch -> applyPatchInternal(patch) }
    val overlayDelegate = OverlayDelegate(application, settingsViewModel, viewModelScope, logHandler::onAiLog)

    val gitDelegate = GitDelegate(settingsViewModel, viewModelScope, logHandler::onBuildLog, logHandler::onProgress)

    val buildDelegate = BuildDelegate(
        application,
        settingsViewModel,
        viewModelScope,
        logHandler::onBuildLog,
        logHandler::onAiLog,
        { map -> overlayDelegate.sourceMap = map },
        { log -> aiDelegate.startContextualAITask("Web Build Failed. Fix this:\n$log") },
        { path ->
            stateDelegate.setCurrentWebUrl("file://$path")
            stateDelegate.setTargetAppVisible(true) // Switch to "App View"
        },
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

    val updateDelegate = UpdateDelegate(application, settingsViewModel, viewModelScope, logHandler::onAiLog)

    // Handles BroadcastReceivers
    val systemEventDelegate = SystemEventDelegate(application, aiDelegate, overlayDelegate, stateDelegate)

    // --- PUBLIC STATE EXPOSURE (Delegated) ---
    val loadingProgress = stateDelegate.loadingProgress
    val isTargetAppVisible = stateDelegate.isTargetAppVisible
    val currentWebUrl = stateDelegate.currentWebUrl
    val buildLog = stateDelegate.buildLog
    val filteredLog = stateDelegate.filteredLog
    val pendingRoute = stateDelegate.pendingRoute

    // Delegate States
    val isSelectMode = overlayDelegate.isSelectMode
    val activeSelectionRect = overlayDelegate.activeSelectionRect
    val isContextualChatVisible = overlayDelegate.isContextualChatVisible
    val requestScreenCapture = overlayDelegate.requestScreenCapture
    val ownedRepos = repoDelegate.ownedRepos
    val sessions = aiDelegate.sessions
    val commitHistory = gitDelegate.commitHistory
    val branches = gitDelegate.branches
    val gitStatus = gitDelegate.gitStatus
    val updateStatus = updateDelegate.updateStatus
    val updateVersion = updateDelegate.updateVersion
    val showUpdateWarning = updateDelegate.showUpdateWarning
    val updateMessage = updateDelegate.updateMessage
    val julesResponse = aiDelegate.julesResponse
    val julesHistory = aiDelegate.julesHistory
    val isLoadingJulesResponse = aiDelegate.isLoadingJulesResponse
    val julesError = aiDelegate.julesError
    val currentJulesSessionId = aiDelegate.currentJulesSessionId
    val showCancelDialog = MutableStateFlow(false).asStateFlow()

    // --- LIFECYCLE ---
    override fun onCleared() {
        super.onCleared()
        buildDelegate.unbindService(getApplication())
        systemEventDelegate.cleanup()
    }

    // --- PROXY METHODS ---

    // BUILD
    fun bindBuildService(c: Context) = buildDelegate.bindService(c)
    fun unbindBuildService(c: Context) = buildDelegate.unbindService(c)
    fun startBuild(c: Context, p: File? = null) = buildDelegate.startBuild(p)
    fun clearBuildCaches(c: Context) { /* TODO */ }
    fun downloadBuildTools() {
        viewModelScope.launch {
            val token = settingsViewModel.getGithubToken()
            if (token.isNullOrBlank()) {
                logHandler.onBuildLog("Error: GitHub Token required to download tools.")
                return@launch
            }

            stateDelegate.setLoadingProgress(0)
            logHandler.onBuildLog("Checking for build tools...")

            var zipFile: File? = null
            try {
                // Fetch releases on IO thread
                val releases = withContext(Dispatchers.IO) {
                    val service = GitHubApiClient.createService(token)
                    service.getReleases("HereLiesAz", "IDEaz")
                }

                // Look for 'tools.zip' in assets
                val toolAsset = releases.asSequence()
                    .flatMap { it.assets }
                    .firstOrNull { it.name == "tools.zip" }

                if (toolAsset == null) {
                    logHandler.onBuildLog("Error: 'tools.zip' not found in recent releases.")
                    stateDelegate.setLoadingProgress(null)
                    return@launch
                }

                logHandler.onBuildLog("Downloading tools from ${toolAsset.name}...")
                zipFile = File(getApplication<Application>().cacheDir, "tools.zip")

                val success = downloadFile(toolAsset.browserDownloadUrl, zipFile) { progress ->
                    stateDelegate.setLoadingProgress(progress)
                }

                if (success) {
                    logHandler.onBuildLog("Installing tools...")
                    val installed = withContext(Dispatchers.IO) {
                        ToolManager.installToolsFromZip(getApplication(), zipFile)
                    }
                    if (installed) {
                        logHandler.onBuildLog("Build tools installed successfully.")
                        settingsViewModel.setLocalBuildEnabled(true)
                    } else {
                        logHandler.onBuildLog("Error: Failed to install tools.")
                        settingsViewModel.setLocalBuildEnabled(false)
                    }
                } else {
                    logHandler.onBuildLog("Error: Download failed.")
                    settingsViewModel.setLocalBuildEnabled(false)
                }
            } catch (e: Exception) {
                logHandler.onBuildLog("Error downloading tools: ${e.message}")
                e.printStackTrace()
                settingsViewModel.setLocalBuildEnabled(false)
            } finally {
                zipFile?.delete()
                stateDelegate.setLoadingProgress(null)
            }
        }
    }

    // GIT
    fun refreshGitData() = gitDelegate.refreshGitData()
    fun gitFetch() = gitDelegate.fetch()
    fun gitPull() = gitDelegate.pull()
    fun gitPush() = gitDelegate.push()
    fun gitStash(m: String?) = gitDelegate.stash(m)
    fun gitUnstash() = gitDelegate.unstash()
    fun switchBranch(b: String) = gitDelegate.switchBranch(b)

    // AI
    fun sendPrompt(p: String?) { if(!p.isNullOrBlank()) aiDelegate.startContextualAITask(p) }
    fun submitContextualPrompt(p: String) {
        val context = overlayDelegate.pendingContextInfo ?: "No context"
        val base64 = overlayDelegate.pendingBase64Screenshot
        val richPrompt = if (base64 != null) "$context\n\n$p\n\n[IMAGE: data:image/png;base64,$base64]" else "$context\n\n$p"
        aiDelegate.startContextualAITask(richPrompt)
    }
    fun resumeSession(id: String) = aiDelegate.resumeSession(id)
    fun fetchSessionsForRepo(r: String) = aiDelegate.fetchSessionsForRepo(r)

    // OVERLAY
    fun toggleSelectMode(b: Boolean) = overlayDelegate.toggleSelectMode(b)
    fun clearSelection() = overlayDelegate.clearSelection()
    fun closeContextualChat() = overlayDelegate.clearSelection()
    fun requestScreenCapturePermission() = overlayDelegate.requestScreenCapturePermission()
    fun screenCaptureRequestHandled() = overlayDelegate.screenCaptureRequestHandled()
    fun setScreenCapturePermission(c: Int, d: Intent?) = overlayDelegate.setScreenCapturePermission(c, d)
    fun hasScreenCapturePermission() = overlayDelegate.hasScreenCapturePermission()
    fun setPendingRoute(r: String?) = stateDelegate.setPendingRoute(r)

    // REPO
    fun fetchGitHubRepos() = repoDelegate.fetchGitHubRepos()
    fun scanLocalProjects() = repoDelegate.scanLocalProjects()
    fun getLocalProjectsWithMetadata() = repoDelegate.getLocalProjectsWithMetadata()
    fun forceUpdateInitFiles() = repoDelegate.forceUpdateInitFiles()
    fun uploadProjectSecrets(o: String, r: String) = repoDelegate.uploadProjectSecrets(o, r)
    fun createGitHubRepository(name: String, desc: String, priv: Boolean, type: ProjectType, pkg: String, ctx: Context, onSuccess: () -> Unit) {
        repoDelegate.createGitHubRepository(name, desc, priv, type, pkg, ctx) { owner, branch ->
            saveAndInitialize(name, owner, branch, pkg, type, ctx)
            onSuccess()
        }
    }
    fun selectRepositoryForSetup(repo: GitHubRepoResponse, onSuccess: () -> Unit) {
        repoDelegate.selectRepositoryForSetup(repo) { owner, branch ->
            repoDelegate.uploadProjectSecrets(owner, repo.name)
            aiDelegate.fetchSessionsForRepo(repo.fullName)
            repoDelegate.forceUpdateInitFiles()
            onSuccess()
        }
    }
    fun saveAndInitialize(appName: String, user: String, branch: String, pkg: String, type: ProjectType, context: Context, initialPrompt: String? = null) {
        viewModelScope.launch {
            settingsViewModel.saveProjectConfig(appName, user, branch)
            settingsViewModel.saveTargetPackageName(pkg)
            settingsViewModel.setProjectType(type.name)
            repoDelegate.uploadProjectSecrets(user, appName)
            repoDelegate.forceUpdateInitFiles()
            buildDelegate.startBuild(context.filesDir.resolve(appName))
        }
    }
    fun loadProject(name: String, onSuccess: () -> Unit) {
        viewModelScope.launch {
            settingsViewModel.setAppName(name)
            val user = settingsViewModel.getGithubUser()
            if (!user.isNullOrBlank()) repoDelegate.uploadProjectSecrets(user, name)
            onSuccess()
        }
    }
    fun forkRepository(u: String, onSuccess: () -> Unit = {}) { /* TODO */ }

    fun registerExternalProject(u: Uri) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                try {
                    val takeFlags: Int = Intent.FLAG_GRANT_READ_URI_PERMISSION or
                            Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                    getApplication<Application>().contentResolver.takePersistableUriPermission(u, takeFlags)
                } catch (e: Exception) {
                    // Ignore if persistence is not supported
                }

                val documentFile = androidx.documentfile.provider.DocumentFile.fromTreeUri(getApplication(), u)
                if (documentFile == null || !documentFile.isDirectory) {
                    logHandler.onOverlayLog("Invalid project directory selected.")
                    return@launch
                }

                var projectName = documentFile.name ?: "Imported_${System.currentTimeMillis()}"
                var destDir = getApplication<Application>().filesDir.resolve(projectName)

                var counter = 1
                while (destDir.exists()) {
                    projectName = "${documentFile.name ?: "Imported"}_$counter"
                    destDir = getApplication<Application>().filesDir.resolve(projectName)
                    counter++
                }

                logHandler.onOverlayLog("Importing project '$projectName'...")
                logHandler.onProgress(0)

                copyDocumentFileToLocal(documentFile, destDir)

                logHandler.onOverlayLog("Import complete.")
                logHandler.onProgress(null)

                // Analyze and Load
                val projectType = com.hereliesaz.ideaz.utils.ProjectAnalyzer.detectProjectType(destDir)
                val packageName = com.hereliesaz.ideaz.utils.ProjectAnalyzer.detectPackageName(destDir)
                    ?: "com.ideaz.imported.${projectName.filter { it.isLetterOrDigit() }.lowercase()}"

                val owner = settingsViewModel.getGithubUser() ?: "local"
                val branch = "main"

                withContext(Dispatchers.Main) {
                    saveAndInitialize(projectName, owner, branch, packageName, projectType, getApplication())
                }

            } catch (e: Exception) {
                logHandler.onOverlayLog("Failed to import project: ${e.message}")
                e.printStackTrace()
            } finally {
                logHandler.onProgress(null)
            }
        }
    }

    private fun copyDocumentFileToLocal(src: androidx.documentfile.provider.DocumentFile, dest: File) {
        if (src.isDirectory) {
            if (!dest.exists()) dest.mkdirs()
            src.listFiles().forEach { file ->
                val destFile = File(dest, file.name ?: "unknown")
                copyDocumentFileToLocal(file, destFile)
            }
        } else {
            if (src.name != null) {
                getApplication<Application>().contentResolver.openInputStream(src.uri)?.use { input ->
                    dest.outputStream().use { output ->
                        input.copyTo(output)
                    }
                }
            }
        }
    }

    fun deleteProject(n: String) { /* TODO */ }
    fun registerExternalProject(u: Uri) { /* TODO */ }
    fun deleteProject(n: String) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val dir = settingsViewModel.getProjectPath(n)
                if (dir.exists()) {
                    if (dir.deleteRecursively()) {
                        withContext(Dispatchers.Main) {
                            settingsViewModel.removeProject(n)
                            settingsViewModel.removeProjectPath(n)
                            if (settingsViewModel.getAppName() == n) {
                                settingsViewModel.setAppName("")
                            }
                            repoDelegate.scanLocalProjects()
                            stateDelegate.appendAiLog("Project '$n' deleted successfully.")
                        }
                    } else {
                        withContext(Dispatchers.Main) {
                            stateDelegate.appendAiLog("Failed to delete project '$n'. Check permissions.")
                        }
                    }
                } else {
                    withContext(Dispatchers.Main) {
                        settingsViewModel.removeProject(n)
                        settingsViewModel.removeProjectPath(n)
                        repoDelegate.scanLocalProjects()
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    stateDelegate.appendAiLog("Error deleting project '$n': ${e.message}")
                }
            }
        }
    }
    fun syncAndDeleteProject(n: String) { /* TODO */ }
                performLocalDeletion(n)
                logHandler.onBuildLog("Project '$n' deleted locally.\n")
            } catch (e: Exception) {
                logHandler.onBuildLog("Error deleting project: ${e.message}\n")
            }
        }
    }

    fun syncAndDeleteProject(n: String) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val projectDir = settingsViewModel.getProjectPath(n)
                if (projectDir.exists()) {
                    logHandler.onBuildLog("Syncing project '$n' before deletion...\n")
                    val git = GitManager(projectDir)

                    if (git.hasChanges()) {
                        git.addAll()
                        git.commit("Sync before delete")
                    }

                    val token = settingsViewModel.getGithubToken()
                    val user = settingsViewModel.getGithubUser() ?: "git"

                    if (!token.isNullOrBlank()) {
                        git.push(user, token) { p, t -> logHandler.onGitProgress(p, t) }
                        logHandler.onBuildLog("Project synced successfully.\n")
                    } else {
                        logHandler.onBuildLog("Warning: No GitHub token found. Skipping push.\n")
                    }
                }
                performLocalDeletion(n)
                logHandler.onBuildLog("Project '$n' deleted.\n")
            } catch (e: Exception) {
                logHandler.onBuildLog("Error syncing/deleting project: ${e.message}\n")
            }
        }
    }

    private suspend fun performLocalDeletion(n: String) {
        val projectDir = settingsViewModel.getProjectPath(n)
        if (projectDir.exists()) {
            projectDir.deleteRecursively()
        }
        withContext(Dispatchers.Main) {
            settingsViewModel.removeProject(n)
            settingsViewModel.removeProjectPath(n)
            if (settingsViewModel.getAppName() == n) {
                settingsViewModel.setAppName("")
            }
            scanLocalProjects()
        }
    }

    // UPDATE
    fun checkForExperimentalUpdates() = updateDelegate.checkForExperimentalUpdates()
    fun confirmUpdate() = updateDelegate.confirmUpdate()
    fun dismissUpdateWarning() = updateDelegate.dismissUpdateWarning()

    // MISC
    fun clearLog() = stateDelegate.clearLog()
    fun launchTargetApp(c: Context) { /* TODO */ }
    fun downloadDependencies() = buildDelegate.downloadDependencies()

    fun checkRequiredKeys(): List<String> {
        val missing = mutableListOf<String>()
        if (settingsViewModel.getApiKey().isNullOrBlank()) missing.add("Jules API Key")
        if (settingsViewModel.getGithubToken().isNullOrBlank()) missing.add("GitHub Token")
        return missing
    }

    private suspend fun downloadFile(urlStr: String, destination: File, onProgress: (Int) -> Unit): Boolean {
        return withContext(Dispatchers.IO) {
            try {
                val url = URL(urlStr)
                val connection = url.openConnection() as HttpURLConnection
                connection.connect()

                if (connection.responseCode != HttpURLConnection.HTTP_OK) {
                    return@withContext false
                }

                val fileLength = connection.contentLength
                val input = connection.inputStream
                val output = FileOutputStream(destination)

                val data = ByteArray(4096)
                var total: Long = 0
                var count: Int
                while (input.read(data).also { count = it } != -1) {
                    total += count
                    if (fileLength > 0) {
                        onProgress((total * 100 / fileLength).toInt())
                    }
                    output.write(data, 0, count)
                }
                output.close()
                input.close()
                true
            } catch (e: Exception) {
                e.printStackTrace()
                false
            }
        }
    }

    private suspend fun applyPatchInternal(patch: Patch): Boolean {
        return withContext(Dispatchers.IO) {
            try {
                val appName = settingsViewModel.getAppName() ?: return@withContext false
                val projectDir = settingsViewModel.getProjectPath(appName)
                patch.actions.forEach { action ->
                    val file = File(projectDir, action.filePath)
                    when (action.type) {
                        "CREATE_FILE" -> {
                            file.parentFile?.mkdirs()
                            file.writeText(action.content)
                        }
                        "UPDATE_FILE" -> {
                            if (file.exists()) file.writeText(action.content)
                        }
                        "DELETE_FILE" -> {
                            if (file.exists()) file.delete()
                        }
                    }
                }
                gitDelegate.refreshGitData()
                true
            } catch (e: Exception) {
                false
            }
        }
    }
}

interface LogHandler {
    fun onBuildLog(msg: String)
    fun onAiLog(msg: String)
    fun onProgress(p: Int?)
    fun onGitProgress(p: Int, t: String)
}