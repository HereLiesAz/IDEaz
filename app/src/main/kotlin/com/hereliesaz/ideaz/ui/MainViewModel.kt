package com.hereliesaz.ideaz.ui

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.IBinder
import android.util.Log
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
import com.hereliesaz.ideaz.api.CreateSessionRequest
import com.hereliesaz.ideaz.api.Source
import com.hereliesaz.ideaz.services.UIInspectionService
import kotlinx.coroutines.delay
import androidx.preference.PreferenceManager
import com.hereliesaz.ideaz.api.GitHubRepoContext
import com.hereliesaz.ideaz.api.SourceContext
import java.io.FileOutputStream
import java.io.IOException

class MainViewModel : ViewModel() {

    // --- Global Build Log ---
    private val _buildLog = MutableStateFlow("")
    val buildLog = _buildLog.asStateFlow() // This is NOW the single destination for ALL text output

    // --- Service Binders ---
    private var buildService: IBuildService? = null
    private var isBuildServiceBound = false

    // --- Session / API State ---
    // These are still needed for internal logic, but not for direct display
    private val _session = MutableStateFlow<Session?>(null)
    val session = _session.asStateFlow() // Used by IdeNavHost
    private val _activities = MutableStateFlow<List<Activity>>(emptyList())
    val activities = _activities.asStateFlow() // Used by IdeNavHost
    private val _sessions = MutableStateFlow<List<Session>>(emptyList())
    val sessions = _sessions.asStateFlow() // Used by IdeNavHost
    private val _sources = MutableStateFlow<List<Source>>(emptyList())
    val sources = _sources.asStateFlow() // Used by IdeNavHost

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

    // Lazy init for SettingsViewModel
    private val settingsViewModel by lazy { SettingsViewModel() }


    // --- Build Service Connection ---
    private val buildServiceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            buildService = IBuildService.Stub.asInterface(service)
            isBuildServiceBound = true
            _buildLog.value += "Status: Build Service Connected\n"
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            buildService = null
            isBuildServiceBound = false
            _buildLog.value += "Status: Build Service Disconnected\n"
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
                _buildLog.value += "\nBuild successful: $apkPath\n"
                _buildLog.value += "Status: Build Successful\n"

                // Contextual AI task is finished
                logToOverlay("Build successful. Task finished.")
                sendOverlayBroadcast(Intent("com.hereliesaz.ideaz.TASK_FINISHED"))

                val buildDir = File(apkPath).parentFile
                if (buildDir != null) {
                    val parser = SourceMapParser(buildDir)
                    sourceMap = parser.parse()
                    _buildLog.value += "Source map loaded. Found ${sourceMap.size} entries.\n"
                }
            }
        }

        override fun onFailure(log: String) {
            viewModelScope.launch {
                _buildLog.value += "\nBuild failed:\n$log\n"
                _buildLog.value += "Status: Build Failed\n"

                // Check which AI flow is active. This is tricky.
                // For now, assume build failures are debugged globally.
                logToOverlay("Build failed. See global log to debug.")
                _buildLog.value += "AI Status: Build failed, asking AI to debug...\n"
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
            _buildLog.value += "Source for $id found at ${entry.file}:${entry.line}\n"
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
                            _buildLog.value += "Context Found:\nFile: ${entry.file}\nLine: ${entry.line}\nSnippet: $snippet\n"
                        } else {
                            _buildLog.value += "Error: Line number ${entry.line} is out of bounds for ${entry.file}\n"
                        }
                    } else {
                        _buildLog.value += "Error: Source file not found at ${entry.file}\n"
                    }
                } catch (e: Exception) {
                    _buildLog.value += "Error reading source file: ${e.message}\n"
                }
            }
        } else {
            _buildLog.value += "Source for $id not found\n"
        }
    }


    // --- Build Logic ---
    fun startBuild(context: Context) {
        if (isBuildServiceBound) {
            viewModelScope.launch {
                _buildLog.value = "Status: Building...\n" // Clear log and set status
                val projectDir = File(extractProject(context))
                buildService?.startBuild(projectDir.absolutePath, buildCallback)
            }
        } else {
            _buildLog.value += "Status: Service not bound\n"
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
    fun sendPrompt(prompt: String, isInitialization: Boolean = false) {
        val taskKey = if (isInitialization) {
            SettingsViewModel.KEY_AI_ASSIGNMENT_INIT
        } else {
            SettingsViewModel.KEY_AI_ASSIGNMENT_CONTEXTLESS
        }

        // This is a contextless prompt, so context is null
        _selectedFile.value = null
        _selectedLine.value = null
        _selectedCodeSnippet.value = null

        _buildLog.value += "\nSending prompt for $taskKey: $prompt\n"

        // Get the assigned AI model for this task
        val model = getAssignedModelForTask(taskKey)
        if (model == null) {
            _buildLog.value += "Error: No AI model assigned for this task. Go to Settings.\n"
            return
        }

        // Check if the required key is present
        if (settingsViewModel.getApiKey(appContext!!, model.requiredKey).isNullOrBlank()) {
            _buildLog.value += "Error: API Key for ${model.displayName} is missing. Go to Settings.\n"
            return
        }

        viewModelScope.launch {
            _buildLog.value += "AI Status: Sending...\n"

            when (model.id) {
                AiModels.JULES_DEFAULT -> {
                    try {
                        val sessionRequest = createSessionRequest(prompt)
                        if (sessionRequest == null) {
                            _buildLog.value += "AI Status: Error: Project settings incomplete.\n"
                            _buildLog.value += "Please go to Project settings and set App Name and GitHub User.\n"
                            return@launch
                        }

                        val response = ApiClient.julesApiService.createSession(sessionRequest)
                        _session.value = response // Store as the "global" session
                        _buildLog.value += "AI Status: Session created. Waiting for patch...\n"
                        // Poll and log to _buildLog
                        pollForPatch(response.name, _buildLog)

                    } catch (e: Exception) {
                        _buildLog.value += "AI Status: Error: ${e.message}\n"
                    }
                }

                AiModels.GEMINI_FLASH -> {
                    // --- Gemini Logic (Placeholder) ---
                    _buildLog.value += "AI Status: Idle\n"
                    _buildLog.value += "Gemini Flash client not yet implemented.\n"
                    // TODO: Implement Gemini API call here
                    // 1. Call Gemini API with googleApiKey
                    // 2. Get text response
                    // 3. (Future) Convert text response to a patch
                    // 4. (Future) call applyPatch(...)
                }

                // Add other models here
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

        // Get the assigned AI model for this task
        val model = getAssignedModelForTask(SettingsViewModel.KEY_AI_ASSIGNMENT_OVERLAY)
        if (model == null) {
            logToOverlay("Error: No AI model assigned for this task. Go to Settings.")
            return
        }

        // Check if the required key is present
        if (settingsViewModel.getApiKey(appContext!!, model.requiredKey).isNullOrBlank()) {
            logToOverlay("Error: API Key for ${model.displayName} is missing. Go to Settings.")
            return
        }

        viewModelScope.launch {
            when (model.id) {
                AiModels.JULES_DEFAULT -> {
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
                AiModels.GEMINI_FLASH -> {
                    // --- Gemini Logic (Placeholder) ---
                    logToOverlay("Gemini Flash client not yet implemented.")
                    logToOverlay("Task Finished.") // Manually finish
                }
            }
        }
    }

    // --- AI Helper Functions ---

    /**
     * Gets the assigned AiModel for a given task, handling fallback to Default.
     */
    private fun getAssignedModelForTask(taskKey: String): AiModel? {
        val modelId = settingsViewModel.getAiAssignment(appContext!!, taskKey)
        return AiModels.findById(modelId)
    }


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
        // Get the assigned AI model for this task
        val model = getAssignedModelForTask(SettingsViewModel.KEY_AI_ASSIGNMENT_CONTEXTLESS) // Debug follows contextless for now
        if (model == null) {
            _buildLog.value += "Error: No AI model assigned for this task. Go to Settings.\n"
            return
        }
        if (settingsViewModel.getApiKey(appContext!!, model.requiredKey).isNullOrBlank()) {
            _buildLog.value += "Error: API Key for ${model.displayName} is missing. Go to Settings.\n"
            return
        }

        viewModelScope.launch {
            _buildLog.value += "AI Status: Debugging build failure...\n"

            when (model.id) {
                AiModels.JULES_DEFAULT -> {
                    try {
                        session.value?.let { // Uses the global session
                            val message = UserMessaged(buildLog.value)
                            val updatedSession = ApiClient.julesApiService.sendMessage(it.name, message)
                            _session.value = updatedSession
                            _buildLog.value += "AI Status: Debug info sent. Waiting for new patch...\n"
                            // Poll and log to global log
                            pollForPatch(it.name, _buildLog)
                        }
                    } catch (e: Exception) {
                        _buildLog.value += "AI Status: Error debugging: ${e.message}\n"
                    }
                }
                AiModels.GEMINI_FLASH -> {
                    _buildLog.value += "AI Status: Idle\n"
                    _buildLog.value += "Gemini debug client not yet implemented.\n"
                }
            }
        }
    }

    /** Helper to abstract logging destination */
    private fun logTo(target: Any?, message: String) {
        if (target == null) return
        when (target) {
            is MutableStateFlow<*> -> {
                (target as? MutableStateFlow<String>)?.value += "$message\n"
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
                _buildLog.value += "Error listing sessions: ${e.message}\n"
            }
        }
    }

    fun loadSources() {
        viewModelScope.launch {
            try {
                _buildLog.value += "AI Status: Loading sources...\n"
                val response = ApiClient.julesApiService.listSources()
                _sources.value = response.sources
                _buildLog.value += "AI Status: Sources loaded.\n"
            } catch (e: Exception) {
                _buildLog.value += "AI Status: Error loading sources: ${e.message}\n"
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
                _buildLog.value += "Error listing activities: ${e.message}\n"
            }
        }
    }

    fun updateCodeContent(newContent: String) {
        _codeContent.value = newContent
    }
}