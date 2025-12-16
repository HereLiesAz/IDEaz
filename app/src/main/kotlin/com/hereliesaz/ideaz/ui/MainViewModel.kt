package com.hereliesaz.ideaz.ui

import android.app.Application
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.hereliesaz.ideaz.api.GitHubApiClient
import com.hereliesaz.ideaz.api.GitHubRepoResponse
import com.hereliesaz.ideaz.git.GitManager
import com.hereliesaz.ideaz.models.ProjectType
import com.hereliesaz.ideaz.services.CrashReportingService
import com.hereliesaz.ideaz.ui.delegates.*
import com.hereliesaz.ideaz.utils.ErrorCollector
import com.hereliesaz.ideaz.utils.ProjectAnalyzer
import com.hereliesaz.ideaz.utils.ToolManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL

/**
 * The central ViewModel for the application, orchestrating UI state, build processes,
 * Git operations, and AI interactions.
 *
 * This ViewModel delegates specific responsibilities to helper classes (Delegates)
 * to maintain separation of concerns and reduce code size.
 *
 * @param application The Android Application context.
 * @param settingsViewModel The ViewModel for accessing and modifying user settings.
 */
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
        override fun onOverlayLog(msg: String) {
             stateDelegate.appendAiLog(msg) // Fallback to AI log for now if overlay log isn't distinct
        }
    }

    val aiDelegate = AIDelegate(settingsViewModel, viewModelScope, logHandler::onAiLog) { diff -> applyUnidiffPatchInternal(diff) }
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

    /**
     * Called by UI when a screen transition occurs to flush non-fatal errors.
     */
    fun flushNonFatalErrors() {
        val errors = ErrorCollector.getAndClear()
        if (errors != null) {
            val apiKey = settingsViewModel.getApiKey()
            val githubUser = settingsViewModel.getGithubUser() ?: "Unknown"

            if (!apiKey.isNullOrBlank()) {
                val intent = Intent(getApplication(), CrashReportingService::class.java).apply {
                    action = CrashReportingService.ACTION_REPORT_NON_FATAL
                    putExtra(CrashReportingService.EXTRA_API_KEY, apiKey)
                    putExtra(CrashReportingService.EXTRA_STACK_TRACE, errors)
                    putExtra(CrashReportingService.EXTRA_GITHUB_USER, githubUser)
                }
                getApplication<Application>().startService(intent)
            }
        }
    }

    // --- PROXY METHODS ---

    // BUILD

    /** Binds the BuildService to the given context. */
    fun bindBuildService(c: Context) = buildDelegate.bindService(c)

    /** Unbinds the BuildService from the given context. */
    fun unbindBuildService(c: Context) = buildDelegate.unbindService(c)

    /** Starts a build for the specified project path (or current project if null). */
    fun startBuild(c: Context, p: File? = null) = buildDelegate.startBuild(p)

    /** Clears local build caches (TODO). */
    fun clearBuildCaches(c: Context) { /* TODO */ }

    /**
     * Downloads and installs the build tools (aapt2, d8, kotlinc) from the latest GitHub release.
     * This is required for local builds to function.
     */
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
                    logHandler.onBuildLog("Error: 'tools.zip' artifact not found in recent releases.")
                    stateDelegate.setLoadingProgress(null)
                    return@launch
                }

                logHandler.onBuildLog("Downloading build tools from ${toolAsset.name}...")
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

    /** Refreshes Git status, branches, and commit history. */
    fun refreshGitData() = gitDelegate.refreshGitData()

    /** Performs a 'git fetch' operation. */
    fun gitFetch() = gitDelegate.fetch()

    /** Performs a 'git pull' operation. */
    fun gitPull() = gitDelegate.pull()

    /** Performs a 'git push' operation. */
    fun gitPush() = gitDelegate.push()

    /** Stashes changes with an optional message. */
    fun gitStash(m: String?) = gitDelegate.stash(m)

    /** Pops the latest stash. */
    fun gitUnstash() = gitDelegate.unstash()

    /** Switches to the specified branch. */
    fun switchBranch(b: String) = gitDelegate.switchBranch(b)

    // AI

    /** Sends a prompt to the active AI session. */
    fun sendPrompt(p: String?) { if(!p.isNullOrBlank()) aiDelegate.startContextualAITask(p) }

    /** Submits a prompt along with context (screen capture, selection) from the overlay. */
    fun submitContextualPrompt(p: String) {
        val context = overlayDelegate.pendingContextInfo ?: "No context"
        val base64 = overlayDelegate.pendingBase64Screenshot
        val richPrompt = if (base64 != null) "$context\n\n$p\n\n[IMAGE: data:image/png;base64,$base64]" else "$context\n\n$p"
        aiDelegate.startContextualAITask(richPrompt)
    }

    /** Resumes a specific Jules session. */
    fun resumeSession(id: String) = aiDelegate.resumeSession(id)

    /** Fetches available Jules sessions for the given repository. */
    fun fetchSessionsForRepo(r: String) = aiDelegate.fetchSessionsForRepo(r)

    // OVERLAY

    /** Toggles the screen selection mode. */
    fun toggleSelectMode(b: Boolean) = overlayDelegate.toggleSelectMode(b)

    /** Clears the current screen selection. */
    fun clearSelection() = overlayDelegate.clearSelection()

    /** Closes the contextual chat and clears selection. */
    fun closeContextualChat() = overlayDelegate.clearSelection()

    /** Requests permission to capture the screen (MediaProjection). */
    fun requestScreenCapturePermission() = overlayDelegate.requestScreenCapturePermission()

    /** Signals that the screen capture request has been handled. */
    fun screenCaptureRequestHandled() = overlayDelegate.screenCaptureRequestHandled()

    /** Sets the result of the screen capture permission request. */
    fun setScreenCapturePermission(c: Int, d: Intent?) = overlayDelegate.setScreenCapturePermission(c, d)

    /** Checks if screen capture permission is granted. */
    fun hasScreenCapturePermission() = overlayDelegate.hasScreenCapturePermission()

    /** Sets a pending navigation route to be handled by the UI. */
    fun setPendingRoute(r: String?) = stateDelegate.setPendingRoute(r)

    // REPO

    /** Fetches the list of repositories owned by the user from GitHub. */
    fun fetchGitHubRepos() = repoDelegate.fetchGitHubRepos()

    /** Scans the local filesystem for imported projects. */
    fun scanLocalProjects() = repoDelegate.scanLocalProjects()

    /** Returns a list of local projects with their metadata. */
    fun getLocalProjectsWithMetadata() = repoDelegate.getLocalProjectsWithMetadata()

    /** Forces an update of the initialization files (workflows, setup scripts) in the project. */
    fun forceUpdateInitFiles() = repoDelegate.forceUpdateInitFiles()

    /** Uploads project secrets (API keys, Keystore) to GitHub Actions secrets. */
    fun uploadProjectSecrets(o: String, r: String) = repoDelegate.uploadProjectSecrets(o, r)

    /** Creates a new GitHub repository and initializes it with the project template. */
    fun createGitHubRepository(name: String, desc: String, priv: Boolean, type: ProjectType, pkg: String, ctx: Context, onSuccess: () -> Unit) {
        repoDelegate.createGitHubRepository(name, desc, priv, type, pkg, ctx) { owner, branch ->
            saveAndInitialize(name, owner, branch, pkg, type, ctx)
            onSuccess()
        }
    }

    /** Selects an existing repository for setup and loads its sessions. */
    fun selectRepositoryForSetup(repo: GitHubRepoResponse, onSuccess: () -> Unit) {
        repoDelegate.selectRepositoryForSetup(repo) { owner, branch ->
            repoDelegate.uploadProjectSecrets(owner, repo.name)
            aiDelegate.fetchSessionsForRepo(repo.fullName)
            repoDelegate.forceUpdateInitFiles()
            onSuccess()
        }
    }

    /** Saves project configuration and triggers the initial build/setup. */
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

    /** Loads a local project by name. */
    fun loadProject(name: String, onSuccess: () -> Unit) {
        viewModelScope.launch {
            settingsViewModel.setAppName(name)
            val user = settingsViewModel.getGithubUser()
            if (!user.isNullOrBlank()) repoDelegate.uploadProjectSecrets(user, name)
            onSuccess()
        }
    }

    /** Forks a repository (TODO). */
    fun forkRepository(u: String, onSuccess: () -> Unit = {}) { /* TODO */ }

    /**
     * Imports an external project folder via Storage Access Framework URI.
     * Copies the folder to the app's internal storage for read/write access.
     */
    fun registerExternalProject(u: Uri) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val documentFile = if (u.scheme == "file" && u.path != null) {
                    androidx.documentfile.provider.DocumentFile.fromFile(File(u.path!!))
                } else {
                    try {
                        val takeFlags: Int = Intent.FLAG_GRANT_READ_URI_PERMISSION or
                                Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                        getApplication<Application>().contentResolver.takePersistableUriPermission(u, takeFlags)
                    } catch (e: Exception) {
                        // Ignore if persistence is not supported
                    }
                    androidx.documentfile.provider.DocumentFile.fromTreeUri(getApplication(), u)
                }

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

    /**
     * Deletes a local project by name.
     */
    fun deleteProject(n: String) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                performLocalDeletion(n)
                logHandler.onBuildLog("Project '$n' deleted locally.\n")
            } catch (e: Exception) {
                logHandler.onBuildLog("Error deleting project: ${e.message}\n")
            }
        }
    }

    /**
     * Syncs changes to remote repository (if configured) before deleting the local project.
     */
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

    /** Checks for experimental updates via the UpdateDelegate. */
    fun checkForExperimentalUpdates() = updateDelegate.checkForExperimentalUpdates()

    /** Confirms and installs a pending update. */
    fun confirmUpdate() = updateDelegate.confirmUpdate()

    /** Dismisses the update warning. */
    fun dismissUpdateWarning() = updateDelegate.dismissUpdateWarning()

    // DEPENDENCIES

    private val _dependencies = MutableStateFlow<List<com.hereliesaz.ideaz.utils.DependencyItem>>(emptyList())
    val dependencies = _dependencies.asStateFlow()

    fun loadDependencies() {
        viewModelScope.launch(Dispatchers.IO) {
            val appName = settingsViewModel.getAppName()
            if (appName != null) {
                val projectDir = settingsViewModel.getProjectPath(appName)
                val deps = com.hereliesaz.ideaz.utils.DependencyManager.listDependencies(projectDir)
                _dependencies.value = deps
            }
        }
    }

    fun addDependencyViaAI(coordinate: String) {
        val prompt = "Add dependency '$coordinate' to the project. Update gradle/libs.versions.toml and app/build.gradle.kts (or build.gradle.kts) accordingly. Ensure to add version to [versions] and library to [libraries] with an alias, then implement it."
        aiDelegate.startContextualAITask(prompt)
    }

    // MISC

    /** Clears the build log. */
    fun clearLog() = stateDelegate.clearLog()

    /**
     * Launches the target application (APK or Web).
     */
    fun launchTargetApp(c: Context) {
        val appName = settingsViewModel.getAppName() ?: return
        val projectTypeStr = settingsViewModel.getProjectType()
        val projectType = ProjectType.fromString(projectTypeStr)

        if (projectType == ProjectType.WEB) {
            if (stateDelegate.currentWebUrl.value == null) {
                val projectDir = settingsViewModel.getProjectPath(appName)
                val indexFile = File(projectDir, "index.html")
                if (indexFile.exists()) {
                    stateDelegate.setCurrentWebUrl("file://${indexFile.absolutePath}")
                }
            }
            stateDelegate.setTargetAppVisible(true)
        } else {
            // Android, Flutter, React Native (assume APK)
            var packageName = settingsViewModel.targetPackageName.value
            if (packageName.isNullOrBlank()) {
                 packageName = "com.example.helloworld" // fallback
            }

            try {
                val intent = c.packageManager.getLaunchIntentForPackage(packageName)
                if (intent != null) {
                    c.startActivity(intent)
                } else {
                    // Try to detect package name again as fallback
                    val projectDir = settingsViewModel.getProjectPath(appName)
                    val detectedPackage = ProjectAnalyzer.detectPackageName(projectDir)

                    if (!detectedPackage.isNullOrBlank() && detectedPackage != packageName) {
                        settingsViewModel.saveTargetPackageName(detectedPackage)
                        val newIntent = c.packageManager.getLaunchIntentForPackage(detectedPackage)
                        if (newIntent != null) {
                            c.startActivity(newIntent)
                            return
                        }
                    }
                    Toast.makeText(c, "App not installed. Please build first.", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                Toast.makeText(c, "Failed to launch app: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    /** Downloads project dependencies (TODO). */
    fun downloadDependencies() { /* TODO */ }

    /** Checks for required API keys and returns a list of missing ones. */
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

    private suspend fun applyUnidiffPatchInternal(diff: String): Boolean {
        return gitDelegate.applyUnidiffPatch(diff)
    }
}

/**
 * Interface for handling log events from delegates.
 */
interface LogHandler {
    fun onBuildLog(msg: String)
    fun onAiLog(msg: String)
    fun onProgress(p: Int?)
    fun onGitProgress(p: Int, t: String)
    fun onOverlayLog(msg: String)
}
