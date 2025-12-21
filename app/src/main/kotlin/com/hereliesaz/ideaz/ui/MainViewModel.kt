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
import com.hereliesaz.ideaz.R
import com.hereliesaz.ideaz.utils.ProjectAnalyzer
import com.hereliesaz.ideaz.utils.ProjectFileObserver
import com.hereliesaz.ideaz.utils.ToolManager
import com.hereliesaz.ideaz.utils.VersionUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.asCoroutineDispatcher
import java.util.concurrent.Executors
import app.cash.zipline.loader.ZiplineLoader
import app.cash.zipline.loader.ManifestVerifier
import app.cash.zipline.loader.asZiplineHttpClient
import com.hereliesaz.ideaz.MainApplication
import com.hereliesaz.ideaz.BuildConfig
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
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
/**
 * The central ViewModel for the IDEaz application.
 *
 * This class orchestrates the interaction between the UI (`MainScreen`), the background build system (`BuildService`),
 * the AI agent (`AIDelegate`), and the Git version control (`GitDelegate`).
 *
 * It uses a Delegate pattern to separate concerns:
 * - [AIDelegate]: Manages AI sessions and Jules API calls.
 * - [BuildDelegate]: Manages the connection to the remote BuildService and build execution.
 * - [GitDelegate]: Manages local Git operations (clone, commit, push).
 * - [RepoDelegate]: Manages remote repository operations (GitHub API).
 * - [OverlayDelegate]: Manages the visual overlay and selection mode.
 * - [StateDelegate]: Centralizes shared UI state (logs, progress).
 * - [SystemEventDelegate]: Handles system broadcasts (screen on/off, package changes).
 * - [UpdateDelegate]: Handles self-updates.
 */
class MainViewModel(
    application: Application,
    val settingsViewModel: SettingsViewModel
) : AndroidViewModel(application) {

    // --- DELEGATES ---
    val stateDelegate = StateDelegate()

    private val ziplineDispatcher = Executors.newSingleThreadExecutor().asCoroutineDispatcher()

    val ziplineLoader: ZiplineLoader by lazy {
        val app = application as MainApplication
        ZiplineLoader(
            dispatcher = ziplineDispatcher,
            // TODO(Phase 11.5): Implement Ed25519 signature verification.
            // Currently utilizing NO_SIGNATURE_CHECKS to enable development of the Hybrid Host features.
            manifestVerifier = ManifestVerifier.NO_SIGNATURE_CHECKS,
            httpClient = app.okHttpClient.asZiplineHttpClient(),
        )
    }

    init {
        viewModelScope.launch {
            com.hereliesaz.ideaz.utils.LogcatReader.observe().collect {
                stateDelegate.appendSystemLog(it)
            }
        }
    }

    // Helper to pipe logs to UI and State
    private val logHandler = object : LogHandler {
        override fun onBuildLog(msg: String) { stateDelegate.appendBuildLog(msg) }
        override fun onAiLog(msg: String) {
            stateDelegate.appendAiLog(msg)
            // Broadcast for overlay logs
            application.sendBroadcast(Intent("com.hereliesaz.ideaz.AI_LOG").apply {
                putExtra("MESSAGE", msg)
                setPackage(application.packageName)
            })
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
        { log -> handleBuildFailure(log) },
        { path ->
            stateDelegate.setCurrentWebUrl("file://$path")
            stateDelegate.setTargetAppVisible(true) // Switch to "App View"
        },
        { launchTargetApp(application) },
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

    private var currentZipline: app.cash.zipline.Zipline? = null

    private fun reloadZipline(manifestPath: String) {
        viewModelScope.launch(ziplineDispatcher) {
            try {
                logHandler.onBuildLog("[Zipline] Reloading from $manifestPath...")
                currentZipline?.close()
                currentZipline = null

                // Load from file URL
                val manifestUrl = File(manifestPath).toUri().toString()
                val zipline = ziplineLoader.loadOnce("guest", manifestUrl) {
                     // TODO: Configure bindings/services exposed to Guest
                }
                currentZipline = zipline

                logHandler.onBuildLog("[Zipline] Reload complete.")

            } catch (e: Exception) {
                logHandler.onBuildLog("[Zipline] Reload failed: ${e.message}")
                e.printStackTrace()
            }
        }
    }

    // Handles BroadcastReceivers
    val systemEventDelegate = SystemEventDelegate(
        application,
        aiDelegate,
        overlayDelegate,
        stateDelegate
    ) { manifestPath ->
        reloadZipline(manifestPath)
    }

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

    // --- ARTIFACT CHECK STATE ---
    data class ArtifactCheckResult(
        val remoteVersion: String,
        val downloadUrl: String,
        val localVersion: String?,
        val isRemoteNewer: Boolean
    )
    private val _artifactCheckResult = MutableStateFlow<ArtifactCheckResult?>(null)
    val artifactCheckResult = _artifactCheckResult.asStateFlow()

    fun dismissArtifactDialog() { _artifactCheckResult.value = null }

    private var fileObserver: ProjectFileObserver? = null

    private fun startFileObservation(projectDir: File) {
        fileObserver?.stopWatching()
        fileObserver = ProjectFileObserver(projectDir.absolutePath) {
            stateDelegate.triggerWebReload()
        }
        fileObserver?.startWatching()
    }

    // --- LIFECYCLE ---
    override fun onCleared() {
        super.onCleared()
        fileObserver?.stopWatching()
        buildDelegate.unbindService(getApplication())
        systemEventDelegate.cleanup()
        ziplineDispatcher.close()
    }

    /**
     * Called by UI when a screen transition occurs to flush non-fatal errors.
     */
    fun flushNonFatalErrors() {
        val errors = ErrorCollector.getAndClear()
        if (errors != null) {
            val apiKey = settingsViewModel.getApiKey()
            val githubToken = settingsViewModel.getGithubToken()
            val githubUser = settingsViewModel.getGithubUser() ?: "Unknown"
            val reportToGithub = settingsViewModel.isReportIdeErrorsEnabled()

            if (!apiKey.isNullOrBlank()) {
                val intent = Intent(getApplication(), CrashReportingService::class.java).apply {
                    action = CrashReportingService.ACTION_REPORT_NON_FATAL
                    putExtra(CrashReportingService.EXTRA_API_KEY, apiKey)
                    putExtra(CrashReportingService.EXTRA_JULES_PROJECT_ID, settingsViewModel.getJulesProjectId())
                    putExtra(CrashReportingService.EXTRA_GITHUB_TOKEN, githubToken)
                    putExtra(CrashReportingService.EXTRA_STACK_TRACE, errors)
                    putExtra(CrashReportingService.EXTRA_GITHUB_USER, githubUser)
                    putExtra(CrashReportingService.EXTRA_REPORT_TO_GITHUB, reportToGithub)
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
                    service.getReleases("HereLiesAz", "IDEaz-buildtools")
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
    fun refreshGitData() { viewModelScope.launch { gitDelegate.refreshGitData() } }

    /** Performs a 'git fetch' operation. */
    fun gitFetch() { viewModelScope.launch { gitDelegate.fetch() } }

    /** Performs a 'git pull' operation. */
    fun gitPull() { viewModelScope.launch { gitDelegate.pull() } }

    /** Performs a 'git push' operation. */
    fun gitPush() {
        viewModelScope.launch {
            gitDelegate.push()
            val appName = settingsViewModel.getAppName()
            val user = settingsViewModel.getGithubUser()
            if (!appName.isNullOrBlank() && !user.isNullOrBlank()) {
                val type = ProjectType.fromString(settingsViewModel.getProjectType())
                if (type == ProjectType.ANDROID || type == ProjectType.FLUTTER) {
                    startArtifactPolling(user, appName)
                }
            }
        }
    }

    /** Stashes changes with an optional message. */
    fun gitStash(m: String?) { viewModelScope.launch { gitDelegate.stash(m) } }

    /** Pops the latest stash. */
    fun gitUnstash() { viewModelScope.launch { gitDelegate.unstash() } }

    /** Switches to the specified branch. */
    fun switchBranch(b: String) { viewModelScope.launch { gitDelegate.switchBranch(b) } }

    fun deployWebProject() {
        val appName = settingsViewModel.getAppName()
        val projectTypeStr = settingsViewModel.getProjectType()
        val projectType = ProjectType.fromString(projectTypeStr)
        if (projectType != ProjectType.WEB) return

        viewModelScope.launch {
            logHandler.onBuildLog("Deploying Web Project (Push to GitHub)...")
            try {
                // Ensure latest changes are committed
                gitDelegate.commit("Deploy: ${System.currentTimeMillis()}")
                // Use default push (uses settings creds)
                gitDelegate.push()
                logHandler.onBuildLog("Pushed successfully. GitHub Actions will handle deployment.")
            } catch (e: Exception) {
                logHandler.onBuildLog("Deploy failed: ${e.message}")
            }
        }
    }

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

    fun handleSelection(rect: android.graphics.Rect) {
        overlayDelegate.onSelectionMade(rect)

        if (stateDelegate.currentWebUrl.value != null) {
            val intent = Intent("com.hereliesaz.ideaz.INSPECT_WEB").apply {
                putExtra("X", rect.centerX().toFloat())
                putExtra("Y", rect.centerY().toFloat())
                setPackage(getApplication<Application>().packageName)
            }
            getApplication<Application>().sendBroadcast(intent)
        }
    }

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
            viewModelScope.launch(Dispatchers.IO) {
                com.hereliesaz.ideaz.utils.TemplateManager.copyTemplate(ctx, type, ctx.filesDir.resolve(name), pkg, name)
                withContext(Dispatchers.Main) {
                    saveAndInitialize(name, owner, branch, pkg, type, ctx)
                    onSuccess()
                }
            }
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

            // Check for remote artifacts if it's an Android project
            if (type == ProjectType.ANDROID || type == ProjectType.FLUTTER) {
                startArtifactPolling(user, appName)
            }
        }
    }

    private var pollingJob: Job? = null

    private fun startArtifactPolling(user: String, repo: String) {
        pollingJob?.cancel()
        pollingJob = viewModelScope.launch {
            // logHandler.onOverlayLog("Started polling for remote artifacts...") // Too noisy?
            val startTime = System.currentTimeMillis()
            val timeout = 10 * 60 * 1000L // 10 minutes

            while (System.currentTimeMillis() - startTime < timeout) {
                checkForRemoteArtifact(user, repo, getApplication())

                if (_artifactCheckResult.value?.isRemoteNewer == true) {
                    // logHandler.onOverlayLog("New artifact found.")
                    break
                }

                delay(30_000) // 30 seconds
            }
        }
    }

    private suspend fun checkForRemoteArtifact(user: String, repo: String, context: Context) {
        val token = settingsViewModel.getGithubToken()
        if (token.isNullOrBlank()) return

        try {
            val service = GitHubApiClient.createService(token)
            val releases = withContext(Dispatchers.IO) { service.getReleases(user, repo) }

            // Find latest release (prefer pre-release debug builds) with an APK
            val release = releases.firstOrNull { r ->
                r.prerelease && r.assets.any { it.name.endsWith(".apk") }
            } ?: releases.firstOrNull { r -> r.assets.any { it.name.endsWith(".apk") } } ?: return

            // Find the APK asset with the highest version
            val validAssets = release.assets.filter {
                it.name.endsWith(".apk") && VersionUtils.extractVersionFromFilename(it.name) != null
            }

            val bestAsset = validAssets.sortedWith { a1, a2 ->
                val v1 = VersionUtils.extractVersionFromFilename(a1.name) ?: "0"
                val v2 = VersionUtils.extractVersionFromFilename(a2.name) ?: "0"
                VersionUtils.compareVersions(v1, v2)
            }.lastOrNull() ?: return

            // Parse remote version
            val remoteVersion = VersionUtils.extractVersionFromFilename(bestAsset.name) ?: return

            // Check local APK
            val projectDir = context.filesDir.resolve(repo)
            // Look for generic APKs in typical build output
            val apkDir = File(projectDir, "app/build/outputs/apk/debug")
            val localApk = apkDir.walk().filter { it.extension == "apk" }.firstOrNull()

            var localVersion: String? = null
            if (localApk != null) {
                val pm = context.packageManager
                val info = pm.getPackageArchiveInfo(localApk.absolutePath, 0)
                localVersion = info?.versionName
            }

            // Compare versions
            val isNewer = if (localVersion == null) true else {
                VersionUtils.compareVersions(remoteVersion, localVersion) > 0
            }

            _artifactCheckResult.value = ArtifactCheckResult(
                remoteVersion = remoteVersion,
                downloadUrl = bestAsset.browserDownloadUrl,
                localVersion = localVersion,
                isRemoteNewer = isNewer
            )

        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun downloadLatestArtifact(url: String, onSuccess: (File) -> Unit) {
        viewModelScope.launch {
            stateDelegate.setLoadingProgress(0)
            stateDelegate.setBottomSheetState(com.composables.core.SheetDetent.FullyExpanded) // Expand logs
            logHandler.onBuildLog("Downloading latest artifact from $url...")

            val destFile = File(getApplication<Application>().cacheDir, "downloaded_project.apk")
            val success = downloadFile(url, destFile) { progress ->
                stateDelegate.setLoadingProgress(progress)
            }

            if (success) {
                logHandler.onBuildLog("Download complete. Installing/Loading...")
                onSuccess(destFile)
            } else {
                logHandler.onBuildLog("Download failed.")
            }
            stateDelegate.setLoadingProgress(null)
        }
    }

    /**
     * Checks if the local APK selected by URI is older than the remote version.
     * Returns "downgrade", "upgrade", or "same".
     *
     * Note: This operation suspends and performs IO on Dispatchers.IO.
     */
    suspend fun checkLocalApkVersion(uri: Uri, remoteVersion: String): String {
        return withContext(Dispatchers.IO) {
            try {
                val context = getApplication<Application>()
                // Copy to temp file to parse
                val tempFile = File(context.cacheDir, "temp_check.apk")
                context.contentResolver.openInputStream(uri)?.use { input ->
                    tempFile.outputStream().use { output ->
                        input.copyTo(output)
                    }
                }

                val pm = context.packageManager
                val info = pm.getPackageArchiveInfo(tempFile.absolutePath, 0)
                val localVersion = info?.versionName ?: return@withContext "unknown"
                val localPackage = info.packageName

                // Verify package name
                val targetPackage = settingsViewModel.targetPackageName.value
                // If we don't know target package yet (setup), we might skip or check appName.
                // But usually we know it or can guess.
                // If settingsViewModel doesn't have it, assume "com.hereliesaz." + appName logic?
                // For now, if targetPackage is set, enforce it.
                if (!targetPackage.isNullOrBlank() && localPackage != targetPackage) {
                    logHandler.onOverlayLog("Error: Selected APK package '$localPackage' does not match project '$targetPackage'.")
                    tempFile.delete()
                    return@withContext "mismatch"
                }

                tempFile.delete()

                // Compare versions (simple string comparison for now, or assume semver)
                if (localVersion == remoteVersion) return@withContext "same"

                // Simple semantic version check
                // Format: X.Y.Z
                fun parse(v: String) = v.split(".").mapNotNull { it.toIntOrNull() }
                val rParts = parse(remoteVersion)
                val lParts = parse(localVersion)

                for (i in 0 until maxOf(rParts.size, lParts.size)) {
                    val r = rParts.getOrElse(i) { 0 }
                    val l = lParts.getOrElse(i) { 0 }
                    if (l < r) return@withContext "downgrade"
                    if (l > r) return@withContext "upgrade"
                }
                "same"

            } catch (e: Exception) {
                e.printStackTrace()
                "unknown"
            }
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

    /** Forks a repository. */
    fun forkRepository(u: String, onSuccess: () -> Unit = {}) {
        val parts = u.removePrefix("https://github.com/")
            .removeSuffix(".git")
            .split("/")
            .filter { it.isNotBlank() }

        if (parts.size < 2) {
            logHandler.onOverlayLog("Invalid repository format. Use 'owner/repo'.")
            return
        }

        val owner = parts[0]
        val repo = parts[1]

        repoDelegate.forkRepository(owner, repo) { newOwner, newRepo, _ ->
            viewModelScope.launch {
                repoDelegate.uploadProjectSecrets(newOwner, newRepo)
                aiDelegate.fetchSessionsForRepo("$newOwner/$newRepo")
                repoDelegate.forceUpdateInitFiles()
                onSuccess()
            }
        }
    }

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
        if (n.isBlank()) return
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
                    try {
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
                    } catch (e: Exception) {
                        logHandler.onBuildLog("Sync failed: ${e.message}. Aborting deletion.\n")
                        throw e
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
            if (!projectDir.deleteRecursively()) {
                if (projectDir.exists()) {
                    throw java.io.IOException("Failed to delete project directory: ${projectDir.absolutePath}")
                }
            }
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
        val typeStr = settingsViewModel.getProjectType()
        val type = ProjectType.fromString(typeStr)

        val prompt = if (type == ProjectType.FLUTTER) {
            "Add dependency '$coordinate' to `pubspec.yaml` in the `dependencies` section."
        } else {
            "Add dependency '$coordinate' to the project. Update gradle/libs.versions.toml and app/build.gradle.kts (or build.gradle.kts) accordingly. Ensure to add version to [versions] and library to [libraries] with an alias, then implement it."
        }
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
            val projectDir = settingsViewModel.getProjectPath(appName)
            if (stateDelegate.currentWebUrl.value == null) {
                val indexFile = File(projectDir, "index.html")
                if (indexFile.exists()) {
                    stateDelegate.setCurrentWebUrl("file://${indexFile.absolutePath}")
                }
            }
            startFileObservation(projectDir)
            stateDelegate.setTargetAppVisible(true)
        } else {
            // Android, Flutter, React Native (assume APK)
            val packageName = settingsViewModel.targetPackageName.value

            try {
                if (packageName.isNullOrBlank()) {
                     // Try to detect package name if not set
                    val projectDir = settingsViewModel.getProjectPath(appName)
                    val detectedPackage = ProjectAnalyzer.detectPackageName(projectDir)

                    if (!detectedPackage.isNullOrBlank()) {
                        settingsViewModel.saveTargetPackageName(detectedPackage)
                        stateDelegate.setTargetAppVisible(true)
                    } else {
                        Toast.makeText(c, c.getString(R.string.error_app_not_installed), Toast.LENGTH_SHORT).show()
                    }
                    return
                }

                // Switch to "App View". AndroidProjectHost will handle launching on the virtual display.
                stateDelegate.setTargetAppVisible(true)

            } catch (e: Exception) {
                Toast.makeText(c, c.getString(R.string.error_launch_failed, e.message), Toast.LENGTH_SHORT).show()
            }
        }
    }


    /** Downloads project dependencies. */
    fun downloadDependencies() {
        buildDelegate.downloadDependencies()
    }

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

    private fun handleBuildFailure(log: String) {
        val isAutoDebug = settingsViewModel.isAutoDebugBuildsEnabled()
        if (!isAutoDebug) return

        // Heuristic for IDE Error
        val isIdeError = log.contains("[IDE] Failed") ||
                log.contains("tools not found") ||
                log.contains("com.hereliesaz.ideaz") ||
                (log.contains("FileNotFoundException") && !log.contains("build.gradle"))

        if (isIdeError) {
            if (settingsViewModel.isReportIdeErrorsEnabled()) {
                val token = settingsViewModel.getGithubToken()
                viewModelScope.launch {
                    val result = com.hereliesaz.ideaz.utils.GithubIssueReporter.reportError(
                        getApplication(),
                        token,
                        Throwable("Build Failure (Detected via Log)"),
                        "Build failed with suspected IDE error",
                        log
                    )
                    logHandler.onOverlayLog("IDE Error reported: $result")
                }
            } else {
                logHandler.onOverlayLog("IDE Error detected. Reporting disabled.")
            }
        } else {
            // Project Error -> Jules Session
            aiDelegate.startContextualAITask("Build Failed. Fix this:\n$log")
        }
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
