package com.hereliesaz.ideaz.ui.delegates

import android.app.Application
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.IBinder
import com.hereliesaz.ideaz.IBuildCallback
import com.hereliesaz.ideaz.IBuildService
import com.hereliesaz.ideaz.api.GitHubApiClient
import com.hereliesaz.ideaz.buildlogic.RemoteBuildManager
import com.hereliesaz.ideaz.git.GitManager
import com.hereliesaz.ideaz.models.ProjectType
import com.hereliesaz.ideaz.services.BuildService
import com.hereliesaz.ideaz.ui.SettingsViewModel
import com.hereliesaz.ideaz.utils.SourceMapParser
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.consumeAsFlow
import java.io.File
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Delegate responsible for managing the connection to the [BuildService] and orchestrating build operations.
 *
 * **Architecture:**
 * - **Service Binding:** Maintains an AIDL connection to the remote `:build_process` service.
 * - **Race to Build:** Implements a strategy where local builds and remote (GitHub Actions) builds run simultaneously, taking the result of whichever finishes first.
 * - **Log Batching:** Buffers logs from the AIDL callback (Binder thread) before sending them to the UI, preventing main thread congestion.
 *
 * @param application The Application context.
 * @param settingsViewModel Access to build settings (local vs remote enabled) and tokens.
 * @param scope CoroutineScope for build orchestration.
 * @param onLog Callback for general build logs (batched).
 * @param onOverlayLog Callback for high-priority user-visible logs (toasts/overlay).
 * @param onSourceMapUpdated Callback invoked when a new Source Map is generated (for UI inspection).
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
    private val onSourceMapUpdated: (Map<String, com.hereliesaz.ideaz.models.SourceMapEntry>) -> Unit,
    private val onBuildFailure: (String) -> Unit,
    private val onWebBuildSuccess: (String) -> Unit,
    private val onAndroidBuildSuccess: () -> Unit,
    private val gitDelegate: GitDelegate
) {

    // AIDL Interface to the remote service
    private var buildService: IBuildService? = null
    private var isBuildServiceBound = false
    private var isServiceRegistered = false

    // Track the current build job to allow cancellation
    private var buildJob: Job? = null

    // --- Log Batching ---
    // Unbounded channel to receive logs from Binder threads without blocking.
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
     * AIDL Callback implementation passed to the BuildService.
     * Note: Methods here are called on a Binder Thread, not the Main Thread.
     */
    private val buildCallback = object : IBuildCallback.Stub() {
        override fun onLog(message: String) {
            // Push to channel for batch processing.
            logChannel.trySend(message)
        }

        override fun onSuccess(apkPath: String) {
            handleSuccess(apkPath)
        }

        override fun onFailure(log: String) {
            scope.launch {
                onLog("\n[IDE] Build Failed.\n")
                onOverlayLog("Build failed. Check global log.")
                onBuildFailure(log)
            }
        }
    }

    /**
     * Handles a successful build (Local or Remote).
     * @param apkPath The absolute path to the generated/downloaded APK (or artifact).
     */
    private fun handleSuccess(apkPath: String) {
        scope.launch {
            onLog("\n[IDE] Build successful: $apkPath\n")

            val type = ProjectType.fromString(settingsViewModel.getProjectType())

            if (type == ProjectType.WEB) {
                // Web Projects don't produce an APK, but an artifact path (index.html or dist dir)
                onLog("[IDE] Web Project ready. Loading WebView...\n")
                onWebBuildSuccess(apkPath)

                // Push to GitHub for deployment (Pages)
                if (!settingsViewModel.getGithubToken().isNullOrBlank()) {
                        onLog("[IDE] Triggering remote Web Build (Pushing to GitHub)...\n")
                        gitDelegate.push()
                }
            } else {
                // Android/Flutter Projects
                onOverlayLog("Build successful. Updating...")

                // Parse Source Map (R8 mapping.txt) for UI Inspection context
                val buildDir = File(apkPath).parentFile
                if (buildDir != null) {
                    val parser = SourceMapParser(buildDir)
                    onSourceMapUpdated(parser.parse())
                }

                // Notify MainViewModel to launch the app
                onAndroidBuildSuccess()
            }
        }
    }

    // --- Service Management ---

    fun bindService(context: Context) {
        if (isServiceRegistered) return
        val intent = Intent(context, BuildService::class.java)
        // BIND_AUTO_CREATE ensures the service is created if not running.
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
     * Starts the build process. Determines whether to run Local, Remote, or Race based on settings.
     */
    fun startBuild(projectDir: File? = null) {
        // Cancel any currently running build job to prevent conflicts.
        buildJob?.cancel()

        buildJob = scope.launch {
            val typeStr = settingsViewModel.getProjectType()
            val type = ProjectType.fromString(typeStr)

            // Special Case: Flutter builds currently only supported via Remote Build
            if (type == ProjectType.FLUTTER) {
                onLog("[IDE] Flutter Project detected. Triggering Remote Build (Pushing to GitHub)...\n")
                gitDelegate.push()
                return@launch
            }

            val localEnabled = settingsViewModel.isLocalBuildEnabled()
            val token = settingsViewModel.getGithubToken()
            val user = settingsViewModel.getGithubUser()

            val dir = projectDir ?: settingsViewModel.getProjectPath(settingsViewModel.getAppName() ?: "")

            // "Race" is possible if both Local is enabled and Remote credentials are present.
            val canRace = localEnabled && type == ProjectType.ANDROID && !token.isNullOrBlank() && !user.isNullOrBlank()

            // 1. Remote Only
            if (!localEnabled && type != ProjectType.WEB) {
                if (token.isNullOrBlank() || user.isNullOrBlank()) {
                     onLog("Error: Remote Build requires GitHub Token and User.\n")
                     return@launch
                }
                startRemoteOnlyBuild(dir, user!!, token!!)
                return@launch
            }

            // 2. Wait for Service Binding (Local/Race requires it)
            var attempts = 0
            while (!isBuildServiceBound && attempts < 10) {
                kotlinx.coroutines.delay(500)
                attempts++
            }

            if (!isBuildServiceBound) {
                onLog("Error: Build Service not bound.\n")
                return@launch
            }

            // 3. Race or Local
            if (canRace) {
                startRaceBuild(dir, user!!, token!!)
            } else {
                // Standard Local Build
                buildService?.startBuild(dir.absolutePath, buildCallback)
            }
        }
    }

    /**
     * Executes a Remote-Only build.
     * Commits changes, pushes to GitHub, and polls for the result.
     */
    private suspend fun startRemoteOnlyBuild(dir: File, user: String, token: String) {
         onLog("[IDE] Preparing Remote Build...\n")

         injectResizeableActivity(dir)

         // Commit & Push synchronously
         if (!gitDelegate.commit("Remote Build Trigger: ${System.currentTimeMillis()}")) {
             onLog("Error: Commit failed. Aborting remote build.\n")
             return
         }

         onLog("[IDE] Pushing to remote...\n")
         gitDelegate.push()

         val headSha = gitDelegate.getHeadSha()
         if (headSha == null) {
             onLog("Error: Could not determine HEAD SHA.\n")
             return
         }

         val api = GitHubApiClient.createService(token)
         val manager = RemoteBuildManager(application, api, token, user, dir.name, onLog)
         val apkPath = manager.pollAndDownload(headSha)
         if (apkPath != null) {
             handleSuccess(apkPath)
         } else {
             buildCallback.onFailure("Remote Build Failed.")
         }
    }

    /**
     * Executes the "Race to Build" strategy.
     * Starts both Local Build and Remote Build (Push + Poll).
     * The first one to finish wins and cancels the other.
     */
    private suspend fun startRaceBuild(dir: File, user: String, token: String) {
         onLog("[IDE] Starting 'Race to Build' (Local vs Remote)...\n")

         injectResizeableActivity(dir)

         // Commit synchronously to ensure HEAD represents the current state for both local and remote.
         if (!gitDelegate.commit("Race Build Trigger: ${System.currentTimeMillis()}")) {
             onLog("Error: Commit failed. Aborting race build.\n")
             return
         }

         val headSha = gitDelegate.getHeadSha()
         if (headSha == null) {
             onLog("Warning: Could not determine HEAD SHA. Fallback to Local Only.\n")
             buildService?.startBuild(dir.absolutePath, buildCallback)
             return
         }

         // Trigger Push (Async) so we don't block Local Build start.
         scope.launch {
             onLog("[Race] Pushing changes to remote...\n")
             gitDelegate.push()
         }

         val raceController = RaceController()

         // A. Start Remote Poller Job
         val remoteJob = scope.launch {
             val api = GitHubApiClient.createService(token)
             val manager = RemoteBuildManager(application, api, token, user, dir.name) { msg ->
                 onLog("[Remote] $msg")
             }
             val apk = manager.pollAndDownload(headSha)
             if (apk != null) {
                 if (raceController.tryWin()) {
                      onLog("[Race] Remote Build Won! Cancelling Local Build...\n")
                      buildService?.cancelBuild()
                      handleSuccess(apk)
                 }
             }
         }

         // B. Start Local Build
         val raceCallback = object : IBuildCallback.Stub() {
             override fun onLog(msg: String) = buildCallback.onLog(msg)
             override fun onSuccess(apk: String) {
                 if (raceController.tryWin()) {
                     onLog("[Race] Local Build Won! Cancelling Remote Polling...\n")
                     remoteJob.cancel()
                     handleSuccess(apk)
                 }
             }
             override fun onFailure(msg: String) {
                 onLog("[Race] Local Build Failed. Waiting for Remote...\n")
             }
         }

         buildService?.startBuild(dir.absolutePath, raceCallback)
    }

    /**
     * Injects `android:resizeableActivity="true"` into the AndroidManifest.
     * This is required for the app to run correctly inside the Virtual Display host,
     * which may have arbitrary dimensions.
     */
    private fun injectResizeableActivity(projectDir: File) {
        val manifestFile = File(projectDir, "app/src/main/AndroidManifest.xml")
        if (manifestFile.exists()) {
            var content = manifestFile.readText()
            if (!content.contains("android:resizeableActivity")) {
                content = content.replaceFirst("<application", "<application android:resizeableActivity=\"true\"")
                manifestFile.writeText(content)
            }
        }
    }

    /**
     * Helper class to ensure thread-safe "first-to-finish" logic for Race Build.
     */
    private class RaceController {
        private val winnerDeclared = AtomicBoolean(false)
        fun tryWin(): Boolean = winnerDeclared.compareAndSet(false, true)
    }

    /**
     * Triggers dependency download via the BuildService.
     */
    fun downloadDependencies(projectDir: File? = null) {
        scope.launch {
            if (isBuildServiceBound) {
                val dir = projectDir ?: settingsViewModel.getProjectPath(settingsViewModel.getAppName() ?: "")
                buildService?.downloadDependencies(dir.absolutePath, buildCallback)
            } else {
                onLog("Error: Build Service not bound.\n")
            }
        }
    }

    /**
     * Requests cancellation of the current build operation.
     */
    fun cancelBuild() {
        scope.launch {
            if (isBuildServiceBound) {
                try {
                    buildService?.cancelBuild()
                    onLog("Cancellation requested.\n")
                } catch (e: Exception) {
                    onLog("Error cancelling build: ${e.message}\n")
                }
            }
        }
    }
}
