package com.hereliesaz.ideaz.ui.delegates

import android.app.Application
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.IBinder
import android.util.Log
import com.hereliesaz.ideaz.IBuildCallback
import com.hereliesaz.ideaz.IBuildService
import com.hereliesaz.ideaz.services.BuildService
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File

class BuildDelegate(
    private val app: Application,
    private val aiDelegate: AIDelegate,
    private val onLog: (String) -> Unit
) {
    private var buildService: IBuildService? = null
    private val _buildStatus = MutableStateFlow("Idle")
    val buildStatus = _buildStatus.asStateFlow()

    // Internal log for AI context if needed, but primary output via callback
    private val _buildLog = MutableStateFlow("")
    val buildLog = _buildLog.asStateFlow()

    private val connection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            buildService = IBuildService.Stub.asInterface(service)
            _buildStatus.value = "Connected"
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            buildService = null
            _buildStatus.value = "Disconnected"
        }
    }

    private val buildCallback = object : IBuildCallback.Stub() {
        override fun onLog(message: String) {
            _buildLog.value += "$message\n"
            onLog(message)
        }

        override fun onSuccess(apkPath: String) {
            val msg = "Build Success: $apkPath"
            _buildLog.value += "$msg\n"
            onLog(msg)
            _buildStatus.value = "Success"
        }

        override fun onFailure(error: String) {
            val msg = "Build Failed: $error"
            _buildLog.value += "$msg\n"
            onLog(msg)
            _buildStatus.value = "Failed"

            // Forward failure log to AI for resolution if it's a compilation error
            // Simple heuristic: if it contains "error:" or "exception"
            if (error.contains("error", ignoreCase = true) ||
                error.contains("exception", ignoreCase = true) ||
                _buildLog.value.contains("error:", ignoreCase = true)) {
                 aiDelegate.startContextualAITask("Build Error: $error\nLog:\n${_buildLog.value}")
            }
        }
    }

    init {
        bindService()
    }

    private fun bindService() {
        val intent = Intent(app, BuildService::class.java)
        app.bindService(intent, connection, Context.BIND_AUTO_CREATE)
    }

    fun unbindService() {
        try {
            app.unbindService(connection)
        } catch (e: IllegalArgumentException) {
            // Ignore if not registered
        }
    }

    fun startBuild(projectPath: String) {
        if (buildService != null) {
            _buildLog.value = "Starting build for $projectPath...\n"
            _buildStatus.value = "Building"
            try {
                buildService?.startBuild(projectPath, buildCallback)
            } catch (e: Exception) {
                _buildLog.value += "Failed to start build: ${e.message}\n"
                _buildStatus.value = "Error"
            }
        } else {
            _buildLog.value += "Build Service not connected. Reconnecting...\n"
            bindService()
        }
    }
}
