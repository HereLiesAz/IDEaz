package com.hereliesaz.ideaz.ui

// --- FIX: Use import aliases to resolve ambiguity ---
import android.app.Activity as AndroidActivity // <-- FIX
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.graphics.Rect
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
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.io.File
import com.hereliesaz.ideaz.models.SourceMapEntry
import com.hereliesaz.ideaz.utils.SourceMapParser
import com.hereliesaz.ideaz.api.Activity as ApiActivity // <-- FIX
import com.hereliesaz.ideaz.api.CreateSessionRequest
import com.hereliesaz.ideaz.api.Source
import com.hereliesaz.ideaz.services.ScreenshotService
import com.hereliesaz.ideaz.services.UIInspectionService
import kotlinx.coroutines.delay
import androidx.preference.PreferenceManager
import com.hereliesaz.ideaz.api.GeminiApiClient
import com.hereliesaz.ideaz.api.GitHubRepoContext
import com.hereliesaz.ideaz.api.SourceContext
import java.io.FileOutputStream
import java.io.IOException
import kotlinx.coroutines.Job
import android.app.Application
import androidx.lifecycle.AndroidViewModel

class MainViewModel(application: Application) : AndroidViewModel(application) {

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

    private val appContext: Context
    private val settingsViewModel: SettingsViewModel

    init {
        appContext = application.applicationContext
        settingsViewModel = SettingsViewModel()
    }

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
                buildService?.updateNotification(message)
            }
        }
        override fun onSuccess(apkPath: String) {
            viewModelScope.launch {
                _buildLog.value += "\nBuild successful: $apkPath\n"
                _buildLog.value += "Status: Build Successful\n"
                contextualTaskJob = null // Task is finished
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
                contextualTaskJob = null // Task is finished
                logToOverlay("Build failed. See global log to debug.")
                _buildLog.value += "AI Status: Build failed, asking AI to debug...\n"
                debugBuild() // Global debug
            }
        }
    }

    // --- Service Binding ---
    fun bindBuildService(context: Context) {
        appContext = context.applicationContext // Store context

        filteredLog = combine(
            buildLog,
            aiLog,
            settingsViewModel.logVerbosity
        ) { build, ai, verbosity ->
            when (verbosity) {
                SettingsViewModel.LOG_VERBOSITY_BUILD -> build
                SettingsViewModel.LOG_VERBOSITY_AI -> ai
                else -> "$build\n$ai"
            }
        }.stateIn(viewModelScope, SharingStarted.Lazily, "")

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

    fun clearLog() {
        _buildLog.value = ""
        _aiLog.value = ""
    }

    // --- Inspection Logic ---

    /**
     * Called by MainActivity when it receives "com.hereliesaz.ideaz.PROMPT_SUBMITTED_NODE"
     */
    fun onNodePromptSubmitted(resourceId: String, prompt: String, bounds: Rect) {
        Log.d("MainViewModel", "Contextual (NODE) prompt submitted for $resourceId: $prompt")

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
        Log.d("MainViewModel", "Contextual (RECT) prompt submitted for $rect: $prompt")

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
        context.startService(Intent(context, com.hereliesaz.ideaz.services.UIInspectionService::class.java))
    }

    fun stopInspection(context: Context) {
        context.stopService(Intent(context, com.hereliesaz.ideaz.services.UIInspectionService::class.java))
        confirmCancelTask() // Stop inspection is a hard cancel
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
            Log.e("MainViewModel", "Failed to copy asset: $assetPath", e)
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
    fun sendPrompt(prompt: String, isInitialization: Boolean = false) {
        val taskKey = if (isInitialization) {
            SettingsViewModel.KEY_AI_ASSIGNMENT_INIT
        } else {
            SettingsViewModel.KEY_AI_ASSIGNMENT_CONTEXTLESS
        }

        _buildLog.value += "\nSending prompt for $taskKey: $prompt\n"

        val model = getAssignedModelForTask(taskKey)
        if (model == null) {
            _buildLog.value += "Error: No AI model assigned for this task. Go to Settings.\n"
            return
        }
        if (settingsViewModel.getApiKey(appContext!!, model.requiredKey).isNullOrBlank()) {
            _buildLog.value += "Error: API Key for ${model.displayName} is missing. Go to Settings.\n"
            return
        }

        viewModelScope.launch {
            _buildLog.value += "AI Status: Sending...\n"
            _aiLog.value = "" // Clear previous AI log

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
                        _session.value = response
                        _buildLog.value += "AI Status: Session created. Waiting for patch...\n"
                        pollForPatch(response.name, _buildLog)

                    } catch (e: Exception) {
                        _buildLog.value += "AI Status: Error: ${e.message}\n"
                    }
                }
                AiModels.GEMINI_FLASH -> {
                    _buildLog.value += "AI Status: Idle\n"
                    _buildLog.value += "Gemini Flash client not yet implemented.\n"
                }
            }
        }
    }

    // --- CONTEXTUAL AI (Overlay Log) ---
    private fun startContextualAITask(richPrompt: String) {
        // Re-show the log UI *before* sending the prompt
        pendingRect?.let {
            sendOverlayBroadcast(Intent("com.hereliesaz.ideaz.SHOW_LOG_UI").apply {
                putExtra("RECT", it)
            })
        }
        pendingRect = null // Clear it

        logToOverlay("Sending prompt to AI...")

        val model = getAssignedModelForTask(SettingsViewModel.KEY_AI_ASSIGNMENT_OVERLAY)
        if (model == null) {
            logToOverlay("Error: No AI model assigned for this task. Go to Settings.")
            return
        }
        if (settingsViewModel.getApiKey(appContext!!, model.requiredKey).isNullOrBlank()) {
            logToOverlay("Error: API Key for ${model.displayName} is missing. Go to Settings.")
            return
        }

        contextualTaskJob = viewModelScope.launch {
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
                        pollForPatch(response.name, "OVERLAY") // Use a string to signify overlay

                    } catch (e: Exception) {
                        logToOverlay("Error: ${e.message}")
                        logToOverlay("Task Finished.") // Manually finish
                    }
                }
                AiModels.GEMINI_FLASH -> {
                    val currentContext = appContext ?: return@launch
                    val apiKey = settingsViewModel.getApiKey(currentContext, model.requiredKey)
                    if (apiKey.isNullOrBlank()) {
                        _buildLog.value += "AI Status: Error: Gemini API Key is missing.\n"
                        return@launch
                    }
                    val responseText = GeminiApiClient.generateContent(richPrompt, apiKey)
                    if (responseText.startsWith("Error:")) {
                        _buildLog.value += "AI Status: Error: $responseText\n"
                    } else {
                        _buildLog.value += "AI Status: Response received.\n"
                        _buildLog.value += "Gemini Response: $responseText\n"
                    }
                }
            }
        }
    }

    // --- Cancel Logic Functions ---
    fun requestCancelTask() {
        if (settingsViewModel.getShowCancelWarning(appContext!!)) {
            _showCancelDialog.value = true
        } else {
            confirmCancelTask() // No warning needed, just cancel
        }
    }

    fun confirmCancelTask() {
        contextualTaskJob?.cancel()
        contextualTaskJob = null
        _showCancelDialog.value = false
        logToOverlay("Task cancelled by user.")
        sendOverlayBroadcast(Intent("com.hereliesaz.ideaz.TASK_FINISHED"))
    }

    fun dismissCancelTask() {
        _showCancelDialog.value = false
    }

    fun disableCancelWarning() {
        settingsViewModel.setShowCancelWarning(appContext!!, false)
        confirmCancelTask()
    }

    // --- Screenshot Functions ---
    fun hasScreenCapturePermission(): Boolean {
        return screenCaptureData != null
    }

    fun requestScreenCapturePermission() {
        _requestScreenCapture.value = true
    }

    fun screenCaptureRequestHandled() {
        _requestScreenCapture.value = false
    }

    fun setScreenCapturePermission(resultCode: Int, data: Intent?) {
        if (resultCode == AndroidActivity.RESULT_OK && data != null) { // <-- FIX
            screenCaptureResultCode = resultCode
            screenCaptureData = data
        } else {
            screenCaptureResultCode = null
            screenCaptureData = null
            _buildLog.value += "Warning: Screen capture permission denied.\n"
        }
    }

    private fun takeScreenshot(rect: Rect) {
        if (!hasScreenCapturePermission()) {
            logToOverlay("Error: Missing screen capture permission.")
            return
        }

        val intent = Intent(appContext, ScreenshotService::class.java).apply {
            putExtra(ScreenshotService.EXTRA_RESULT_CODE, screenCaptureResultCode)
            putExtra(ScreenshotService.EXTRA_DATA, screenCaptureData)
            putExtra(ScreenshotService.EXTRA_RECT, rect)
        }
        appContext?.startForegroundService(intent)
    }

    fun onScreenshotTaken(base64: String) {
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
        val modelId = settingsViewModel.getAiAssignment(appContext!!, taskKey)
        return AiModels.findById(modelId)
    }

    private fun createSessionRequest(prompt: String): CreateSessionRequest? {
        val prefs = PreferenceManager.getDefaultSharedPreferences(appContext!!)
        val appName = prefs.getString(SettingsViewModel.KEY_APP_NAME, null)
        val githubUser = prefs.getString(SettingsViewModel.KEY_GITHUB_USER, null)
        val branchName = prefs.getString(SettingsViewModel.KEY_BRANCH_NAME, "main")!!

        if (appName == null || githubUser == null) {
            return null
        }

        val sourceName = "sources/github/$githubUser/$appName"
        val sourceContext = SourceContext(
            source = sourceName,
            githubRepoContext = GitHubRepoContext(branchName)
        )

        return CreateSessionRequest(
            prompt = prompt,
            sourceContext = sourceContext,
            title = "$appName IDEaz Session"
        )
    }

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
                updateAiLog(activities)
                val lastPatch = activities.lastOrNull()?.artifacts?.firstOrNull()?.changeSet?.gitPatch // <-- FIX

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

    fun applyPatch(context: Context, logTarget: Any) {
        viewModelScope.launch {
            logTo(logTarget, "Applying patch...")
            try {
                val patch = _activities.value.lastOrNull()?.artifacts?.firstOrNull()?.changeSet?.gitPatch?.unidiffPatch // <-- FIX
                if (patch != null) {
                    val projectDir = context.filesDir.resolve("project")
                    val gitManager = GitManager(projectDir)
                    gitManager.applyPatch(patch)
                    logTo(logTarget, "Patch applied, rebuilding...")
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

    fun debugBuild() {
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
                            pollForPatch(it.name, _buildLog)
                        }
                    } catch (e: Exception) {
                        _buildLog.value += "AI Status: Error debugging: ${e.message}\n"
                    }
                }
                AiModels.GEMINI_FLASH -> {
                    val currentContext = appContext ?: return@launch
                    val apiKey = settingsViewModel.getApiKey(currentContext, model.requiredKey)
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
        if (target == null) return
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
        sendOverlayBroadcast(Intent("com.hereliesaz.ideaz.AI_LOG").apply {
            putExtra("MESSAGE", message)
        })
    }

    private fun sendOverlayBroadcast(intent: Intent) {
        appContext?.sendBroadcast(intent)
    }

    fun listSessions() {
        viewModelScope.launch {
            try {
                val response = ApiClient.julesApiService.listSessions()
                _sessions.value = response.sessions
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

}