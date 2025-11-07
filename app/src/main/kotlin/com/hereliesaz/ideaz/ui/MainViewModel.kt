package com.hereliesaz.ideaz.ui

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.IBinder
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hereliesaz.ideaz.git.GitManager
import com.hereliesaz.ideaz.services.BuildService
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.File
import com.hereliesaz.ideaz.models.SourceMapEntry
import com.hereliesaz.ideaz.utils.SourceMapParser
import com.hereliesaz.ideaz.api.Activity
import com.hereliesaz.ideaz.api.CreateSessionRequest
import com.hereliesaz.ideaz.api.Source
import com.hereliesaz.ideaz.services.UIInspectionService
import kotlinx.coroutines.delay
import androidx.preference.PreferenceManager
import com.hereliesaz.ideaz.IBuildCallback
import com.hereliesaz.ideaz.api.GitHubRepoContext
import com.hereliesaz.ideaz.api.SourceContext
import com.hereliesaz.ideaz.IBuildService
import com.hereliesaz.ideaz.api.ApiClient
import com.hereliesaz.ideaz.api.Session
import com.hereliesaz.ideaz.api.UserMessaged
import java.io.FileOutputStream
import java.io.IOException

class MainViewModel : ViewModel() {

    // --- Global Build Log ---
    private val _buildLog = MutableStateFlow("")
    val buildLog = _buildLog.asStateFlow() // For build logs & contextless AI chat

    // --- Service Binders ---
    private var buildService: IBuildService? = null
    private var isBuildServiceBound = false

    // --- Global State ---
    private val _buildStatus = MutableStateFlow("Idle")
    val buildStatus = _buildStatus.asStateFlow()

    private val _aiStatus = MutableStateFlow("Idle") // For contextless AI
    val aiStatus = _aiStatus.asStateFlow()

    // --- Session / API State ---
    private val _session = MutableStateFlow<Session?>(null) // For contextless AI
    val session = _session.asStateFlow()

    private val _activities = MutableStateFlow<List<Activity>>(emptyList())
    val activities = _activities.asStateFlow()

    private val _sessions = MutableStateFlow<List<Session>>(emptyList())
    val sessions = _sessions.asStateFlow()

    private val _sources = MutableStateFlow<List<Source>>(emptyList())
    val sources = _sources.asStateFlow()

    // --- Code/Source Map State ---
    private val _codeContent = MutableStateFlow("")
    val codeContent = _codeContent.asStateFlow()

    private val _selectedFile = MutableStateFlow<String?>(null)
    val selectedFile = _selectedFile.asStateFlow()

    private val _selectedLine = MutableStateFlow<Int?>(null)
    val selectedLine = _selectedLine.asStateFlow()

    private val _selectedCodeSnippet = MutableStateFlow<String?>(null)
    val selectedCodeSnippet = _selectedCodeSnippet.asStateFlow()

    private var sourceMap: Map<String, SourceMapEntry> = emptyMap()
    private var appContext: Context? = null


    // --- Build Service Connection ---
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

    // --- Build Callback ---
    private val buildCallback = object : IBuildCallback.Stub() {
        override fun onLog(message: String) {
            viewModelScope.launch {
                _buildLog.value += "$message\n" // Build logs go to global log
            }
        }
        override fun onSuccess(apkPath: String) {
            viewModelScope.launch {
                _buildLog.value += "\nBuild successful: $apkPath"
                _buildStatus.value = "Build Successful"
                _aiStatus.value = "Idle" // Global AI is idle

                // Contextual AI task is finished
                logToOverlay("Build successful. Task finished.")
                sendOverlayBroadcast(Intent("com.hereliesaz.ideaz.TASK_FINISHED"))

                val buildDir = File(apkPath).parentFile
                if (buildDir != null) {
                    val parser = SourceMapParser(buildDir)
                    sourceMap = parser.parse()
                    _buildLog.value += "\nSource map loaded. Found ${sourceMap.size} entries."
                }
            }
        }

        override fun onFailure(log: String) {
            viewModelScope.launch {
                _buildLog.value += "\nBuild failed:\n$log"
                _buildStatus.value = "Build Failed"

                // Check which AI flow is active. This is tricky.
                // For now, assume build failures are debugged globally.
                logToOverlay("Build failed. See global log to debug.")
                _aiStatus.value = "Build failed, asking AI to debug..."
                debugBuild() // Global debug
            }
        }
    }

    // --- Service Binding ---
    fun bindBuildService(context: Context) {
        appContext = context.applicationContext // Store context

        // Bind Build Service
        Intent("com.hereliesaz.ideaz.BUILD_SERVICE").also { intent ->
            intent.component = ComponentName(context, BuildService::class.java)
            context.bindService(intent, buildServiceConnection, Context.BIND_AUTO_CREATE)
        }
        // Also load sources when service is bound
        loadSources()
    }

    fun unbindBuildService(context: Context) {
        if (isBuildServiceBound) {
            context.unbindService(buildServiceConnection)
            isBuildServiceBound = false
        }
        appContext = null // Clear context
    }

    // --- Inspection Logic ---

    /**
     * Called by MainActivity when it receives "com.hereliesaz.ideaz.INSPECTION_RESULT"
     */
    fun onInspectionResult(resourceId: String) {
        // Inspection found a node.
        // 1. Look up its source
        lookupSource(resourceId)
        // 2. Tell the UIInspectionService to show its prompt
        sendOverlayBroadcast(Intent("com.hereliesaz.ideaz.SHOW_PROMPT").apply {
            putExtra("RESOURCE_ID", resourceId)
        })
    }

    /**
     * Called by MainActivity when it receives "com.hereliesaz.ideaz.PROMPT_SUBMITTED"
     */
    fun onContextualPromptSubmitted(resourceId: String, prompt: String) {
        Log.d("MainViewModel", "Contextual prompt submitted for $resourceId: $prompt")
        // A contextual prompt was submitted, start a new AI task for it
        startContextualAITask(resourceId, prompt)
    }

    fun startInspection(context: Context) {
        // We no longer register/unregister the receiver here, MainActivity does it.
        context.startService(Intent(context, com.hereliesaz.ideaz.services.UIInspectionService::class.java))
    }

    fun stopInspection(context: Context) {
        context.stopService(Intent(context, com.hereliesaz.ideaz.services.UIInspectionService::class.java))
        // Also tell the service to hide any UI it might be showing
        sendOverlayBroadcast(Intent("com.hereliesaz.ideaz.TASK_FINISHED"))
    }

    fun lookupSource(id: String) {
        val entry = sourceMap[id]
        if (entry != null) {
            // Log to global log
            _buildLog.value += "\nSource for $id found at ${entry.file}:${entry.line}"
            viewModelScope.launch {
                try {
                    val file = File(entry.file)
                    if (file.exists()) {
                        val lines = file.readLines()
                        val lineIndex = entry.line - 1 // 0-indexed

                        if (lineIndex >= 0 && lineIndex < lines.size) {
                            val snippet = lines[lineIndex].trim() // Just get the single line
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


    // --- Build Logic ---
    fun startBuild(context: Context) {
        if (isBuildServiceBound) {
            viewModelScope.launch {
                _buildStatus.value = "Building..."
                _buildLog.value = "" // Clear global log
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
        try {
            val files = assetManager.list(assetPath)
            if (files.isNullOrEmpty() || files.isEmpty()) {
                // It's a file
                assetManager.open(assetPath).use { input ->
                    FileOutputStream(destPath).use { output ->
                        input.copyTo(output)
                    }
                }
            } else {
                // It's a directory
                val dir = File(destPath)
                if (!dir.exists()) {
                    dir.mkdirs()
                }
                files.forEach {
                    copyAsset(context, "$assetPath/$it", "$destPath/$it")
                }
            }
        } catch (e: IOException) {
            // This can happen if assetPath is a file but list() was called, or vice-versa
            Log.e("MainViewModel", "Failed to copy asset: $assetPath", e)
            // Try as file if list fails
            try {
                assetManager.open(assetPath).use { input ->
                    FileOutputStream(destPath).use { output ->
                        input.copyTo(output)
                    }
                }
            } catch (e2: IOException) {
                Log.e("MainViewModel", "Failed to copy asset as file: $assetPath", e2)
            }
        }
    }

    // --- CONTEXTLESS AI (Global Log) ---
    /**
     * Sends a "contextless" prompt (from the bottom sheet) to the AI.
     * Logs are streamed to the global _buildLog.
     */
    fun sendPrompt(prompt: String) {
        // This is a contextless prompt, so context is null
        _selectedFile.value = null
        _selectedLine.value = null
        _selectedCodeSnippet.value = null

        _buildLog.value += "\nSending contextless prompt: $prompt"

        viewModelScope.launch {
            _aiStatus.value = "Sending..."
            try {
                val sessionRequest = createSessionRequest(prompt)
                if (sessionRequest == null) {
                    _aiStatus.value = "Error: Project settings incomplete."
                    _buildLog.value += "\nPlease go to Project settings and set App Name and GitHub User."
                    return@launch
                }

                val response = ApiClient.julesApiService.createSession(sessionRequest)
                _session.value = response // Store as the "global" session
                _aiStatus.value = "Session created. Waiting for patch..."
                // Poll and log to _buildLog
                pollForPatch(response.name, _buildLog)

            } catch (e: Exception) {
                _aiStatus.value = "Error: ${e.message}"
                _buildLog.value += "\nError: ${e.message}"
            }
        }
    }

    // --- CONTEXTUAL AI (Overlay Log) ---
    /**
     * Starts a "contextual" AI task (from the overlay prompt).
     * Logs are streamed to the aiOverlayService.
     */
    private fun startContextualAITask(resourceId: String, prompt: String) {
        // We have context from the inspection
        val contextFile = _selectedFile.value
        val contextLine = _selectedLine.value
        val contextSnippet = _selectedCodeSnippet.value

        val richPrompt = if (contextFile != null && contextLine != null && contextSnippet != null) {
            """
            User Request: "$prompt"
            Context (for element $resourceId):
            File: $contextFile
            Line: $contextLine
            Code Snippet (at line $contextLine):
            $contextSnippet
            
            Please generate a git patch to apply this change.
            """.trimIndent()
        } else {
            // Context lookup failed, but we still have the resourceId
            "User Request: \"$prompt\" for element with resource ID \"$resourceId\"."
        }

        // Log to overlay
        logToOverlay("Sending prompt to AI...")

        viewModelScope.launch {
            try {
                val sessionRequest = createSessionRequest(richPrompt)
                if (sessionRequest == null) {
                    logToOverlay("Error: Project settings incomplete. Go to main app.")
                    logToOverlay("Task Finished.") // Manually finish
                    return@launch
                }

                val response = ApiClient.julesApiService.createSession(sessionRequest)
                logToOverlay("Session created. Waiting for patch...")
                // Poll and log to overlay
                pollForPatch(response.name, "OVERLAY") // Use a string to signify overlay

            } catch (e: Exception) {
                logToOverlay("Error: ${e.message}")
                logToOverlay("Task Finished.") // Manually finish
            }
        }
    }

    // --- AI Helper Functions ---

    /** Creates a session request object from current project settings */
    private fun createSessionRequest(prompt: String): CreateSessionRequest? {
        val prefs = PreferenceManager.getDefaultSharedPreferences(appContext!!)
        val appName = prefs.getString(SettingsViewModel.KEY_APP_NAME, null)
        val githubUser = prefs.getString(SettingsViewModel.KEY_GITHUB_USER, null)
        val branchName = prefs.getString(SettingsViewModel.KEY_BRANCH_NAME, "main")!!

        if (appName == null || githubUser == null) {
            return null
        }

        val sourceName = "sources/github.com/$githubUser/$appName"
        val sourceContext = SourceContext(
            source = sourceName,
            githubRepoContext = GitHubRepoContext(branchName)
        )

        return CreateSessionRequest(
            prompt = prompt,
            sourceContext = sourceContext,
            title = "$appName IDEaz Session",
            automationMode = "AUTO_CREATE_PR"
        )
    }

    /** Generic poll function that logs to a specified output (either global log or overlay) */
    private fun pollForPatch(sessionName: String, logTarget: Any, attempts: Int = 0) {
        viewModelScope.launch {
            if (attempts > 20) { // 100s timeout
                logTo(logTarget, "Error: Timed out waiting for AI patch.")
                if (logTarget == "OVERLAY") sendOverlayBroadcast(Intent("com.hereliesaz.ideaz.TASK_FINISHED"))
                return@launch
            }

            try {
                logTo(logTarget, "Polling for patch... (Attempt ${attempts + 1})")
                val activities = ApiClient.julesApiService.listActivities(sessionName)
                val lastPatch = activities.lastOrNull()?.artifacts?.firstOrNull()?.changeSet?.gitPatch

                if (lastPatch != null) {
                    logTo(logTarget, "Patch found! Applying...")
                    _activities.value = activities // Store patch globally for apply
                    appContext?.let { applyPatch(it, logTarget) }
                } else {
                    // Not found, poll again
                    delay(5000)
                    pollForPatch(sessionName, logTarget, attempts + 1)
                }
            } catch (e: Exception) {
                logTo(logTarget, "Error polling for patch: ${e.message}")
                if (logTarget == "OVERLAY") sendOverlayBroadcast(Intent("com.hereliesaz.ideaz.TASK_FINISHED"))
            }
        }
    }

    /** Generic apply patch function */
    fun applyPatch(context: Context, logTarget: Any) {
        viewModelScope.launch {
            logTo(logTarget, "Applying patch...")
            try {
                val patch = _activities.value.lastOrNull()?.artifacts?.firstOrNull()?.changeSet?.gitPatch?.unidiffPatch
                if (patch != null) {
                    val projectDir = context.filesDir.resolve("project")
                    val gitManager = GitManager(projectDir)
                    gitManager.applyPatch(patch)
                    logTo(logTarget, "Patch applied, rebuilding...")
                    // Build is global, logs will go to _buildLog via buildCallback
                    startBuild(context)
                } else {
                    logTo(logTarget, "Error: Apply patch called but no patch found.")
                    if (logTarget == "OVERLAY") sendOverlayBroadcast(Intent("com.hereliesaz.ideaz.TASK_FINISHED"))
                }
            } catch (e: Exception) {
                logTo(logTarget, "Error applying patch: ${e.message}")
                if (logTarget == "OVERLAY") sendOverlayBroadcast(Intent("com.hereliesaz.ideaz.TASK_FINISHED"))
            }
        }
    }

    /** Global debug function, logs to global log */
    fun debugBuild() {
        viewModelScope.launch {
            _aiStatus.value = "Debugging build failure..."
            try {
                session.value?.let { // Uses the global session
                    val message = UserMessaged(buildLog.value)
                    val updatedSession = ApiClient.julesApiService.sendMessage(it.name, message)
                    _session.value = updatedSession
                    _aiStatus.value = "Debug info sent. Waiting for new patch..."
                    // Poll and log to global log
                    pollForPatch(it.name, _buildLog)
                }
            } catch (e: Exception) {
                _aiStatus.value = "Error debugging: ${e.message}"
            }
        }
    }

    /** Helper to abstract logging destination */
    private fun logTo(target: Any?, message: String) {
        if (target == null) return
        when (target) {
            is MutableStateFlow<*> -> {
                (target as? MutableStateFlow<String>)?.value += "\n$message"
            }
            "OVERLAY" -> {
                logToOverlay(message)
            }
        }
    }

    private fun logToOverlay(message: String) {
        sendOverlayBroadcast(Intent("com.hereliesaz.ideaz.AI_LOG").apply {
            putExtra("MESSAGE", message)
        })
    }

    private fun sendOverlayBroadcast(intent: Intent) {
        appContext?.sendBroadcast(intent)
    }


    // --- API Listing Functions (unchanged) ---
    fun listSessions() {
        viewModelScope.launch {
            try {
                val response = ApiClient.julesApiService.listSessions()
                _sessions.value = response.sessions // Correctly access the list
            } catch (e: Exception) {
                _aiStatus.value = "Error listing sessions: ${e.message}"
            }
        }
    }

    fun loadSources() {
        viewModelScope.launch {
            try {
                _aiStatus.value = "Loading sources..."
                val response = ApiClient.julesApiService.listSources()
                _sources.value = response.sources
                _aiStatus.value = "Sources loaded."
            } catch (e: Exception) {
                _aiStatus.value = "Error loading sources: ${e.message}"
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
}