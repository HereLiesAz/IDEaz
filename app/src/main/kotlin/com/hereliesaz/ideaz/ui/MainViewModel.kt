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
import com.hereliesaz.ideaz.api.ApiClient
import com.hereliesaz.ideaz.git.GitManager
import com.hereliesaz.ideaz.services.BuildService
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.File
import com.hereliesaz.ideaz.models.SourceMapEntry
import com.hereliesaz.ideaz.utils.SourceMapParser

class MainViewModel : ViewModel() {

    private val _buildLog = MutableStateFlow("")
    val buildLog = _buildLog.asStateFlow()

    private val _buildStatus = MutableStateFlow("Idle")
    val buildStatus = _buildStatus.asStateFlow()

    private val _aiStatus = MutableStateFlow("Idle")
    val aiStatus = _aiStatus.asStateFlow()

    private val _patch = MutableStateFlow<String?>(null)
    val patch = _patch.asStateFlow()

    private var buildService: IBuildService? = null
    private var isBuildServiceBound = false

    private val buildServiceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            buildService = IBuildService.Stub.asInterface(service)
            isBuildServiceBound = true
            _buildStatus.value = "Build Service Connected"
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            buildService = null
            isBuildServiceBound = false
            _buildStatus.value = "Build Service Disconnected"
        }
    }

    fun lookupSource(id: String) {
        val entry = sourceMap[id]
        if (entry != null) {
            _buildLog.value += "\nSource for $id found at ${entry.file}:${entry.line}"
        } else {
            _buildLog.value += "\nSource for $id not found"
        }
    }

    private var sourceMap: Map<String, SourceMapEntry> = emptyMap()

    private val buildCallback = object : IBuildCallback.Stub() {
        override fun onSuccess(apkPath: String) {
            viewModelScope.launch {
                _buildLog.value += "\nBuild successful: $apkPath"
                _buildStatus.value = "Build Successful"

                val buildDir = File(apkPath).parentFile
                if (buildDir != null) {
                    val parser = SourceMapParser(buildDir)
                    sourceMap = parser.parse()
                    _buildLog.value += "\nSource map loaded. Found ${sourceMap.size} entries."
                    lookupSource("sample_text")
                }
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
        Intent("com.hereliesaz.ideaz.BUILD_SERVICE").also { intent ->
            intent.component = ComponentName(context, BuildService::class.java)
            context.bindService(intent, buildServiceConnection, Context.BIND_AUTO_CREATE)
        }
    }

    fun unbindService(context: Context) {
        if (isBuildServiceBound) {
            context.unbindService(buildServiceConnection)
            isBuildServiceBound = false
        }
    }

    fun startBuild(context: Context) {
        if (isBuildServiceBound) {
            viewModelScope.launch {
                _buildStatus.value = "Building..."
                _buildLog.value = ""

                val projectDir = File(extractProject(context))
                buildService?.startBuild(projectDir.absolutePath, buildCallback)
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

    fun sendPrompt(prompt: String) {
        viewModelScope.launch {
            _aiStatus.value = "Sending..."
            try {
                val response = ApiClient.julesApiService.sendPrompt(prompt)
                _patch.value = response
                _aiStatus.value = "Patch received"
            } catch (e: Exception) {
                _aiStatus.value = "Error: ${e.message}"
            }
        }
    }

    fun applyPatch(context: Context) {
        viewModelScope.launch {
            _aiStatus.value = "Applying patch..."
            try {
                val projectDir = context.filesDir.resolve("project")
                val gitManager = GitManager(projectDir)
                gitManager.applyPatch(patch.value!!)
                _aiStatus.value = "Patch applied"
            } catch (e: Exception) {
                _aiStatus.value = "Error: ${e.message}"
            }
        }
    }
}
