package com.hereliesaz.ideaz.ui.delegates

import android.app.Application
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.IBinder
import com.hereliesaz.ideaz.IBuildCallback
import com.hereliesaz.ideaz.IBuildService
import com.hereliesaz.ideaz.models.ProjectType
import com.hereliesaz.ideaz.services.BuildService
import com.hereliesaz.ideaz.ui.SettingsViewModel
import com.hereliesaz.ideaz.utils.SourceMapParser
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.consumeAsFlow
import kotlinx.coroutines.launch
import java.io.File

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
    private val gitDelegate: GitDelegate
) {

    private var buildService: IBuildService? = null
    private var isBuildServiceBound = false
    private var isServiceRegistered = false

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
            // DO NOT launch coroutines on Dispatchers.Main here, as it causes ANRs during verbose builds.
            logChannel.trySend(message)
            
            // Redundant notification update removed. BuildService now handles this internally.
        }

        override fun onSuccess(apkPath: String) {
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
                }
            }
        }

        override fun onFailure(log: String) {
            scope.launch {
                onLog("\n[IDE] Build Failed.\n")
                onOverlayLog("Build failed. Check global log.")
                onBuildFailure(log)
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
        scope.launch {
            val typeStr = settingsViewModel.getProjectType()
            val type = ProjectType.fromString(typeStr)

            if (type != ProjectType.WEB && !settingsViewModel.isLocalBuildEnabled()) {
                onLog("[INFO] Local build disabled. Using GitHub Action (Remote).\n")
            }

            var attempts = 0
            while (!isBuildServiceBound && attempts < 10) {
                kotlinx.coroutines.delay(500)
                attempts++
            }

            if (isBuildServiceBound) {
                val dir = projectDir ?: settingsViewModel.getProjectPath(settingsViewModel.getAppName() ?: "")
                buildService?.startBuild(dir.absolutePath, buildCallback)
            } else {
                onLog("Error: Build Service not bound after waiting.\n")
            }
        }
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
