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
 * Manages interactions with the background BuildService.
 * Handles binding, unbinding, and invoking build operations via AIDL.
 *
 * This version implements Log Batching and off-main-thread processing to prevent ANRs.
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

    private var buildService: IBuildService? = null
    private var isBuildServiceBound = false
    private var isServiceRegistered = false

    private var buildJob: Job? = null

    // Log Batching
    private val logChannel = Channel<String>(Channel.UNLIMITED)

    init {
        // Collect logs in a single coroutine to avoid flood.
        // Use Dispatchers.Default to keep heavy string processing (splitting, filtering) off the Main thread.
        scope.launch(Dispatchers.Default) {
            val batch = StringBuilder()
            var lastUpdate = 0L
            
            logChannel.consumeAsFlow().collect { msg ->
                batch.append(msg).append("\n")
                val now = System.currentTimeMillis()
                // Update UI state at most every 200ms to reduce recomposition pressure
                if (now - lastUpdate > 200) {
                    val batchStr = batch.toString()
                    batch.setLength(0)
                    lastUpdate = now
                    onLog(batchStr)
                }
            }
        }
    }

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

    private val buildCallback = object : IBuildCallback.Stub() {
        override fun onLog(message: String) {
            // Push to channel for background processing.
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

    private fun handleSuccess(apkPath: String) {
        scope.launch {
            onLog("\n[IDE] Build successful: $apkPath\n")

            val type = ProjectType.fromString(settingsViewModel.getProjectType())

            if (type == ProjectType.WEB) {
                onLog("[IDE] Web Project ready. Loading WebView...\n")
                onWebBuildSuccess(apkPath)

                if (!settingsViewModel.getGithubToken().isNullOrBlank()) {
                        onLog("[IDE] Triggering remote Web Build (Pushing to GitHub)...\n")
                        gitDelegate.push()
                }
            } else {
                onOverlayLog("Build successful. Updating...")
                application.sendBroadcast(Intent("com.hereliesaz.ideaz.SHOW_UPDATE_POPUP"))
                val buildDir = File(apkPath).parentFile
                if (buildDir != null) {
                    val parser = SourceMapParser(buildDir)
                    onSourceMapUpdated(parser.parse())
                }
                // Automatically switch to App View
                onAndroidBuildSuccess()
            }
        }
    }

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
            }
            isServiceRegistered = false
        }
    }

    fun startBuild(projectDir: File? = null) {
        // Cancel any previous build job
        buildJob?.cancel()

        buildJob = scope.launch {
            val typeStr = settingsViewModel.getProjectType()
            val type = ProjectType.fromString(typeStr)

            // Flutter Special Case
            if (type == ProjectType.FLUTTER) {
                onLog("[IDE] Flutter Project detected. Triggering Remote Build (Pushing to GitHub)...\n")
                gitDelegate.push()
                return@launch
            }

            val localEnabled = settingsViewModel.isLocalBuildEnabled()
            val token = settingsViewModel.getGithubToken()
            val user = settingsViewModel.getGithubUser()

            val dir = projectDir ?: settingsViewModel.getProjectPath(settingsViewModel.getAppName() ?: "")

            val canRace = localEnabled && type == ProjectType.ANDROID && !token.isNullOrBlank() && !user.isNullOrBlank()

            if (!localEnabled && type != ProjectType.WEB) {
                if (token.isNullOrBlank() || user.isNullOrBlank()) {
                     onLog("Error: Remote Build requires GitHub Token and User.\n")
                     return@launch
                }
                // Remote Only
                startRemoteOnlyBuild(dir, user!!, token!!)
                return@launch
            }

            // Ensure Service is bound
            var attempts = 0
            while (!isBuildServiceBound && attempts < 10) {
                kotlinx.coroutines.delay(500)
                attempts++
            }

            if (!isBuildServiceBound) {
                onLog("Error: Build Service not bound.\n")
                return@launch
            }

            if (canRace) {
                startRaceBuild(dir, user!!, token!!)
            } else {
                // Standard Local
                buildService?.startBuild(dir.absolutePath, buildCallback)
            }
        }
    }

    private suspend fun startRemoteOnlyBuild(dir: File, user: String, token: String) {
         onLog("[IDE] Preparing Remote Build...\n")

         injectResizeableActivity(dir)

         // Commit & Push synchronously
         gitDelegate.commit("Remote Build Trigger: ${System.currentTimeMillis()}")

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

    private suspend fun startRaceBuild(dir: File, user: String, token: String) {
         onLog("[IDE] Starting 'Race to Build' (Local vs Remote)...\n")

         injectResizeableActivity(dir)

         // Commit synchronously to ensure HEAD represents current state
         gitDelegate.commit("Race Build Trigger: ${System.currentTimeMillis()}")

         val headSha = gitDelegate.getHeadSha()
         if (headSha == null) {
             onLog("Warning: Could not determine HEAD SHA. Fallback to Local Only.\n")
             buildService?.startBuild(dir.absolutePath, buildCallback)
             return
         }

         // Trigger Push (Async)
         scope.launch {
             onLog("[Race] Pushing changes to remote...\n")
             gitDelegate.push()
         }

         val raceController = RaceController()

         // Launch Remote Poller Job
         val remoteJob = scope.launch {
             // Polling needs to be robust against "push not finished yet"
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

         // Start Local Build
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

    private class RaceController {
        private val winnerDeclared = AtomicBoolean(false)
        fun tryWin(): Boolean = winnerDeclared.compareAndSet(false, true)
    }

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
}
