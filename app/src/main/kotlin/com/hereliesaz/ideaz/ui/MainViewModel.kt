@file:Suppress("DEPRECATION")

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
import com.hereliesaz.ideaz.ui.editor.EditorViewModel
import com.hereliesaz.ideaz.services.JsCompilerService
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
 * The central ViewModel for the IDEaz application.
 *
 * **Role:**
 * This class serves as the "Brain" of the IDE. It orchestrates the interaction between:
 * - The UI layer (Screens, Composables).
 * - The background build system ([BuildService]).
 * - The AI agent ([AIDelegate]).
 * - The Version Control System ([GitDelegate]).
 * - The Host Environment (VirtualDisplay/WebView).
 *
 * **Architecture:**
 * To avoid a "God Class" anti-pattern, logic is split into specialized [Delegates]:
 * - [AIDelegate]: Manages AI sessions and Jules API calls.
 * - [BuildDelegate]: Manages the connection to the remote BuildService and build execution.
 * - [GitDelegate]: Manages local Git operations (clone, commit, push).
 * - [RepoDelegate]: Manages remote repository operations (GitHub API).
 * - [OverlayDelegate]: Manages the visual overlay and selection mode.
 * - [StateDelegate]: Centralizes shared UI state (logs, progress).
 * - [SystemEventDelegate]: Handles system broadcasts (screen on/off, package changes).
 * - [UpdateDelegate]: Handles self-updates.
 *
 * @param application The Android Application context.
 * @param settingsViewModel The ViewModel for accessing and modifying user settings.
 */
class MainViewModel(
    application: Application,
    val settingsViewModel: SettingsViewModel
) : AndroidViewModel(application) {

    // --- Sub-ViewModels ---

    /**
     * Lazy instantiation of [EditorViewModel] to avoid overhead if the editor is not used.
     * Passed [JsCompilerService] to support on-the-fly JS compilation for Web projects.
     */
    val editorViewModel: EditorViewModel by lazy {
        EditorViewModel(JsCompilerService(application))
    }

    // --- Core Infrastructure ---

    /**
     * Dedicated single-threaded dispatcher for Zipline operations.
     * Zipline (QuickJS) requires all access to occur on a single thread to ensure thread confinement.
     */
    private val ziplineDispatcher = Executors.newSingleThreadExecutor().asCoroutineDispatcher()

    /**
     * Loader for the Zipline dynamic code engine.
     *
     * **Security Note:**
     * Currently using [ManifestVerifier.NO_SIGNATURE_CHECKS] for development velocity.
     * TODO(Phase 11.5): Implement Ed25519 signature verification for production releases.
     */
    val ziplineLoader: ZiplineLoader by lazy {
        val app = application as MainApplication
        ZiplineLoader(
            dispatcher = ziplineDispatcher,
            manifestVerifier = ManifestVerifier.NO_SIGNATURE_CHECKS,
            httpClient = app.okHttpClient.asZiplineHttpClient(),
        )
    }

    /**
     * Shared State Delegate. Holds all StateFlows used by the UI.
     */
    val stateDelegate = StateDelegate(viewModelScope)

    init {
        // Start observing system logs (logcat) immediately.
        viewModelScope.launch {
            com.hereliesaz.ideaz.utils.LogcatReader.observe().collect {
                stateDelegate.appendSystemLog(it)
            }
        }
    }

    // --- Delegation Glue ---

    /**
     * Anonymous implementation of [LogHandler] to pass to delegates.
     * This acts as the "bridge" allowing delegates to push logs/progress back to the [StateDelegate]
     * without knowing about the specific implementation details or coupling directly to [MainViewModel].
     */
    private val logHandler = object : LogHandler {
        override fun onBuildLog(msg: String) { stateDelegate.appendBuildLog(msg) }

        override fun onAiLog(msg: String) {
            stateDelegate.appendAiLog(msg)
            // Broadcast for the Overlay UI (which runs in a separate Service process/context)
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
             stateDelegate.appendAiLog(msg) // Fallback to AI log for now
        }
    }

    // --- Delegate Initialization ---

    val aiDelegate = AIDelegate(
        settingsViewModel,
        viewModelScope,
        logHandler::onAiLog,
        { diff -> applyUnidiffPatchInternal(diff) },
        jsCompilerService = JsCompilerService(application),
        onWebReload = { stateDelegate.triggerWebReload() }
    )

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
            // Web Build Success Callback
            stateDelegate.setCurrentWebUrl("file://$path")
            stateDelegate.setTargetAppVisible(true) // Switch to "App View"

            // Update EditorViewModel with project context for file browsing
            val appName = settingsViewModel.getAppName()
            if (appName != null) {
                val projectDir = settingsViewModel.getProjectPath(appName)
                editorViewModel.setProjectDir(projectDir)
            }
        },
        {
            // Android Build Success Callback
            // Notify System that update is ready (if applicable) or just launch
            val intent = Intent("com.hereliesaz.ideaz.SHOW_UPDATE_POPUP")
            if (lastPrompt != null) {
                intent.putExtra("PROMPT", lastPrompt)
            }
            application.sendBroadcast(intent)
            launchTargetApp(application)
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

    // Handle System Events (Broadcasts)
    val systemEventDelegate = SystemEventDelegate(
        application,
        aiDelegate,
        overlayDelegate,
        stateDelegate
    ) { manifestPath ->
        reloadZipline(manifestPath)
    }

    // --- Zipline (Dynamic Code) Management ---

    private var currentZipline: app.cash.zipline.Zipline? = null

    /**
     * Reloads the Zipline engine from a local manifest.
     * Currently disabled due to deprecation of `loadOnce` API in the Zipline library.
     */
    private fun reloadZipline(manifestPath: String) {
        viewModelScope.launch(ziplineDispatcher) {
            try {
                logHandler.onBuildLog("[Zipline] Reloading from $manifestPath...")
                currentZipline?.close()
                currentZipline = null

                // Load from file URL
                val manifestUrl = File(manifestPath).toURI().toString()

                // FIXME: Zipline API loadOnce/load is deprecated (ERROR level) and cannot be used.
                // Disabling temporarily to unblock build.
                // val result = ziplineLoader.loadOnce("guest", manifestUrl) { ... }
                val result: app.cash.zipline.loader.LoadResult = app.cash.zipline.loader.LoadResult.Failure(Exception("Zipline disabled due to deprecation"))

                if (result is app.cash.zipline.loader.LoadResult.Success) {
                    currentZipline = result.zipline
                    logHandler.onBuildLog("[Zipline] Reload complete.")
                } else if (result is app.cash.zipline.loader.LoadResult.Failure) {
                    throw result.exception
                }

            } catch (e: Exception) {
                val msg = "[Zipline] Reload failed: ${e.message}"
                logHandler.onBuildLog(msg)
                e.printStackTrace()
                // Auto-report Guest runtime crash to Jules for analysis
                aiDelegate.startContextualAITask("Guest Code Runtime Error. Please fix:\n$msg\n${e.stackTraceToString()}")
            }
        }
    }

    // --- Public State Exposure (Delegated) ---

    // Expose StateFlows directly from delegates to avoid boilerplate duplication
    val loadingProgress = stateDelegate.loadingProgress
    val isTargetAppVisible = stateDelegate.isTargetAppVisible
    val currentWebUrl = stateDelegate.currentWebUrl
    val buildLog = stateDelegate.buildLog
    val filteredLog = stateDelegate.filteredLog
    val pendingRoute = stateDelegate.pendingRoute

    val isSelectMode = overlayDelegate.isSelectMode
    val activeSelectionRect = overlayDelegate.activeSelectionRect
    val isContextualChatVisible = overlayDelegate.isContextualChatVisible
    val requestScreenCapture = overlayDelegate.requestScreenCapture

    // Track the last user prompt to restore context after an app restart
    private var lastPrompt: String? = null

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

    // --- Artifact Check State ---

    data class ArtifactCheckResult(
        val remoteVersion: String,
        val downloadUrl: String,
        val localVersion: String?,
        val isRemoteNewer: Boolean
    )
    private val _artifactCheckResult = MutableStateFlow<ArtifactCheckResult?>(null)
    /** Holds the result of a check for remote GitHub artifacts (APKs). */
    val artifactCheckResult = _artifactCheckResult.asStateFlow()

    fun dismissArtifactDialog() { _artifactCheckResult.value = null }

    // --- File Observation ---

    private var fileObserver: ProjectFileObserver? = null

    /**
     * Starts watching the project directory for file changes.
     * Currently used to trigger WebView reloads for Web projects.
     */
    private fun startFileObservation(projectDir: File) {
        fileObserver?.stopWatching()
        fileObserver = ProjectFileObserver(projectDir.absolutePath) {
            stateDelegate.triggerWebReload()
        }
        fileObserver?.startWatching()
    }

    // --- Lifecycle ---

    override fun onCleared() {
        super.onCleared()
        fileObserver?.stopWatching()
        buildDelegate.unbindService(getApplication())
        systemEventDelegate.cleanup()
        ziplineDispatcher.close()
    }

    /**
     * Called by UI when a screen transition occurs to flush non-fatal errors.
     *
     * **Logic:**
     * 1. Retrieves unique, non-fatal errors collected by [ErrorCollector].
     * 2. If errors exist, starts the [CrashReportingService] with an intent to report them to the configured backend (GitHub/Jules).
     * This ensures that "silent" errors don't pile up without user/dev visibility.
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

    // --- Proxy Methods (Forwarding calls to Delegates) ---

    // BUILD Operations
    fun bindBuildService(c: Context) = buildDelegate.bindService(c)
    fun unbindBuildService(c: Context) = buildDelegate.unbindService(c)
    fun startBuild(c: Context, p: File? = null) = buildDelegate.startBuild(p)
    fun clearBuildCaches(c: Context) { /* TODO: Implement cache clearing logic in BuildService */ }

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
                    service.getReleases(BuildConfig.BUILD_TOOLS_OWNER, BuildConfig.BUILD_TOOLS_REPO)
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

                // Reuse download logic
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

    // GIT Operations
    fun refreshGitData() { viewModelScope.launch { gitDelegate.refreshGitData() } }
    fun gitFetch() { viewModelScope.launch { gitDelegate.fetch() } }
    fun gitPull() { viewModelScope.launch { gitDelegate.pull() } }
    fun gitPush() {
        viewModelScope.launch(Dispatchers.IO) {
            gitDelegate.push()
            // After pushing, start polling for artifacts if it's an app that produces them
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
    fun gitStash(m: String?) { viewModelScope.launch { gitDelegate.stash(m) } }
    fun gitUnstash() { viewModelScope.launch { gitDelegate.unstash() } }
    fun switchBranch(b: String) { viewModelScope.launch { gitDelegate.switchBranch(b) } }

    /**
     * Triggers deployment for Web Projects (GitHub Pages).
     */
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
                logHandler.onBuildLog("[Instruction] Ensure 'GitHub Pages' is enabled in repository settings (Source: gh-pages branch).")
                withContext(Dispatchers.Main) {
                    Toast.makeText(getApplication(), getApplication<Application>().getString(R.string.deploy_instruction_gh_pages), Toast.LENGTH_LONG).show()
                }

                // Start Polling for Deployment completion
                val user = settingsViewModel.getGithubUser()
                if (appName != null && user != null) {
                    startWebDeploymentPolling(user, appName)
                }

            } catch (e: Exception) {
                logHandler.onBuildLog("Deploy failed: ${e.message}")
            }
        }
    }

    private fun startWebDeploymentPolling(user: String, repo: String) {
        pollingJob?.cancel()
        pollingJob = viewModelScope.launch {
            logHandler.onBuildLog("Polling GitHub Pages status for $user/$repo...")
            val startTime = System.currentTimeMillis()
            val timeout = 10 * 60 * 1000L // 10 minutes

            while (System.currentTimeMillis() - startTime < timeout) {
                val token = settingsViewModel.getGithubToken()
                if (token.isNullOrBlank()) {
                    logHandler.onBuildLog("GitHub token missing, stopping poll.")
                    break
                }

                try {
                    val service = GitHubApiClient.createService(token)
                    val response = service.getPages(user, repo)

                    if (response.isSuccessful) {
                        val body = response.body()
                        val status = body?.status
                        val url = body?.htmlUrl

                        if (status == "built" && url != null) {
                            logHandler.onBuildLog("Deployment successful! Loading: $url")
                            stateDelegate.setCurrentWebUrl(url)
                            stateDelegate.setTargetAppVisible(true)
                            break
                        } else {
                            logHandler.onBuildLog("Deployment status: $status...")
                        }
                    } else if (response.code() == 404) {
                        logHandler.onBuildLog("Waiting for GitHub Pages to be available (404)...")
                    } else {
                        logHandler.onBuildLog("Error polling pages: Code ${response.code()}")
                    }

                } catch (e: Exception) {
                    logHandler.onBuildLog("Error polling pages: ${e.message}")
                    e.printStackTrace()
                }

                delay(15_000) // 15 seconds
            }
        }
    }

    // AI Operations
    fun sendPrompt(p: String?) {
        if (!p.isNullOrBlank()) {
            lastPrompt = p
            aiDelegate.startContextualAITask(p)
        }
    }

    fun submitContextualPrompt(p: String) {
        lastPrompt = p
        val context = overlayDelegate.pendingContextInfo ?: "No context"
        val base64 = overlayDelegate.pendingBase64Screenshot
        val richPrompt = if (base64 != null) "$context\n\n$p\n\n[IMAGE: data:image/png;base64,$base64]" else "$context\n\n$p"
        aiDelegate.startContextualAITask(richPrompt)
    }

    fun resumeSession(id: String) = aiDelegate.resumeSession(id)
    fun fetchSessionsForRepo(r: String) = aiDelegate.fetchSessionsForRepo(r)

    // OVERLAY Operations
    fun toggleSelectMode(b: Boolean) = overlayDelegate.toggleSelectMode(b)

    fun handleSelection(rect: android.graphics.Rect) {
        overlayDelegate.onSelectionMade(rect)
        // Broadcast specifically for Web inspection if active
        if (stateDelegate.currentWebUrl.value != null) {
            val intent = Intent("com.hereliesaz.ideaz.INSPECT_WEB").apply {
                putExtra("X", rect.centerX().toFloat())
                putExtra("Y", rect.centerY().toFloat())
                setPackage(getApplication<Application>().packageName)
            }
            getApplication<Application>().sendBroadcast(intent)
        }
    }

    fun clearSelection() = overlayDelegate.clearSelection()
    fun closeContextualChat() = overlayDelegate.clearSelection()
    fun requestScreenCapturePermission() = overlayDelegate.requestScreenCapturePermission()
    fun screenCaptureRequestHandled() = overlayDelegate.screenCaptureRequestHandled()
    fun setScreenCapturePermission(c: Int, d: Intent?) = overlayDelegate.setScreenCapturePermission(c, d)
    fun hasScreenCapturePermission() = overlayDelegate.hasScreenCapturePermission()
    fun setPendingRoute(r: String?) = stateDelegate.setPendingRoute(r)

    // REPO Operations
    fun fetchGitHubRepos() = repoDelegate.fetchGitHubRepos()
    fun scanLocalProjects() = repoDelegate.scanLocalProjects()
    fun getLocalProjectsWithMetadata() = repoDelegate.getLocalProjectsWithMetadata()
    fun forceUpdateInitFiles() = repoDelegate.forceUpdateInitFiles()
    fun uploadProjectSecrets(o: String, r: String) = repoDelegate.uploadProjectSecrets(o, r)

    /** Creates a new repo and initializes the project. */
    fun createGitHubRepository(name: String, desc: String, priv: Boolean, type: ProjectType, pkg: String, ctx: Context, onSuccess: () -> Unit) {
        repoDelegate.createGitHubRepository(name, desc, priv, type, pkg, ctx) { owner, branch ->
            viewModelScope.launch(Dispatchers.IO) {
                // Copy template files
                com.hereliesaz.ideaz.utils.TemplateManager.copyTemplate(ctx, type, ctx.filesDir.resolve(name), pkg, name)
                withContext(Dispatchers.Main) {
                    saveAndInitialize(name, owner, branch, pkg, type, ctx)
                    onSuccess()
                }
            }
        }
    }

    /** Selects a repo and prepares it for use. */
    fun selectRepositoryForSetup(repo: GitHubRepoResponse, onSuccess: () -> Unit) {
        repoDelegate.selectRepositoryForSetup(repo) { owner, branch ->
            repoDelegate.uploadProjectSecrets(owner, repo.name)
            aiDelegate.fetchSessionsForRepo(repo.fullName)
            repoDelegate.forceUpdateInitFiles()
            onSuccess()
        }
    }

    /**
     * Saves configuration and triggers initial build.
     * Common exit path for Setup/Clone/Load flows.
     */
    fun saveAndInitialize(appName: String, user: String, branch: String, pkg: String, type: ProjectType, context: Context, initialPrompt: String? = null) {
        viewModelScope.launch {
            aiDelegate.clearSession()
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
            val startTime = System.currentTimeMillis()
            val timeout = 10 * 60 * 1000L // 10 minutes

            while (System.currentTimeMillis() - startTime < timeout) {
                checkForRemoteArtifact(user, repo, getApplication())

                if (_artifactCheckResult.value?.isRemoteNewer == true) {
                    break
                }
                delay(30_000) // 30 seconds
            }
        }
    }

    /**
     * Checks if a remote artifact (APK) exists and is newer than local.
     */
    private suspend fun checkForRemoteArtifact(user: String, repo: String, context: Context) {
        val token = settingsViewModel.getGithubToken()
        if (token.isNullOrBlank()) return

        try {
            val service = GitHubApiClient.createService(token)
            val releases = withContext(Dispatchers.IO) { service.getReleases(user, repo) }

            // Find valid release
            val release = releases.firstOrNull { r ->
                r.prerelease && r.assets.any { it.name.endsWith(".apk") }
            } ?: releases.firstOrNull { r -> r.assets.any { it.name.endsWith(".apk") } } ?: return

            val validAssets = release.assets.filter {
                it.name.endsWith(".apk") && VersionUtils.extractVersionFromFilename(it.name) != null
            }

            val bestAsset = validAssets.sortedWith { a1, a2 ->
                val v1 = VersionUtils.extractVersionFromFilename(a1.name) ?: "0"
                val v2 = VersionUtils.extractVersionFromFilename(a2.name) ?: "0"
                VersionUtils.compareVersions(v1, v2)
            }.lastOrNull() ?: return

            val remoteVersion = VersionUtils.extractVersionFromFilename(bestAsset.name) ?: return

            // Check Local Version
            val projectDir = context.filesDir.resolve(repo)
            val possibleDirs = listOf(
                File(projectDir, "app/build/outputs/apk/debug"),
                File(projectDir, "android/app/build/outputs/apk/debug"),
                File(projectDir, "build/app/outputs/flutter-apk")
            )

            val localApk = possibleDirs.asSequence()
                .flatMap { it.walk() }
                .filter { it.extension == "apk" }
                .firstOrNull()

            var localVersion: String? = null
            if (localApk != null) {
                val pm = context.packageManager
                val info = pm.getPackageArchiveInfo(localApk.absolutePath, 0)
                localVersion = info?.versionName
            }

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

    /** Downloads the artifact selected by the user. */
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
     */
    suspend fun checkLocalApkVersion(uri: Uri, remoteVersion: String): String {
        return withContext(Dispatchers.IO) {
            try {
                val context = getApplication<Application>()
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

                val targetPackage = settingsViewModel.targetPackageName.value
                if (!targetPackage.isNullOrBlank() && localPackage != targetPackage) {
                    logHandler.onOverlayLog("Error: Selected APK package '$localPackage' does not match project '$targetPackage'.")
                    tempFile.delete()
                    return@withContext "mismatch"
                }

                tempFile.delete()

                if (localVersion == remoteVersion) return@withContext "same"

                val rParts = remoteVersion.split(".").mapNotNull { it.toIntOrNull() }
                val lParts = localVersion.split(".").mapNotNull { it.toIntOrNull() }

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

    fun loadProject(name: String, onSuccess: () -> Unit) {
        viewModelScope.launch {
            aiDelegate.clearSession()
            settingsViewModel.setAppName(name)
            val user = settingsViewModel.getGithubUser()
            if (!user.isNullOrBlank()) repoDelegate.uploadProjectSecrets(user, name)
            onSuccess()
        }
    }

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
     * Copies the folder to the app's internal storage (`filesDir`) for read/write access.
     * This is necessary because direct in-place editing of `content://` URIs is limited/unreliable for build tools.
     */
    fun registerExternalProject(u: Uri) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                // Convert to DocumentFile
                val documentFile = if (u.scheme == "file" && u.path != null) {
                    androidx.documentfile.provider.DocumentFile.fromFile(File(u.path!!))
                } else {
                    try {
                        // Persist permissions across reboots
                        val takeFlags: Int = Intent.FLAG_GRANT_READ_URI_PERMISSION or
                                Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                        getApplication<Application>().contentResolver.takePersistableUriPermission(u, takeFlags)
                    } catch (e: Exception) {
                        // Ignore if persistence is not supported (e.g., file://)
                    }
                    androidx.documentfile.provider.DocumentFile.fromTreeUri(getApplication(), u)
                }

                if (documentFile == null || !documentFile.isDirectory) {
                    logHandler.onOverlayLog("Invalid project directory selected.")
                    return@launch
                }

                // Handle Name Collision
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

                // Copy files
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

    /** Deletes a project locally. */
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
     * Syncs changes to remote repository before deleting local files.
     * Prevents data loss.
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

    // UPDATE Operations
    fun checkForExperimentalUpdates() = updateDelegate.checkForExperimentalUpdates()
    fun confirmUpdate() = updateDelegate.confirmUpdate()
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

    fun clearLog() = stateDelegate.clearLog()

    /**
     * Launches the target application (Android, Web, or React Native).
     *
     * **Logic:**
     * - **Web:** Points the WebView to the project's `index.html`.
     * - **React Native:** Launches `ReactNativeActivity` if the bundle exists, else fallback to APK.
     * - **Android/Flutter:** Switches to "App View" (Host) or launches installed APK.
     */
    fun launchTargetApp(c: Context) {
        // Suppress launch if Artifact Dialog is open
        if (_artifactCheckResult.value != null) {
            logHandler.onBuildLog("[IDE] Launch suppressed: Artifact selection dialog is open.")
            return
        }

        val appName = settingsViewModel.getAppName() ?: return
        val projectTypeStr = settingsViewModel.getProjectType()
        val projectType = ProjectType.fromString(projectTypeStr)

        if (projectType == ProjectType.WEB) {
            val projectDir = settingsViewModel.getProjectPath(appName)
            if (stateDelegate.currentWebUrl.value == null) {
                // For Web Projects with Kotlin/JS, we rely on the extracted www/index.html
                // which references the compiled app.js.
                val wwwDir = File(c.filesDir, "www")
                val indexFile = File(wwwDir, "index.html")
                // Fallback to project source index.html if www/index.html is missing
                val fileToLoad = if (indexFile.exists()) indexFile else File(projectDir, "index.html")

                if (fileToLoad.exists()) {
                    stateDelegate.setCurrentWebUrl("file://${fileToLoad.absolutePath}")
                }
            }
            startFileObservation(projectDir)
            stateDelegate.setTargetAppVisible(true)
        } else if (projectType == ProjectType.REACT_NATIVE) {
            val projectDir = settingsViewModel.getProjectPath(appName)
            val bundleFile = File(projectDir, "build/react_native_dist/index.android.bundle")

            if (bundleFile.exists()) {
                // Try to resolve module name from app.json
                var moduleName = appName
                val appJsonFile = File(projectDir, "app.json")
                if (appJsonFile.exists()) {
                    try {
                        val json = org.json.JSONObject(appJsonFile.readText())
                        moduleName = json.optString("name", appName)
                        val expo = json.optJSONObject("expo")
                        if (moduleName == appName && expo != null) {
                            moduleName = expo.optString("name", appName)
                        }
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                }

                val intent = Intent(c, com.hereliesaz.ideaz.react.ReactNativeActivity::class.java).apply {
                    putExtra("BUNDLE_PATH", bundleFile.absolutePath)
                    putExtra("MODULE_NAME", moduleName)
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                }
                c.startActivity(intent)
                return
            }

            // Fallback
            val packageName = settingsViewModel.targetPackageName.value
            launchInstalledApk(c, packageName, appName)
        } else {
            // Android, Flutter
            val packageName = settingsViewModel.targetPackageName.value
            launchInstalledApk(c, packageName, appName)
        }
    }

    private fun launchInstalledApk(c: Context, packageName: String?, appName: String) {
            try {
                if (packageName.isNullOrBlank()) {
                    // Try to detect
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

                // Switch to "App View"
                stateDelegate.setTargetAppVisible(true)

            } catch (e: Exception) {
                Toast.makeText(c, c.getString(R.string.error_launch_failed, e.message), Toast.LENGTH_SHORT).show()
            }
    }

    fun downloadDependencies() {
        buildDelegate.downloadDependencies()
    }

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

        // Heuristics to separate IDE errors from User Project errors
        val isIdeError = log.contains("[IDE] Failed") ||
                log.contains("tools not found") ||
                log.contains("com.hereliesaz.ideaz") ||
                (log.contains("FileNotFoundException") && !log.contains("build.gradle")) ||
                log.contains("OutOfMemoryError") ||
                log.contains("No space left on device") ||
                log.contains("Exit code 139") || // Segfault
                log.contains("Exit code 134") || // Abort
                log.contains("Signal 11") ||
                log.contains("Signal 6")

        if (isIdeError) {
            if (settingsViewModel.isReportIdeErrorsEnabled()) {
                val token = settingsViewModel.getGithubToken()
                viewModelScope.launch {
                    val result = com.hereliesaz.ideaz.utils.GithubIssueReporter.reportError(
                        getApplication(),
                        token,
                        Throwable("Build Failure (Environment/Infrastructure Issue)"),
                        "Build failed with suspected IDE/Environment error",
                        log
                    )
                    logHandler.onOverlayLog("Environment/IDE Error reported: $result")
                }
            } else {
                logHandler.onOverlayLog("Environment/IDE Error detected. Reporting disabled.")
            }
        } else {
            // Project Error -> Ask Jules to fix it
            aiDelegate.startContextualAITask("Build Failed. Fix this:\n$log")
        }
    }
}

/**
 * Interface for handling log events from delegates.
 * Decouples delegates from MainViewModel implementation details.
 */
interface LogHandler {
    fun onBuildLog(msg: String)
    fun onAiLog(msg: String)
    fun onProgress(p: Int?)
    fun onGitProgress(p: Int, t: String)
    fun onOverlayLog(msg: String)
}
