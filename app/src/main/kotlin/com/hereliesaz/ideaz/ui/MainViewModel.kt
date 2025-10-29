package com.hereliesaz.ideaz.ui

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.IBinder
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hereliesaz.ideaz.IBuildCallback
import com.hereliesaz.ideaz.IBuildService
import com.hereliesaz.ideaz.services.BuildService
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class MainViewModel : ViewModel() {

    private val _buildLog = MutableStateFlow("")
    val buildLog = _buildLog.asStateFlow()

    private val _buildStatus = MutableStateFlow("Idle")
    val buildStatus = _buildStatus.asStateFlow()

    private var buildService: IBuildService? = null
    private var isServiceBound = false

    private val serviceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            buildService = IBuildService.Stub.asInterface(service)
            isServiceBound = true
            _buildStatus.value = "Service Connected"
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            buildService = null
            isServiceBound = false
            _buildStatus.value = "Service Disconnected"
        }
    }

    private val buildCallback = object : IBuildCallback.Stub() {
        override fun onSuccess(apkPath: String) {
            viewModelScope.launch {
                _buildLog.value += "\nBuild successful: $apkPath"
                _buildStatus.value = "Build Successful"
            }
        }

        override fun onFailure(log: String) {
            viewModelScope.launch {
                _buildLog.value += "\nBuild failed:\n$log"
                _buildStatus.value = "Build Failed"
            }
        }
    }

    fun bindService(context: Context) {
        val intent = Intent("com.hereliesaz.ideaz.BUILD_SERVICE")
        intent.component = ComponentName(context, BuildService::class.java)
        context.bindService(intent, serviceConnection, Context.BIND_AUTO_CREATE)
    }

    fun unbindService(context: Context) {
        if (isServiceBound) {
            context.unbindService(serviceConnection)
            isServiceBound = false
        }
    }

    fun startBuild(context: Context) {
        if (isServiceBound) {
            viewModelScope.launch {
                _buildStatus.value = "Building..."
                _buildLog.value = ""
                // Extract project from assets and start build
                // For now, we'll just use a placeholder path
                val projectPath = extractProject(context)
                buildService?.startBuild(projectPath, buildCallback)
            }
        } else {
            _buildStatus.value = "Service not bound"
        }
    }

    private fun extractProject(context: Context): String {
        val projectDir = context.filesDir.resolve("project")
        if (projectDir.exists()) {
            projectDir.deleteRecursively()
        }
        projectDir.mkdirs()

        context.assets.list("project")?.forEach {
            copyAsset(context, "project/$it", projectDir.resolve(it).absolutePath)
        }
        return projectDir.absolutePath
    }

    private fun copyAsset(context: Context, assetPath: String, destPath: String) {
        val assetManager = context.assets
        val files = assetManager.list(assetPath)
        if (files.isNullOrEmpty()) {
            assetManager.open(assetPath).use { input ->
                java.io.FileOutputStream(destPath).use { output ->
                    input.copyTo(output)
                }
            }
        } else {
            val dir = java.io.File(destPath)
            if (!dir.exists()) {
                dir.mkdirs()
            }
            files.forEach {
                copyAsset(context, "$assetPath/$it", "$destPath/$it")
            }
        }
    }
}
