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
import kotlinx.coroutines.launch
import java.io.File

/**
 * Manages interactions with the background BuildService.
 * Handles binding, unbinding, and invoking build operations via AIDL.
 *
 * @param application The Application context.
 * @param settingsViewModel ViewModel to access project settings.
 * @param scope CoroutineScope for handling callbacks on the main thread.
 * @param onLog Callback for general build logs.
 * @param onOverlayLog Callback for high-priority overlay logs.
 * @param onSourceMapUpdated Callback when a new SourceMap is generated after a successful build.
 * @param onWebBuildFailure Callback to trigger AI assistance on web build failures.
 * @param onWebBuildSuccess Callback when a web build succeeds (to load the WebView).
 * @param gitDelegate Delegate to handle Git operations (e.g., pushing after a web build).
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
            scope.launch {
                onLog("$message\n")
                buildService?.updateNotification(message)
            }
        }

        override fun onSuccess(apkPath: String) {
            scope.launch {
                onLog("\n[IDE] Build successful: $apkPath\n")

                val type = ProjectType.fromString(settingsViewModel.getProjectType())

                // Check if this is a web build
                if (type == ProjectType.WEB) {
                    onLog("[IDE] Web Project ready. Loading WebView...\n")
                    onWebBuildSuccess(apkPath)

                    // Web Projects: Push to GitHub if enabled to trigger remote build/deploy
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

    /**
     * Binds the BuildService to the application context.
     * Ensures the service is started and connected.
     */
    fun bindService(context: Context) {
        if (isServiceRegistered) return
        val intent = Intent(context, BuildService::class.java)
        context.applicationContext.bindService(intent, buildServiceConnection, Context.BIND_AUTO_CREATE)
        isServiceRegistered = true
    }

    /**
     * Unbinds the BuildService.
     * Should be called when the ViewModel is cleared.
     */
    fun unbindService(context: Context) {
        if (isServiceRegistered) {
            try {
                context.applicationContext.unbindService(buildServiceConnection)
            } catch (e: Exception) {
            }
            isServiceRegistered = false
        }
    }

    /**
     * Initiates a build process.
     * Checks if local build is enabled and if the service is bound.
     * @param projectDir Optional specific project directory. Defaults to current project.
     */
    fun startBuild(projectDir: File? = null) {
        scope.launch {
            val typeStr = settingsViewModel.getProjectType()
            val type = ProjectType.fromString(typeStr)

            if (type != ProjectType.WEB && !settingsViewModel.isLocalBuildEnabled()) {
                onLog("[INFO] Local build disabled. Using GitHub Action (Remote).\n")
                // Proceed to call buildService, which now handles remote build logic
            }

            if (type == ProjectType.WEB) {
                onLog("[IDE] Web Project: Pulling latest changes...\n")
                // In a real scenario, we might await a git pull here
            }

            // Retry binding logic
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

    /**
     * Initiates dependency download process via the BuildService.
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
}
