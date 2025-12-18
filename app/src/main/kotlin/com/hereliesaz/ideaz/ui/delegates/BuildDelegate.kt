package com.hereliesaz.ideaz.ui.delegates

import android.app.Application
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.IBinder
import com.hereliesaz.ideaz.IBuildCallback
import com.hereliesaz.ideaz.IBuildService
import com.hereliesaz.ideaz.models.SourceMapEntry
import com.hereliesaz.ideaz.services.BuildService
import com.hereliesaz.ideaz.ui.SettingsViewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class BuildDelegate(
    private val application: Application,
    private val settings: SettingsViewModel,
    private val scope: CoroutineScope,
    private val onBuildLog: (String) -> Unit,
    private val onAiLog: (String) -> Unit,
    private val onSourceMapReady: (Map<String, SourceMapEntry>) -> Unit,
    private val onBuildError: (String) -> Unit,
    private val onApkReady: (String) -> Unit,
    private val gitDelegate: GitDelegate
) {

    private var buildService: IBuildService? = null
    private var isBound = false

    private val connection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            buildService = IBuildService.Stub.asInterface(service)
            isBound = true
        }
        override fun onServiceDisconnected(name: ComponentName?) {
            buildService = null
            isBound = false
        }
    }

    init {
        bindService()
    }

    private fun bindService() {
        val intent = Intent(application, BuildService::class.java)
        application.bindService(intent, connection, Context.BIND_AUTO_CREATE)
    }

    fun unbindService(context: Context) {
        if (isBound) {
            context.unbindService(connection)
            isBound = false
        }
    }

    fun downloadDependencies() {
        // Triggered via MainViewModel usually
        val path = settings.getAppName()?.let { "/sdcard/IDEaz/Projects/$it" } ?: return
        if (isBound) {
            // Remote build handles this implicitly during GitHub workflow,
            // but we might want to trigger it explicitly.
            // For now, this is a placeholder.
            onBuildLog("Dependencies are managed remotely by GitHub Actions.")
        } else {
            bindService()
        }
    }

    fun startBuild(projectPath: String) {
        if (!isBound) {
            bindService()
            onBuildLog("Service binding... retry in a moment.")
            return
        }

        try {
            buildService?.startBuild(projectPath, object : IBuildCallback.Stub() {
                override fun onLog(message: String) {
                    scope.launch(Dispatchers.Main) { onBuildLog(message) }
                }

                override fun onSuccess(apkPath: String) {
                    scope.launch(Dispatchers.Main) {
                        onBuildLog("Build Complete: $apkPath")
                        onApkReady(apkPath)
                    }
                }

                override fun onFailure(error: String) {
                    scope.launch(Dispatchers.Main) {
                        onBuildLog("Build Failed: $error")
                        onBuildError(error) // Triggers AI Contextual Task
                    }
                }
            })
        } catch (e: Exception) {
            onBuildError("Service communication error: ${e.message}")
        }
    }
}