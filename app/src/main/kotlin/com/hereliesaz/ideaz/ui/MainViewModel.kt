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
import com.hereliesaz.ideaz.api.Session
import com.hereliesaz.ideaz.api.UserMessaged
import com.hereliesaz.ideaz.git.GitManager
import com.hereliesaz.ideaz.services.BuildService
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.File
import com.hereliesaz.ideaz.models.SourceMapEntry
import com.hereliesaz.ideaz.utils.SourceMapParser
import com.hereliesaz.ideaz.api.Activity

class MainViewModel : ViewModel() {

    private val _buildLog = MutableStateFlow("")
    val buildLog = _buildLog.asStateFlow()

    private val _buildStatus = MutableStateFlow("Idle")
    val buildStatus = _buildStatus.asStateFlow()

    private val _aiStatus = MutableStateFlow("Idle")
    val aiStatus = _aiStatus.asStateFlow()

    private val _session = MutableStateFlow<Session?>(null)
    val session = _session.asStateFlow()

    private val _activities = MutableStateFlow<List<Activity>>(emptyList())
    val activities = _activities.asStateFlow()

    private val _sessions = MutableStateFlow<List<Session>>(emptyList())
    val sessions = _sessions.asStateFlow()

    private val _codeContent = MutableStateFlow("")
    val codeContent = _codeContent.asStateFlow()

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
        override fun onLog(message: String) {
            viewModelScope.launch {
                _buildLog.value += "$message\n"
            }
        }
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
                val sources = ApiClient.julesApiService.listSources()
                if (sources.isNotEmpty()) {
                    val source = sources.first()
                    val sourceContext = com.hereliesaz.ideaz.api.SourceContext(source.name, com.hereliesaz.ideaz.api.GitHubRepoContext("main"))
                    val session = com.hereliesaz.ideaz.api.Session("", "", prompt, sourceContext, "", false, "AUTO_CREATE_PR", "", "", "","", emptyList())
                    val response = ApiClient.julesApiService.createSession(session)
                    _session.value = response
                    _aiStatus.value = "Session created"
                    listActivities()
                } else {
                    _aiStatus.value = "No sources found"
                }
            } catch (e: Exception) {
                _aiStatus.value = "Error: ${e.message}"
            }
        }
    }

    fun applyPatch(context: Context) {
        viewModelScope.launch {
            _aiStatus.value = "Applying patch..."
            try {
                _activities.value.lastOrNull()?.artifacts?.firstOrNull()?.changeSet?.gitPatch?.unidiffPatch?.let {
                    val projectDir = context.filesDir.resolve("project")
                    val gitManager = GitManager(projectDir)
                    gitManager.applyPatch(it)
                    _aiStatus.value = "Patch applied, rebuilding..."
                    startBuild(context)
                }
            } catch (e: Exception) {
                _aiStatus.value = "Error applying patch: ${e.message}"
            }
        }
    }

    fun debugBuild() {
        viewModelScope.launch {
            _aiStatus.value = "Debugging..."
            try {
                session.value?.let {
                    val message = UserMessaged(buildLog.value)
                    val updatedSession = ApiClient.julesApiService.sendMessage(it.name, message)
                    _session.value = updatedSession
                    _aiStatus.value = "Debugging complete"
                    listActivities()
                }
            } catch (e: Exception) {
                _aiStatus.value = "Error debugging: ${e.message}"
            }
        }
    }

    fun listSessions() {
        viewModelScope.launch {
            try {
                _sessions.value = ApiClient.julesApiService.listSessions()
            } catch (e: Exception) {
                _aiStatus.value = "Error listing sessions: ${e.message}"
            }
        }
    }

    fun listActivities() {
        viewModelScope.launch {
            try {
                session.value?.let {
                    _activities.value = ApiClient.julesApiService.listActivities(it.name)
                }
            } catch (e: Exception) {
                _aiStatus.value = "Error listing activities: ${e.message}"
            }
        }
    }


    fun updateCodeContent(newContent: String) {
        _codeContent.value = newContent
    }

    private val inspectionReceiver = object : android.content.BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            val resourceId = intent?.getStringExtra("RESOURCE_ID")
            if (resourceId != null) {
                lookupSource(resourceId)
            }
        }
    }

    fun startInspection(context: Context) {
        androidx.localbroadcastmanager.content.LocalBroadcastManager.getInstance(context).registerReceiver(inspectionReceiver, android.content.IntentFilter("com.hereliesaz.ideaz.INSPECTION_RESULT"))
        context.startService(Intent(context, com.hereliesaz.ideaz.services.UIInspectionService::class.java))
    }

    fun stopInspection(context: Context) {
        androidx.localbroadcastmanager.content.LocalBroadcastManager.getInstance(context).unregisterReceiver(inspectionReceiver)
        context.stopService(Intent(context, com.hereliesaz.ideaz.services.UIInspectionService::class.java))
    }
}
