package com.hereliesaz.ideaz.ui

// --- FIX: Use import aliases to resolve ambiguity ---
import android.app.Activity as AndroidActivity // <-- FIX
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.graphics.Rect
import android.app.Application
import android.os.IBinder
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.hereliesaz.ideaz.IBuildCallback
import com.hereliesaz.ideaz.IBuildService
import com.hereliesaz.ideaz.api.ApiClient
import com.hereliesaz.ideaz.api.Session
import com.hereliesaz.ideaz.api.UserMessaged
import com.hereliesaz.ideaz.git.GitManager
import com.hereliesaz.ideaz.services.BuildService
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.io.File
import com.hereliesaz.ideaz.models.SourceMapEntry
import com.hereliesaz.ideaz.utils.SourceMapParser
import com.hereliesaz.ideaz.api.Activity as ApiActivity // <-- FIX
import com.hereliesaz.ideaz.api.Source
import com.hereliesaz.ideaz.services.ScreenshotService
import kotlinx.coroutines.delay
import androidx.preference.PreferenceManager
import com.hereliesaz.ideaz.api.GeminiApiClient
import com.hereliesaz.ideaz.api.GitHubRepoContext
import com.hereliesaz.ideaz.api.SourceContext
import java.io.FileOutputStream
import java.io.IOException
import kotlinx.coroutines.Job

class MainViewModel(
    application: Application,
    val settingsViewModel: SettingsViewModel
) : AndroidViewModel(application) {

    private val TAG = "MainViewModel"

    // --- Global Build Log ---
    private val _buildLog = MutableStateFlow("")
    val buildLog = _buildLog.asStateFlow()

    private val _aiLog = MutableStateFlow("")
    private val aiLog = _aiLog.asStateFlow()

    lateinit var filteredLog: StateFlow<String>


    // --- Service Binders ---
    private var buildService: IBuildService? = null
    private var isBuildServiceBound = false

    // --- Session / API State ---
    private val _session = MutableStateFlow<Session?>(null)
    val session = _session.asStateFlow()
    private val _activities = MutableStateFlow<List<ApiActivity>>(emptyList()) // <-- FIX
    val activities = _activities.asStateFlow()
    private val _sessions = MutableStateFlow<List<Session>>(emptyList())
    val sessions = _sessions.asStateFlow()
    private val _sources = MutableStateFlow<List<Source>>(emptyList())
    val sources = _sources.asStateFlow()

    // --- Code/Source Map State ---
    private var sourceMap: Map<String, SourceMapEntry> = emptyMap()

    // --- Cancel Dialog State ---
    private val _showCancelDialog = MutableStateFlow(false)
    val showCancelDialog = _showCancelDialog.asStateFlow()
    private var contextualTaskJob: Job? = null

    // --- Screenshot State ---
    private val _requestScreenCapture = MutableStateFlow(false)
    val requestScreenCapture = _requestScreenCapture.asStateFlow()
    private var screenCaptureResultCode: Int? = null
    private var screenCaptureData: Intent? = null
    private var pendingRichPrompt: String? = null // Holds the prompt while screenshot is taken
    private var pendingRect: Rect? = null // Holds the rect to re-draw the log box
    // --- END ---

    init {
        // Service is now bound explicitly from MainActivity
    }

    override fun onCleared() {
        super.onCleared()
        unbindBuildService(getApplication())
    }


    // --- Build Service Connection ---
    private val buildServiceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            Log.d(TAG, "onServiceConnected: Service connected")
            buildService = IBuildService.Stub.asInterface(service)
            isBuildServiceBound = true
            _buildLog.value += "[INFO] Status: Build Service Connected\n"
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            Log.d(TAG, "onServiceDisconnected: Service disconnected")
            buildService = null
            isBuildServiceBound = false
            _buildLog.value += "[INFO] Status: Build Service Disconnected\n"
        }
    }

    // --- Build Callback ---
    private val buildCallback = object : IBuildCallback.Stub() {
        override fun onLog(message: String) {
            Log.d(TAG, "onLog: $message")
            viewModelScope.launch {
                _buildLog.value += "[VERBOSE] $message\n" // Build logs go to global log
                buildService?.updateNotification(message)
            }
        }
        override fun onSuccess(apkPath: String) {
            Log.d(TAG, "onSuccess: Build successful, APK at $apkPath")
            viewModelScope.launch {
                _buildLog.value += "\n[INFO] Build successful: $apkPath\n"
                _buildLog.value += "[INFO] Status: Build Successful\n"
                contextualTaskJob = null // Task is finished
                logToOverlay("Build successful. Task finished.")
                sendOverlayBroadcast(Intent("com.hereliesaz.ideaz.TASK_FINISHED"))

                val buildDir = File(apkPath).parentFile
                if (buildDir != null) {
                    val parser = SourceMapParser(buildDir)
                    sourceMap = parser.parse()
                    _buildLog.value += "[DEBUG] Source map loaded. Found ${sourceMap.size} entries.\n"
                }
            }
        }

        override fun onFailure(log: String) {
            Log.e(TAG, "onFailure: Build failed with log:\n$log")
            viewModelScope.launch {
                _buildLog.value += "\n[INFO] Build failed:\n$log\n"
                _buildLog.value += "[INFO] Status: Build Failed\n"
                contextualTaskJob = null // Task is finished
                logToOverlay("Build failed. See global log to debug.")
                _buildLog.value += "[INFO] AI Status: Build failed, asking AI to debug...\n"
                debugBuild() // Global debug
            }
        }
    }

    // --- Service Binding ---
    fun bindBuildService(context: Context) {
        Log.d(TAG, "bindBuildService called")

        filteredLog = combine(
            buildLog,
            aiLog,
            settingsViewModel.logLevel
        ) { build, ai, level ->
            val combinedLogs = "$build\n$ai"
            if (combinedLogs.isBlank()) return@combine ""

            val lines = combinedLogs.lines()

            val filteredLines = lines.filter { line ->
                when (level) {
                    SettingsViewModel.LOG_LEVEL_INFO ->
                        line.startsWith("[INFO]") || !line.trim().startsWith("[") // Show INFO and untagged lines
                    SettingsViewModel.LOG_LEVEL_DEBUG ->
                        line.startsWith("[INFO]") || line.startsWith("[DEBUG]") || !line.trim().startsWith("[") // Show INFO, DEBUG, and untagged
                    SettingsViewModel.LOG_LEVEL_VERBOSE ->
                        true // Show all
                    else ->
                        true // Default to showing all
                }
            }
            filteredLines.joinToString("\n")
        }.stateIn(viewModelScope, SharingStarted.Lazily, "")

        // Bind Build Service
        Log.d(TAG, "Binding to BuildService")
        Intent("com.hereliesaz.ideaz.BUILD_SERVICE").also { intent ->
            intent.component = ComponentName(context, BuildService::class.java)
            context.bindService(intent, buildServiceConnection, Context.BIND_AUTO_CREATE)
        }
        // Also load sources when service is bound
        loadSources()
    }

    fun unbindBuildService(context: Context) {
        Log.d(TAG, "unbindBuildService called")
        if (isBuildServiceBound) {
            Log.d(TAG, "Unbinding from BuildService")
            context.unbindService(buildServiceConnection)
            isBuildServiceBound = false
        }
    }

    fun clearLog() {
        Log.d(TAG, "clearLog called")
        _buildLog.value = ""
        _aiLog.value = ""
    }

    // --- Inspection Logic ---

    /**
     * Called by MainActivity when it receives "com.hereliesaz.ideaz.PROMPT_SUBMITTED_NODE"
     */
    fun onNodePromptSubmitted(resourceId: String, prompt: String, bounds: Rect) {
        Log.d(TAG, "onNodePromptSubmitted: resourceId=$resourceId, prompt='$prompt', bounds=$bounds")

        pendingRect = bounds // Save the rect to re-draw the log box
        val entry = sourceMap[resourceId]

        // Construct the text-based prefix
        if (entry != null) {
            viewModelScope.launch {
                try {
                    val file = File(entry.file)
                    val lines = file.readLines()
                    val lineIndex = entry.line - 1
                    val snippet = lines.getOrNull(lineIndex)?.trim()

                    // Prefix + User Prompt
                    pendingRichPrompt = """
                    Context (for element $resourceId):
                    File: ${entry.file}
                    Line: ${entry.line}
                    Code Snippet: $snippet
                    
                    User Request: "$prompt"
                    """.trimIndent()
                    takeScreenshot(bounds)

                } catch (e: Exception) {
                    pendingRichPrompt = "Context: Element $resourceId (Error: Could not read source file ${e.message})\nUser Request: \"$prompt\""
                    takeScreenshot(bounds)
                }
            }
        } else {
            // Fallback if source map fails
            pendingRichPrompt = "Context: Element $resourceId (Error: Not found in source map)\nUser Request: \"$prompt\""
            takeScreenshot(bounds)
        }
    }

    /**
     * Called by MainActivity when it receives "com.hereliesaz.ideaz.PROMPT_SUBMITTED_RECT"
     */
    fun onRectPromptSubmitted(rect: Rect, prompt: String) {
        Log.d(TAG, "onRectPromptSubmitted: rect=$rect, prompt='$prompt'")

        pendingRect = rect // Save the rect to re-draw the log box
        val richPrompt = """
        Context: Screen area Rect(${rect.left}, ${rect.top}, ${rect.right}, ${rect.bottom})
        
        User Request: "$prompt"
        """.trimIndent()

        // Store the prompt and take a screenshot
        pendingRichPrompt = richPrompt
        takeScreenshot(rect)
    }

    fun startInspection(context: Context) {
        Log.d(TAG, "startInspection called")
        context.startService(Intent(context, com.hereliesaz.ideaz.services.UIInspectionService::class.java))
    }

    fun stopInspection(context: Context) {
        Log.d(TAG, "stopInspection called")
        context.stopService(Intent(context, com.hereliesaz.ideaz.services.UIInspectionService::class.java))
        confirmCancelTask() // Stop inspection is a hard cancel
    }


    // --- Build Logic ---
    fun startBuild(context: Context) {
        Log.d(TAG, "startBuild called")
        if (isBuildServiceBound) {
            viewModelScope.launch {
                _buildLog.value = "[INFO] Status: Building...\n" // Clear log and set status
                val projectDir = File(extractProject(context))
                Log.d(TAG, "Project extracted to: ${projectDir.absolutePath}")
                buildService?.startBuild(projectDir.absolutePath, buildCallback)
            }
        } else {
            Log.w(TAG, "startBuild: Build service not bound")
            _buildLog.value += "[INFO] Status: Service not bound\n"
        }
    }

    private fun extractProject(context: Context): String {
        Log.d(TAG, "extractProject called")
        val projectDir = context.filesDir.resolve("project")
        if (projectDir.exists()) {
            Log.d(TAG, "Deleting existing project directory")
            projectDir.deleteRecursively()
        }
        projectDir.mkdirs()
        Log.d(TAG, "Created project directory at: ${projectDir.absolutePath}")

        context.assets.list("project")?.forEach {
            copyAsset(context, "project/$it", projectDir.resolve(it).absolutePath)
        }
        return projectDir.absolutePath
    }

    private fun copyAsset(context: Context, assetPath: String, destPath: String) {
        Log.d(TAG, "copyAsset from '$assetPath' to '$destPath'")
        val assetManager = context.assets
        try {
            val files = assetManager.list(assetPath)
            if (files.isNullOrEmpty() || files.isEmpty()) {
                // It's a file
                Log.d(TAG, "Copying file: $assetPath")
                assetManager.open(assetPath).use { input ->
                    FileOutputStream(destPath).use { output ->
                        input.copyTo(output)
                    }
                }
            } else {
                // It's a directory
                Log.d(TAG, "Creating directory: $destPath")
                val dir = File(destPath)
                if (!dir.exists()) {
                    dir.mkdirs()
                }
                files.forEach {
                    copyAsset(context, "$assetPath/$it", "$destPath/$it")
                }
            }
        } catch (e: IOException) {
            Log.e(TAG, "Failed to copy asset: $assetPath", e)
            try {
                // Fallback for files that are not listed as empty directories
                assetManager.open(assetPath).use { input ->
                    FileOutputStream(destPath).use { output ->
                        input.copyTo(output)
                    }
                }
            } catch (e2: IOException) {
                Log.e(TAG, "Failed to copy asset as file: $assetPath", e2)
            }
        }
    }

    // --- CONTEXTLESS AI (Global Log) ---
    fun sendPrompt(prompt: String?, isInitialization: Boolean = false) {
        Log.d(TAG, "sendPrompt called with prompt: '$prompt', isInitialization: $isInitialization")
        val taskKey = if (isInitialization) {
            SettingsViewModel.KEY_AI_ASSIGNMENT_INIT
        } else {
            SettingsViewModel.KEY_AI_ASSIGNMENT_CONTEXTLESS
        }
        Log.d(TAG, "Task key determined as: $taskKey")

        _buildLog.value += "\n[INFO] Sending prompt for $taskKey: $prompt\n"

        val model = getAssignedModelForTask(taskKey)
        if (model == null) {
            Log.w(TAG, "No AI model assigned for task: $taskKey")
            _buildLog.value += "[INFO] Error: No AI model assigned for this task. Go to Settings.\n"
            return
        }
        Log.d(TAG, "Assigned model: ${model.displayName}")

        if (settingsViewModel.getApiKey(model.requiredKey).isNullOrBlank()) {
            Log.w(TAG, "API key for ${model.displayName} is missing")
            _buildLog.value += "[INFO] Error: API Key for ${model.displayName} is missing. Go to Settings.\n"
            return
        }

        viewModelScope.launch {
            _buildLog.value += "[INFO] AI Status: Sending...\n"
            _aiLog.value = "" // Clear previous AI log

            when (model.id) {
                AiModels.JULES_DEFAULT -> {
                    Log.d(TAG, "Using Jules API for contextless prompt")
                    try {
                        val sessionRequest = createSessionRequest(prompt)
                        if (sessionRequest == null) {
                            Log.w(TAG, "Failed to create session request, project settings incomplete")
                            _buildLog.value += "[INFO] AI Status: Error: Project settings incomplete.\n"
                            _buildLog.value += "[INFO] Please go to Project settings and set App Name and GitHub User.\n"
                            return@launch
                        }
                        Log.d(TAG, "Creating Jules session")
                        val response = ApiClient.julesApiService.createSession(sessionRequest)
                        _session.value = response
                        Log.d(TAG, "Jules session created: ${response.name}")
                        _buildLog.value += "[DEBUG] Jules session created: ${response.name}\n"
                        _buildLog.value += "[INFO] AI Status: Session created. Waiting for patch...\n"
                        response.name?.let { pollForPatch(it, _buildLog) }

                    } catch (e: Exception) {
                        Log.e(TAG, "Error creating Jules session", e)
                        _buildLog.value += "[INFO] AI Status: Error: ${e.message}\n"
                    }
                }
                AiModels.GEMINI_FLASH -> {
                    Log.d(TAG, "Using Gemini Flash for contextless prompt (not implemented)")
                    _buildLog.value += "[INFO] AI Status: Idle\n"
                    _buildLog.value += "[INFO] Gemini Flash client not yet implemented.\n"
                }
            }
        }
    }

    // --- CONTEXTUAL AI (Overlay Log) ---
    private fun startContextualAITask(richPrompt: String) {
        Log.d(TAG, "startContextualAITask called with richPrompt:\n$richPrompt")
        // Re-show the log UI *before* sending the prompt
        pendingRect?.let {
            Log.d(TAG, "Re-showing log UI at rect: $it")
            sendOverlayBroadcast(Intent("com.hereliesaz.ideaz.SHOW_LOG_UI").apply {
                putExtra("RECT", it)
            })
        }
        pendingRect = null // Clear it

        logToOverlay("Sending prompt to AI...")

        val model = getAssignedModelForTask(SettingsViewModel.KEY_AI_ASSIGNMENT_OVERLAY)
        if (model == null) {
            Log.w(TAG, "No AI model assigned for overlay task")
            logToOverlay("[INFO] Error: No AI model assigned for this task. Go to Settings.")
            return
        }
        Log.d(TAG, "Assigned model for overlay task: ${model.displayName}")

        if (settingsViewModel.getApiKey(model.requiredKey).isNullOrBlank()) {
            Log.w(TAG, "API key for ${model.displayName} is missing for overlay task")
            logToOverlay("[INFO] Error: API Key for ${model.displayName} is missing. Go to Settings.")
            return
        }

        contextualTaskJob = viewModelScope.launch {
            when (model.id) {
                AiModels.JULES_DEFAULT -> {
                    Log.d(TAG, "Using Jules API for overlay task")
                    try {
                        val sessionRequest = createSessionRequest(richPrompt)
                        if (sessionRequest == null) {
                            Log.w(TAG, "Failed to create session request for overlay task")
                            logToOverlay("Error: Project settings incomplete. Go to main app.")
                            logToOverlay("Task Finished.") // Manually finish
                            return@launch
                        }

                        Log.d(TAG, "Creating Jules session for overlay task")
                        val response = ApiClient.julesApiService.createSession(sessionRequest)
                        Log.d(TAG, "Jules session created for overlay task: ${response.name}")
                        logToOverlay("Session created. Waiting for patch...")
                        response.name?.let { pollForPatch(it, "OVERLAY") }

                    } catch (e: Exception) {
                        Log.e(TAG, "Error creating Jules session for overlay task", e)
                        logToOverlay("Error: ${e.message}")
                        logToOverlay("Task Finished.") // Manually finish
                    }
                }
                AiModels.GEMINI_FLASH -> {
                    Log.d(TAG, "Using Gemini Flash for overlay task")
                    val apiKey = settingsViewModel.getApiKey(model.requiredKey)
                    if (apiKey.isNullOrBlank()) {
                        Log.w(TAG, "Gemini API key is missing for overlay task")
                        _buildLog.value += "AI Status: Error: Gemini API Key is missing.\n"
                        return@launch
                    }
                    Log.d(TAG, "Generating content with Gemini Flash")
                    val responseText = GeminiApiClient.generateContent(richPrompt, apiKey)
                    if (responseText.startsWith("Error:")) {
                        Log.e(TAG, "Gemini API error: $responseText")
                        _buildLog.value += "AI Status: Error: $responseText\n"
                    } else {
                        Log.d(TAG, "Gemini response received")
                        _buildLog.value += "AI Status: Response received.\n"
                        _buildLog.value += "Gemini Response: $responseText\n"
                    }
                }
            }
        }
    }

    // --- Cancel Logic Functions ---
    fun requestCancelTask() {
        Log.d(TAG, "requestCancelTask called")
        if (settingsViewModel.getShowCancelWarning()) {
            Log.d(TAG, "Showing cancel warning dialog")
            _showCancelDialog.value = true
        } else {
            Log.d(TAG, "Skipping cancel warning dialog and confirming cancellation")
            confirmCancelTask() // No warning needed, just cancel
        }
    }

    fun confirmCancelTask() {
        Log.d(TAG, "confirmCancelTask called")
        contextualTaskJob?.cancel()
        contextualTaskJob = null
        _showCancelDialog.value = false
        logToOverlay("Task cancelled by user.")
        sendOverlayBroadcast(Intent("com.hereliesaz.ideaz.TASK_FINISHED"))
    }

    fun dismissCancelTask() {
        Log.d(TAG, "dismissCancelTask called")
        _showCancelDialog.value = false
    }

    fun disableCancelWarning() {
        Log.d(TAG, "disableCancelWarning called")
        settingsViewModel.setShowCancelWarning(false)
        confirmCancelTask()
    }

    // --- Screenshot Functions ---
    fun hasScreenCapturePermission(): Boolean {
        val hasPermission = screenCaptureData != null
        Log.d(TAG, "hasScreenCapturePermission: $hasPermission")
        return hasPermission
    }

    fun requestScreenCapturePermission() {
        Log.d(TAG, "requestScreenCapturePermission called")
        _requestScreenCapture.value = true
    }

    fun screenCaptureRequestHandled() {
        Log.d(TAG, "screenCaptureRequestHandled called")
        _requestScreenCapture.value = false
    }

    fun setScreenCapturePermission(resultCode: Int, data: Intent?) {
        Log.d(TAG, "setScreenCapturePermission called with resultCode: $resultCode")
        if (resultCode == AndroidActivity.RESULT_OK && data != null) { // <-- FIX
            Log.d(TAG, "Screen capture permission GRANTED")
            screenCaptureResultCode = resultCode
            screenCaptureData = data
        } else {
            Log.w(TAG, "Screen capture permission DENIED")
            screenCaptureResultCode = null
            screenCaptureData = null
            _buildLog.value += "Warning: Screen capture permission denied.\n"
        }
    }

    private fun takeScreenshot(rect: Rect) {
        Log.d(TAG, "takeScreenshot called for rect: $rect")
        if (!hasScreenCapturePermission()) {
            Log.w(TAG, "takeScreenshot aborted: missing screen capture permission")
            logToOverlay("Error: Missing screen capture permission.")
            return
        }

        Log.d(TAG, "Starting ScreenshotService")
        val intent = Intent(getApplication(), ScreenshotService::class.java).apply {
            putExtra(ScreenshotService.EXTRA_RESULT_CODE, screenCaptureResultCode)
            putExtra(ScreenshotService.EXTRA_DATA, screenCaptureData)
            putExtra(ScreenshotService.EXTRA_RECT, rect)
        }
        getApplication<Application>().startForegroundService(intent)
    }

    fun onScreenshotTaken(base64: String) {
        Log.d(TAG, "onScreenshotTaken called")
        val prompt = pendingRichPrompt ?: "Error: No pending prompt"
        pendingRichPrompt = null // Clear it

        // Append the Base64 string to the prompt
        val finalRichPrompt = """
        $prompt
        
        [IMAGE: data:image/png;base64,$base64]
        """.trimIndent()

        startContextualAITask(finalRichPrompt)
    }

    // --- AI Helper Functions ---

    private fun updateAiLog(activities: List<ApiActivity>) {
        Log.d(TAG, "updateAiLog called with ${activities.size} activities")
        val logBuilder = StringBuilder()
        logBuilder.append("--- AI Activity ---\n")
        activities.forEach { activity ->
            logBuilder.append("[${activity.createTime}] ${activity.description}\n")
            if (activity.artifacts.isNotEmpty()) {
                logBuilder.append("  - Artifacts generated\n")
            }
        }
        _aiLog.value = logBuilder.toString()
    }

    private fun getAssignedModelForTask(taskKey: String): AiModel? {
        Log.d(TAG, "getAssignedModelForTask called for taskKey: $taskKey")
        val modelId = settingsViewModel.getAiAssignment(taskKey)
        Log.d(TAG, "Retrieved modelId: $modelId")
        val model = AiModels.findById(modelId)
        Log.d(TAG, "Found model: ${model?.displayName}")
        return model
    }

    private fun createSessionRequest(prompt: String?): Session? {
        Log.d(TAG, "createSessionRequest called")
        val appName = settingsViewModel.getAppName()
        val githubUser = settingsViewModel.getGithubUser()
        val branchName = settingsViewModel.getBranchName()
        Log.d(TAG, "Project settings: appName=$appName, githubUser=$githubUser, branchName=$branchName")


        if (appName.isNullOrBlank() || githubUser.isNullOrBlank() || prompt.isNullOrBlank()) {
            Log.w(TAG, "Cannot create session request, appName, githubUser, or prompt is null or blank")
            return null
        }

        val sourceName = "sources/github/$githubUser/$appName"
        val sourceContext = SourceContext(
            source = sourceName,
            githubRepoContext = GitHubRepoContext(branchName)
        )

        val request = Session(
            prompt = prompt,
            sourceContext = sourceContext,
            title = "$appName IDEaz Session"
        )
        Log.d(TAG, "Created session request: $request")
        return request
    }

    private fun pollForPatch(sessionName: String, logTarget: Any, attempts: Int = 0) {
        Log.d(TAG, "pollForPatch called for session: $sessionName, attempt: ${attempts + 1}")
        viewModelScope.launch {
            if (attempts > 20) { // 100s timeout
                Log.w(TAG, "Timed out waiting for AI patch for session: $sessionName")
                logTo(logTarget, "[INFO] Error: Timed out waiting for AI patch.")
                if (logTarget == "OVERLAY") sendOverlayBroadcast(Intent("com.hereliesaz.ideaz.TASK_FINISHED"))
                return@launch
            }

            try {
                logTo(logTarget, "[DEBUG] Polling for patch... (Attempt ${attempts + 1})")
                val activities = ApiClient.julesApiService.listActivities(sessionName)
                updateAiLog(activities)
                val lastPatch = activities.lastOrNull()?.artifacts?.firstOrNull()?.changeSet?.gitPatch // <-- FIX

                if (lastPatch != null) {
                    Log.d(TAG, "Patch found for session: $sessionName")
                    logTo(logTarget, "[INFO] Patch found! Applying...")
                    _activities.value = activities // Store patch globally for apply
                    applyPatch(getApplication(), logTarget)
                } else {
                    Log.d(TAG, "Patch not found yet, polling again in 5s")
                    // Not found, poll again
                    delay(5000)
                    pollForPatch(sessionName, logTarget, attempts + 1)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error polling for patch for session: $sessionName", e)
                logTo(logTarget, "[INFO] Error polling for patch: ${e.message}")
                if (logTarget == "OVERLAY") sendOverlayBroadcast(Intent("com.hereliesaz.ideaz.TASK_FINISHED"))
            }
        }
    }

    fun applyPatch(context: Context, logTarget: Any) {
        Log.d(TAG, "applyPatch called")
        viewModelScope.launch {
            logTo(logTarget, "Applying patch...")
            try {
                val patch = _activities.value.lastOrNull()?.artifacts?.firstOrNull()?.changeSet?.gitPatch?.unidiffPatch // <-- FIX
                if (patch != null) {
                    Log.d(TAG, "Patch content retrieved, applying...")
                    val projectDir = context.filesDir.resolve("project")
                    val gitManager = GitManager(projectDir)
                    gitManager.applyPatch(patch)
                    Log.d(TAG, "Patch applied successfully, starting build")
                    logTo(logTarget, "Patch applied, rebuilding...")
                    startBuild(context)
                } else {
                    Log.w(TAG, "applyPatch called but no patch was found in activities")
                    logTo(logTarget, "Error: Apply patch called but no patch found.")
                    if (logTarget == "OVERLAY") sendOverlayBroadcast(Intent("com.hereliesaz.ideaz.TASK_FINISHED"))
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error applying patch", e)
                logTo(logTarget, "Error applying patch: ${e.message}")
                if (logTarget == "OVERLAY") sendOverlayBroadcast(Intent("com.hereliesaz.ideaz.TASK_FINISHED"))
            }
        }
    }

    fun debugBuild() {
        Log.d(TAG, "debugBuild called")
        val model = getAssignedModelForTask(SettingsViewModel.KEY_AI_ASSIGNMENT_CONTEXTLESS) // Debug follows contextless for now
        if (model == null) {
            Log.w(TAG, "No AI model assigned for debug task")
            _buildLog.value += "Error: No AI model assigned for this task. Go to Settings.\n"
            return
        }
        if (settingsViewModel.getApiKey(model.requiredKey).isNullOrBlank()) {
            Log.w(TAG, "API key for ${model.displayName} is missing for debug task")
            _buildLog.value += "Error: API Key for ${model.displayName} is missing. Go to Settings.\n"
            return
        }
        Log.d(TAG, "Using model: ${model.displayName} for build debugging")

        viewModelScope.launch {
            _buildLog.value += "AI Status: Debugging build failure...\n"

            when (model.id) {
                AiModels.JULES_DEFAULT -> {
                    Log.d(TAG, "Debugging with Jules API")
                    try {
                        session.value?.let { // Uses the global session
                            Log.d(TAG, "Sending debug message to session: ${it.name}")
                            val message = UserMessaged(buildLog.value)
                            it.name?.let { name ->
                                val updatedSession = ApiClient.julesApiService.sendMessage(name, message)
                                _session.value = updatedSession
                                Log.d(TAG, "Debug message sent, polling for new patch")
                                _buildLog.value += "AI Status: Debug info sent. Waiting for new patch...\n"
                                pollForPatch(name, _buildLog)
                            }
                        } ?: run {
                            Log.w(TAG, "Cannot debug with Jules, no active session")
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "Error during Jules debug", e)
                        _buildLog.value += "AI Status: Error debugging: ${e.message}\n"
                    }
                }
                AiModels.GEMINI_FLASH -> {
                    Log.d(TAG, "Debugging with Gemini Flash")
                    val apiKey = settingsViewModel.getApiKey(model.requiredKey)
                    if (apiKey.isNullOrBlank()) {
                        logToOverlay("Error: Gemini API Key is missing.")
                    } else {
                        val responseText = GeminiApiClient.generateContent(buildLog.value, apiKey)
                        if (responseText.startsWith("Error:")) {
                            logToOverlay(responseText)
                        } else {
                            logToOverlay("Response received.")
                            logToOverlay("Gemini Response: $responseText")
                        }
                    }
                }
            }

        }
    }

    private fun logTo(target: Any?, message: String) {
        if (target == null) {
            Log.w(TAG, "logTo called with null target")
            return
        }
        Log.d(TAG, "logTo target: ${target::class.java.simpleName}, message: $message")
        when (target) {
            is MutableStateFlow<*> -> {
                (target as? MutableStateFlow<String>)?.value += "$message\n"
            }
            "OVERLAY" -> {
                logToOverlay(message)
            }
            // <-- FIX: This was the source of the `Any` vs `String` error.
        }
    }

    private fun logToOverlay(message: String) {
        Log.d(TAG, "logToOverlay: $message")
        sendOverlayBroadcast(Intent("com.hereliesaz.ideaz.AI_LOG").apply {
            putExtra("MESSAGE", message)
        })
    }

    private fun sendOverlayBroadcast(intent: Intent) {
        Log.d(TAG, "sendOverlayBroadcast: action=${intent.action}")
        getApplication<Application>().sendBroadcast(intent)
    }

    fun listSessions() {
        Log.d(TAG, "listSessions called")
        viewModelScope.launch {
            try {
                val response = ApiClient.julesApiService.listSessions()
                _sessions.value = response.sessions
                Log.d(TAG, "Successfully listed ${response.sessions.size} sessions")
            } catch (e: Exception) {
                Log.e(TAG, "Error listing sessions", e)
                _buildLog.value += "Error listing sessions: ${e.message}\n"
            }
        }
    }

    fun loadSources() {
        Log.d(TAG, "loadSources called")
        viewModelScope.launch {
            try {
                _buildLog.value += "AI Status: Loading sources...\n"
                val response = ApiClient.julesApiService.listSources()
                _sources.value = response.sources
                _buildLog.value += "AI Status: Sources loaded.\n"
                Log.d(TAG, "Successfully loaded ${response.sources.size} sources")
            } catch (e: Exception) {
                Log.e(TAG, "Error loading sources", e)
                _buildLog.value += "AI Status: Error loading sources: ${e.message}\n"
            }
        }
    }

    fun listActivities() {
        Log.d(TAG, "listActivities called")
        viewModelScope.launch {
            try {
                session.value?.let {
                    Log.d(TAG, "Listing activities for session: ${it.name}")
                    it.name?.let { name ->
                        _activities.value = ApiClient.julesApiService.listActivities(name)
                        Log.d(TAG, "Successfully listed ${_activities.value.size} activities")
                    }
                } ?: run {
                    Log.w(TAG, "Cannot list activities, no active session")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error listing activities", e)
                _buildLog.value += "Error listing activities: ${e.message}\n"
            }
        }
    }

}