package com.hereliesaz.ideaz.ui.delegates

import android.app.Application
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.IBinder
import com.hereliesaz.ideaz.IBuildService
import com.hereliesaz.ideaz.api.GitHubApiClient
import com.hereliesaz.ideaz.buildlogic.PullRequestCoordinator
import com.hereliesaz.ideaz.buildlogic.RemoteBuildManager
import com.hereliesaz.ideaz.models.ProjectType
import com.hereliesaz.ideaz.services.BuildService
import com.hereliesaz.ideaz.ui.SettingsViewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.consumeAsFlow
import kotlinx.coroutines.launch
import java.io.File

/**
 * Delegate that drives builds. Local on-device builds have been removed; this
 * dispatches to the remote (GitHub Actions) pipeline via [RemoteBuildManager],
 * polls for the artifact, and installs it.
 *
 * It also retains a binding to [BuildService] so that the foreground notification
 * (Sync & Exit, AI prompt RemoteInput) keeps working while a build is in flight.
 *
 * @param application The Application context.
 * @param settingsViewModel Access to credentials and project settings.
 * @param scope CoroutineScope for build orchestration.
 * @param onLog Callback for general build logs (batched).
 * @param onOverlayLog Callback for high-priority user-visible logs (toasts/overlay).
 * @param onBuildFailure Callback invoked on build error.
 * @param onWebBuildSuccess Callback for Web project success (URL/Path).
 * @param onAndroidBuildSuccess Callback for Android project success (Triggers app launch).
 * @param gitDelegate Reference to GitDelegate for commit/push operations required for remote builds.
 */
class BuildDelegate(
    private val application: Application,
    private val settingsViewModel: SettingsViewModel,
    private val scope: CoroutineScope,
    private val onLog: (String) -> Unit,
    private val onOverlayLog: (String) -> Unit,
    private val onBuildFailure: (String) -> Unit,
    private val onWebBuildSuccess: (String) -> Unit,
    private val onAndroidBuildSuccess: () -> Unit,
    private val gitDelegate: GitDelegate
) {

    // AIDL Interface to the remote service (used only to keep the foreground
    // notification alive; build logic itself runs in this process).
    private var buildService: IBuildService? = null
    private var isBuildServiceBound = false
    private var isServiceRegistered = false

    // Track the current build job to allow cancellation
    private var buildJob: Job? = null

    // --- Log Batching ---
    // Unbounded channel to receive logs without blocking.
    private val logChannel = Channel<String>(Channel.UNLIMITED)

    init {
        // Consumer loop: Reads from channel and dispatches to onLog in batches.
        // Runs on Dispatchers.Default to handle string manipulation off the main thread.
        scope.launch(Dispatchers.Default) {
            val batch = StringBuilder()
            var lastUpdate = 0L

            logChannel.consumeAsFlow().collect { msg ->
                batch.append(msg).append("\n")
                val now = System.currentTimeMillis()
                // Update UI state at most every 200ms to reduce recomposition pressure
                if (now - lastUpdate > 200) {
                    val batchStr = batch.toString()
                    batch.setLength(0) // Clear buffer
                    lastUpdate = now
                    onLog(batchStr)
                }
            }
        }
    }

    /**
     * Connection callbacks for the Bound Service.
     */
    private val buildServiceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            buildService = IBuildService.Stub.asInterface(service)
            isBuildServiceBound = true
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            buildService = null
            isBuildServiceBound = false
        }
    }

    /**
     * Forwards a log line to the consumer loop.
     */
    private fun pushLog(message: String) {
        logChannel.trySend(message)
    }

    /**
     * Handles a successful build (remote).
     * @param apkPath The absolute path to the downloaded APK (or web artifact).
     */
    private fun handleSuccess(apkPath: String) {
        scope.launch {
            onLog("\n[IDE] Build successful: $apkPath\n")

            val type = ProjectType.fromString(settingsViewModel.getProjectType())

            if (type.isWebLike()) {
                onLog("[IDE] Web Project ready. Loading WebView...\n")
                onWebBuildSuccess(apkPath)
                // Deployment (push to GitHub Pages) is now an explicit action via
                // the rail's "Deploy" item / MainViewModel.deployWebProject(). Avoid
                // the auto-push that previously fired here — it duplicated the push
                // already done by startBuild and surprised users by deploying every
                // time they just wanted to preview locally.
            } else {
                onOverlayLog("Build successful. Updating...")
                onAndroidBuildSuccess()
            }
        }
    }

    private fun handleFailure(log: String) {
        scope.launch {
            onLog("\n[IDE] Build Failed.\n")
            onOverlayLog("Build failed. Check global log.")
            onBuildFailure(log)
        }
    }

    // --- Service Management ---

    fun bindService(context: Context) {
        if (isServiceRegistered) return
        val intent = Intent(context, BuildService::class.java)
        context.applicationContext.bindService(intent, buildServiceConnection, Context.BIND_AUTO_CREATE)
        isServiceRegistered = true
    }

    fun unbindService(context: Context) {
        if (isServiceRegistered) {
            try {
                context.applicationContext.unbindService(buildServiceConnection)
            } catch (e: Exception) {
                // Ignore errors if already unbound
            }
            isServiceRegistered = false
        }
    }

    // --- Build Logic ---

    /**
     * Starts the build process. Always runs the remote (GitHub Actions) flow.
     */
    fun startBuild(projectDir: File? = null) {
        // Cancel any currently running build job to prevent conflicts.
        buildJob?.cancel()

        buildJob = scope.launch {
            val typeStr = settingsViewModel.getProjectType()
            val type = ProjectType.fromString(typeStr)

            val token = settingsViewModel.getGithubToken()
            val user = settingsViewModel.getGithubUser()

            val dir = projectDir ?: settingsViewModel.getProjectPath(settingsViewModel.getAppName() ?: "")

            // Web projects: no remote build to wait on — just verify the local
            // index.html exists and let the WebView load it. Deploy is now an
            // explicit user action (rail → Deploy / deployWebProject), so
            // startBuild no longer commits or pushes on its own. Previously
            // "Save & Initialize" silently fired an unconditional commit+push,
            // which was a surprise the userflow audit flagged.
            if (type.isWebLike()) {
                pushLog("[IDE] Web Project: preparing local preview.\n")
                val indexHtml = File(dir, "index.html")
                if (indexHtml.exists()) {
                    handleSuccess(indexHtml.absolutePath)
                } else {
                    handleFailure("Web project has no index.html at ${dir.absolutePath}")
                }
                return@launch
            }

            // Android: remote build only.
            if (token.isNullOrBlank() || user.isNullOrBlank()) {
                pushLog("Error: Remote Build requires GitHub Token and User.\n")
                handleFailure("Remote Build requires GitHub Token and User.")
                return@launch
            }
            startRemoteBuild(dir, user, token)
        }
    }

    /**
     * Commit + push to trigger the GitHub Actions workflow, then poll for the APK.
     */
    private suspend fun startRemoteBuild(dir: File, user: String, token: String) {
        onLog("[IDE] Preparing Remote Build...\n")

        if (!gitDelegate.commit("Remote Build Trigger: ${System.currentTimeMillis()}")) {
            onLog("Error: Commit failed. Aborting remote build.\n")
            handleFailure("Commit failed. Aborting remote build.")
            return
        }

        onLog("[IDE] Pushing to remote...\n")
        if (!gitDelegate.push()) {
            onLog("Error: Push failed. Aborting remote build.\n")
            handleFailure("Push failed. Aborting remote build.")
            return
        }

        val headSha = gitDelegate.getHeadSha()
        if (headSha == null) {
            onLog("Error: Could not determine HEAD SHA.\n")
            handleFailure("Could not determine HEAD SHA.")
            return
        }

        val api = GitHubApiClient.createService(token)
        val manager = RemoteBuildManager(
            application, api, token, user, dir.name,
            onLog = { msg -> pushLog(msg) }
        )
        val apkPath = manager.pollAndDownload(headSha)
        if (apkPath != null) {
            handleSuccess(apkPath)
        } else {
            handleFailure("Remote Build Failed.")
        }
    }

    /**
     * The agent (Jules) opened a pull request. Auto-merge it, then poll Actions for
     * the rebuilt APK and install it — the "merge → rebuild → re-sideload" half of
     * the PR-based Android loop. Reuses [RemoteBuildManager] against the merge SHA
     * (the merge already pushed to the default branch, so there's no commit/push to
     * do here, unlike [startRemoteBuild]).
     */
    fun installFromMergedPr(prUrl: String) {
        // Cancel any in-flight build so the merge-driven rebuild is authoritative.
        buildJob?.cancel()
        buildJob = scope.launch {
            val token = settingsViewModel.getGithubToken()
            val user = settingsViewModel.getGithubUser()
            val repo = settingsViewModel.getAppName()
            if (token.isNullOrBlank() || user.isNullOrBlank() || repo.isNullOrBlank()) {
                handleFailure("Auto-merge requires GitHub token, user, and an open project.")
                return@launch
            }

            val api = GitHubApiClient.createService(token)
            onOverlayLog("Merging pull request...")
            val mergeSha = PullRequestCoordinator(api).mergeAndGetSha(prUrl)
            if (mergeSha == null) {
                onLog("[IDE] Could not merge PR: $prUrl\n")
                handleFailure("Failed to merge pull request. Check for conflicts or required reviews.")
                return@launch
            }

            onLog("[IDE] PR merged. Rebuilding from ${mergeSha.take(7)}...\n")
            val manager = RemoteBuildManager(
                application, api, token, user, repo,
                onLog = { msg -> pushLog(msg) }
            )
            val apkPath = manager.pollAndDownload(mergeSha)
            if (apkPath != null) {
                handleSuccess(apkPath)
            } else {
                handleFailure("Remote build after merge failed.")
            }
        }
    }

    /**
     * Requests cancellation of the current build operation.
     */
    fun cancelBuild() {
        scope.launch {
            buildJob?.cancel()
            buildJob = null
            if (isBuildServiceBound) {
                try {
                    buildService?.cancelBuild()
                } catch (e: Exception) {
                    // Ignore — service may have died.
                }
            }
            onLog("Cancellation requested.\n")
        }
    }
}
