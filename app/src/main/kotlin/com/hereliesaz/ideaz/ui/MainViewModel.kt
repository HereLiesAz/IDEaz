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
import kotlinx.coroutines.delay

class MainViewModel : ViewModel() {

    private val _buildLog = MutableStateFlow("")
    val buildLog = _buildLog.asStateFlow()

    private val _buildStatus = MutableStateFlow("Idle")
    val buildStatus = _buildStatus.asStateFlow()

    private val _aiStatus = MutableStateFlow("Idle")
    val aiStatus
            = _aiStatus.asStateFlow()

    private val _session = MutableStateFlow<Session?>(null)
    val session = _session.asStateFlow()

    private val _activities = MutableStateFlow<List<Activity>>(emptyList())
    val activities = _activities.asStateFlow()

    private val _sessions = MutableStateFlow<List<Session>>(emptyList())
    val sessions = _sessions.asStateFlow()

    private val _codeContent = MutableStateFlow("")
    val codeContent = _codeContent.asStateFlow()

    private val _selectedFile = MutableStateFlow<String?>(null)
    val selectedFile = _selectedFile.asStateFlow()

    private val _selectedLine = MutableStateFlow<Int?>(null)
    val selectedLine = _selectedLine.asStateFlow()

    private val _selectedCodeSnippet = MutableStateFlow<String?>(null) // Will hold the snippet
    val selectedCodeSnippet = _selectedCodeSnippet.asStateFlow()

    private var buildService: IBuildService?
            = null
    private var isBuildServiceBound = false

    // Context is needed for applyPatch and startBuild
    private var appContext: Context? = null

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
            viewModelScope.launch {
                try {
                    val file = File(entry.file)
                    if (file.exists()) {
                        val lines = file.readLines()
                        val lineIndex = entry.line - 1 // 0-indexed

                        if (lineIndex >= 0 && lineIndex < lines.size) {
                            val snippet = lines[lineIndex].trim() // Just get the single line for now
                            _selectedFile.value = entry.file
                            _selectedLine.value = entry.line
                            _selectedCodeSnippet.value = snippet
                            _buildLog.value += "\nContext Found:\nFile: ${entry.file}\nLine: ${entry.line}\nSnippet: $snippet"
                        } else {
                            _buildLog.value += "\nError: Line number ${entry.line} is out of bounds for ${entry.file}"
                        }
                    } else {
                        _buildLog.value += "\nError: Source file not found at ${entry.file}"
                    }
                } catch (e: Exception) {
                    _buildLog.value += "\nError reading source file: ${e.message}"
                }
            }
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
                _aiStatus.value = "Idle" // Loop is complete


                val buildDir = File(apkPath).parentFile
                if (buildDir != null) {
                    val parser = SourceMapParser(buildDir)
                    sourceMap = parser.parse()
                    _buildLog.value += "\nSource map loaded.\nFound ${sourceMap.size} entries."
                    lookupSource("sample_text")
                }
            }
        }

        override fun onFailure(log: String) {
            viewModelScope.launch {
                _buildLog.value += "\nBuild failed:\n$log"
                _buildStatus.value = "Build Failed"
                _aiStatus.value = "Build failed, asking AI to debug..."
                // AUTOMATION: Automatically call debugBuild on failure
                debugBuild()
            }
        }
    }

    fun bindService(context: Context) {
        appContext = context.applicationContext // Store context
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
        appContext = null // Clear context
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
        val contextFile = _selectedFile.value
        val contextLine = _selectedLine.value
        val contextSnippet = _selectedCodeSnippet.value

        val richPrompt = if (contextFile != null && contextLine != null && contextSnippet != null) {
            // Construct the rich prompt [cite: 459, 740-745]
            """
            User Request: "$prompt"
            
            Context:
            File: $contextFile
            Line: $contextLine
            Code Snippet (at line $contextLine):
            $contextSnippet
            
            Please generate a git patch to apply this change.
            """.trimIndent()
        } else {
            // This is an initial prompt, no context is available
            _buildLog.value += "\nSending initial prompt..."
            prompt
        }

        _buildLog.value += "\nSending rich prompt:\n$richPrompt" // Log the rich prompt

        viewModelScope.launch {
            _aiStatus.value = "Sending..."
            try {
                val sources = ApiClient.julesApiService.listSources()
                if (sources.isNotEmpty()) {
                    val source = sources.first()
                    val sourceContext = com.hereliesaz.ideaz.api.SourceContext(source.name, com.hereliesaz.ideaz.api.GitHubRepoContext("main"))
                    // Use richPrompt instead of the raw prompt
                    val session = com.hereliesaz.ideaz.api.Session("", "", richPrompt, sourceContext, "", false, "AUTO_CREATE_PR", "", "", "","", emptyList())
                    val response = ApiClient.julesApiService.createSession(session)
                    _session.value = response
                    _aiStatus.value = "Session created. Waiting for patch..."
                    // AUTOMATION: Start polling for the patch
                    pollForPatch(response.name)
                } else {
                    _aiStatus.value = "No sources found"
                }
            } catch (e: Exception) {
                _aiStatus.value = "Error: ${e.message}"
            }
        }
    }

    private fun pollForPatch(sessionName: String, attempts: Int = 0) {
        viewModelScope.launch {
            if (attempts > 20) { // 20 attempts * 5s = 100s timeout
                _aiStatus.value = "Error: Timed out waiting for AI patch."
                return@launch
            }

            try {
                _aiStatus.value = "Polling for patch... (Attempt ${attempts + 1})"
                val activities = ApiClient.julesApiService.listActivities(sessionName)
                val lastPatch = activities.lastOrNull()?.artifacts?.firstOrNull()?.changeSet?.gitPatch

                if (lastPatch != null) {
                    _aiStatus.value = "Patch found! Applying..."
                    _activities.value = activities // Update activities to hold the patch
                    appContext?.let { applyPatch(it) }
                } else {
                    // Not found, poll again
                    delay(5000)
                    pollForPatch(sessionName, attempts + 1)
                }
            } catch (e: Exception) {
                _aiStatus.value = "Error polling for patch: ${e.message}"
            }
        }
    }

    fun applyPatch(context: Context) {
        viewModelScope.launch {
            _aiStatus.value = "Applying patch..."
            try {
                val patch = _activities.value.lastOrNull()?.artifacts?.firstOrNull()?.changeSet?.gitPatch?.unidiffPatch
                if (patch != null) {
                    val projectDir = context.filesDir.resolve("project")
                    val gitManager = GitManager(projectDir)
                    gitManager.applyPatch(patch)
                    _aiStatus.value = "Patch applied, rebuilding..."
                    // AUTOMATION: Automatically start build after patch
                    startBuild(context)
                } else {
                    _aiStatus.value = "Error: Apply patch called but no patch found."
                }
            } catch (e: Exception) {
                _aiStatus.value = "Error applying patch: ${e.message}"
            }
        }
    }

    fun debugBuild() {
        viewModelScope.launch {
            _aiStatus.value = "Debugging build failure..."
            try {
                session.value?.let {
                    val message = UserMessaged(buildLog.value)
                    val updatedSession = ApiClient.julesApiService.sendMessage(it.name, message)
                    _session.value = updatedSession
                    _aiStatus.value = "Debug info sent. Waiting for new patch..."
                    // AUTOMATION: Start polling for the fix
                    pollForPatch(it.name)
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

    private val inspectionReceiver
            = object : android.content.BroadcastReceiver() {
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