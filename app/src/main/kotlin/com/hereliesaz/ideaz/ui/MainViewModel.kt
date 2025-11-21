package com.hereliesaz.ideaz.ui

import android.app.Activity as AndroidActivity
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
import com.hereliesaz.ideaz.jules.JulesApiClient
import com.hereliesaz.ideaz.api.JulesCliClient
import com.hereliesaz.ideaz.git.GitManager
import com.hereliesaz.ideaz.services.BuildService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import com.hereliesaz.ideaz.models.SourceMapEntry
import com.hereliesaz.ideaz.utils.SourceMapParser
import com.hereliesaz.ideaz.models.ProjectType
import com.hereliesaz.ideaz.services.ScreenshotService
import androidx.preference.PreferenceManager
import com.hereliesaz.ideaz.api.GeminiApiClient
import com.hereliesaz.ideaz.api.CreateSessionRequest
import com.hereliesaz.ideaz.api.SourceContext
import com.hereliesaz.ideaz.api.GitHubRepoContext
import com.hereliesaz.ideaz.api.ListSourcesResponse
import com.hereliesaz.ideaz.api.Source
import com.hereliesaz.ideaz.api.ListActivitiesResponse
import java.io.FileOutputStream
import java.io.IOException
import com.hereliesaz.ideaz.utils.ToolManager
import com.hereliesaz.ideaz.utils.ProjectAnalyzer
import com.hereliesaz.ideaz.utils.SourceContextHelper
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.serialization.json.Json
import org.json.JSONObject

class MainViewModel(
    application: Application,
    val settingsViewModel: SettingsViewModel
) : AndroidViewModel(application) {

    private val TAG = "MainViewModel"

    private val _buildLog = MutableStateFlow("")
    val buildLog = _buildLog.asStateFlow()

    private val _aiLog = MutableStateFlow("")
    private val aiLog = _aiLog.asStateFlow()

    lateinit var filteredLog: StateFlow<String>

    private var buildService: IBuildService? = null
    private var isBuildServiceBound = false
    private var isServiceRegistered = false

    private var sourceMap: Map<String, SourceMapEntry> = emptyMap()

    private val _ownedSources = MutableStateFlow<List<Source>>(emptyList())
    val ownedSources = _ownedSources.asStateFlow()

    private val _isLoadingSources = MutableStateFlow(false)
    val isLoadingSources = _isLoadingSources.asStateFlow()

    private val _availableSessions = MutableStateFlow<List<com.hereliesaz.ideaz.api.Session>>(emptyList())
    val availableSessions = _availableSessions.asStateFlow()

    private val _activeSessionId = MutableStateFlow<String?>(null)
    val activeSessionId = _activeSessionId.asStateFlow()

    private val _showCancelDialog = MutableStateFlow(false)
    val showCancelDialog = _showCancelDialog.asStateFlow()
    private var contextualTaskJob: Job? = null

    private val _requestScreenCapture = MutableStateFlow(false)
    val requestScreenCapture = _requestScreenCapture.asStateFlow()
    private var screenCaptureResultCode: Int? = null
    private var screenCaptureData: Intent? = null
    private var pendingRichPrompt: String? = null
    private var pendingRect: Rect? = null

    private val _promptForRect = MutableStateFlow<Rect?>(null)
    val promptForRect = _promptForRect.asStateFlow()

    private val json = Json {
        ignoreUnknownKeys = true
    }

    init {
        fetchOwnedSources()
        fetchSessions()
    }

    override fun onCleared() {
        super.onCleared()
        unbindBuildService(getApplication())
    }

    private val buildServiceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            Log.d(TAG, "onServiceConnected: Service connected")
            buildService = IBuildService.Stub.asInterface(service)
            isBuildServiceBound = true
            _buildLog.value += "[IDE] Status: Build Service Connected\n"
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            Log.d(TAG, "onServiceDisconnected: Service disconnected")
            buildService = null
            isBuildServiceBound = false
            _buildLog.value += "[IDE] Status: Build Service Disconnected\n"
        }
    }

    private val buildCallback = object : IBuildCallback.Stub() {
        override fun onLog(message: String) {
            Log.d(TAG, "onLog: $message")
            viewModelScope.launch {
                // Message should have its own prefix from the source (IDE or BUILD)
                _buildLog.value += "$message\n"
                buildService?.updateNotification(message)
            }
        }

        override fun onSuccess(apkPath: String) {
            Log.d(TAG, "onSuccess: Build successful, APK at $apkPath")
            viewModelScope.launch {
                _buildLog.value += "\n[IDE] Build successful: $apkPath\n"
                _buildLog.value += "[IDE] Status: Build Successful\n"
                contextualTaskJob = null
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
                _buildLog.value += "\n[IDE] Build failed:\n$log\n"
                _buildLog.value += "[IDE] Status: Build Failed\n"
                contextualTaskJob = null
                logToOverlay("Build failed. See global log to debug.")
                _buildLog.value += "[IDE] AI Status: Build failed, asking AI to debug...\n"
                debugBuild()
            }
        }
    }

    fun cloneOrPullProject(owner: String, repo: String, branch: String) {
        val appName = repo
        settingsViewModel.saveProjectConfig(appName, owner, branch)
        settingsViewModel.addProject("$owner/$appName")
        settingsViewModel.setAppName(appName)
        settingsViewModel.setGithubUser(owner)

        val token = settingsViewModel.getGithubToken()
        // For auth, we use the configured github user, but usually token is enough
        val authUser = settingsViewModel.getGithubUser()

        viewModelScope.launch {
            _buildLog.value += "[INFO] Selecting repository '$owner/$appName'...\n"
            val projectDir = getApplication<Application>().filesDir.resolve(appName)

            try {
                if (projectDir.exists() && File(projectDir, ".git").exists()) {
                    _buildLog.value += "[INFO] Project exists. Pulling latest changes...\n"
                    withContext(Dispatchers.IO) {
                        GitManager(projectDir).pull(authUser, token)
                    }
                    _buildLog.value += "[INFO] Pull complete.\n"
                } else {
                    if (projectDir.exists()) {
                        _buildLog.value += "[INFO] Cleaning up existing directory...\n"
                        projectDir.deleteRecursively()
                    }
                    projectDir.mkdirs()

                    _buildLog.value += "[INFO] Cloning $owner/$repo...\n"
                    withContext(Dispatchers.IO) {
                        GitManager(projectDir).clone(owner, repo, authUser, token)
                    }
                    _buildLog.value += "[INFO] Clone complete.\n"
                }

                val type = ProjectAnalyzer.detectProjectType(projectDir)
                settingsViewModel.setProjectType(type.name)
                _buildLog.value += "[INFO] Detected project type: ${type.displayName}\n"

                val pkg = ProjectAnalyzer.detectPackageName(projectDir)
                if (pkg != null) {
                    settingsViewModel.saveTargetPackageName(pkg)
                    _buildLog.value += "[INFO] Detected package name: $pkg\n"
                }

                fetchSessions()

            } catch (e: Exception) {
                Log.e(TAG, "Failed to clone/pull", e)
                _buildLog.value += "[INFO] Error: ${e.message}\n"
            }
        }
    }

    fun deleteSession(session: com.hereliesaz.ideaz.api.Session) {
        viewModelScope.launch {
            try {
                JulesApiClient.deleteSession(session.id)
                fetchSessions() // Refresh list
                _buildLog.value += "[INFO] Session ${session.id} deleted.\n"
            } catch (e: Exception) {
                Log.e(TAG, "Failed to delete session", e)
                _buildLog.value += "[ERROR] Failed to delete session: ${e.message}\n"
            }
        }
    }

    fun trySession(session: com.hereliesaz.ideaz.api.Session) {
        val prUrl = session.outputs?.firstOrNull()?.pullRequest?.url
        if (prUrl == null) {
            _buildLog.value += "[ERROR] No Pull Request found for session.\n"
            return
        }

        val prId = prUrl.substringAfterLast("/")
        val branchName = "pr-$prId"
        val appName = settingsViewModel.getAppName() ?: return
        val projectDir = getApplication<Application>().filesDir.resolve(appName)
        val token = settingsViewModel.getGithubToken()
        val user = settingsViewModel.getGithubUser()

        viewModelScope.launch {
            try {
                _buildLog.value += "[INFO] Fetching PR #$prId...\n"
                withContext(Dispatchers.IO) {
                    val gitManager = GitManager(projectDir)
                    gitManager.fetchPr(prId, branchName, user, token)
                    gitManager.checkout(branchName)
                }
                _buildLog.value += "[INFO] Checked out PR branch. Building...\n"
                startBuild(getApplication())
            } catch (e: Exception) {
                Log.e(TAG, "Try session failed", e)
                _buildLog.value += "[ERROR] Try session failed: ${e.message}\n"
            }
        }
    }

    fun acceptSession(session: com.hereliesaz.ideaz.api.Session) {
        val prUrl = session.outputs?.firstOrNull()?.pullRequest?.url ?: return
        val prId = prUrl.substringAfterLast("/")
        val branchName = "pr-$prId"
        val appName = settingsViewModel.getAppName() ?: return
        val projectDir = getApplication<Application>().filesDir.resolve(appName)
        val mainBranch = settingsViewModel.getBranchName()
        val token = settingsViewModel.getGithubToken()
        val user = settingsViewModel.getGithubUser()

        viewModelScope.launch {
            try {
                withContext(Dispatchers.IO) {
                    val gitManager = GitManager(projectDir)
                    gitManager.checkout(mainBranch)
                    gitManager.pull(user, token)
                    gitManager.merge(branchName)
                }
                _buildLog.value += "[INFO] Merged PR #$prId. Building...\n"
                startBuild(getApplication())
            } catch (e: Exception) {
                Log.e(TAG, "Accept session failed", e)
                _buildLog.value += "[ERROR] Accept session failed: ${e.message}\n"
            }
        }
    }

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
                        line.startsWith("[INFO]") || !line.trim().startsWith("[")
                    SettingsViewModel.LOG_LEVEL_DEBUG ->
                        line.startsWith("[INFO]") || line.startsWith("[DEBUG]") || !line.trim().startsWith("[")
                    SettingsViewModel.LOG_LEVEL_VERBOSE ->
                        true
                    else ->
                        true
                }
            }
            filteredLines.joinToString("\n")
        }.stateIn(viewModelScope, SharingStarted.Lazily, "")

        Log.d(TAG, "Binding to BuildService")
        val app = getApplication<Application>()
        Intent(app, BuildService::class.java).also { intent ->
            isServiceRegistered = app.bindService(intent, buildServiceConnection, Context.BIND_AUTO_CREATE)
        }
    }

    fun unbindBuildService(context: Context) {
        Log.d(TAG, "unbindBuildService called")
        if (isServiceRegistered) {
            Log.d(TAG, "Unbinding from BuildService")
            try {
                getApplication<Application>().unbindService(buildServiceConnection)
            } catch (e: Exception) {
                Log.e(TAG, "Error unbinding service", e)
            }
            isServiceRegistered = false
        }
        isBuildServiceBound = false
        buildService = null
    }

    fun clearLog() {
        Log.d(TAG, "clearLog called")
        _buildLog.value = ""
        _aiLog.value = ""
    }

    fun showRectPrompt(rect: Rect) {
        _promptForRect.value = rect
    }

    fun dismissRectPrompt() {
        _promptForRect.value = null
    }

    fun onNodePromptSubmitted(resourceId: String, prompt: String, bounds: Rect) {
        Log.d(TAG, "onNodePromptSubmitted: resourceId=$resourceId, prompt='$prompt', bounds=$bounds")

        pendingRect = bounds

        viewModelScope.launch {
            val appName = settingsViewModel.getAppName()
            if (appName.isNullOrBlank()) {
                pendingRichPrompt = "Context: Error: No project loaded.\nUser Request: \"$prompt\""
                takeScreenshot(bounds)
                return@launch
            }

            val projectDir = getApplication<Application>().filesDir.resolve(appName)
            val currentMap = sourceMap

            // Background thread for IO
            val contextResult = withContext(Dispatchers.IO) {
                SourceContextHelper.resolveContext(resourceId, projectDir, currentMap)
            }

            if (!contextResult.isError) {
                pendingRichPrompt = """
                    Context (for element $resourceId):
                    File: ${contextResult.file}
                    Line: ${contextResult.line}
                    Code Snippet: ${contextResult.snippet}
                    
                    User Request: "$prompt"
                    """.trimIndent()
            } else {
                pendingRichPrompt = "Context: Element $resourceId (Error: ${contextResult.errorMessage})\nUser Request: \"$prompt\""
            }

            takeScreenshot(bounds)
        }
    }

    fun onRectPromptSubmitted(rect: Rect, prompt: String) {
        Log.d(TAG, "onRectPromptSubmitted: rect=$rect, prompt='$prompt'")
        dismissRectPrompt() // Hide the popup

        pendingRect = rect
        val richPrompt = """
        Context: Screen area Rect(${rect.left}, ${rect.top}, ${rect.right}, ${rect.bottom})
        
        User Request: "$prompt"
        """.trimIndent()

        pendingRichPrompt = richPrompt
        takeScreenshot(rect)
    }

    fun startInspection(context: Context) {
        Log.d(TAG, "startInspection called")
        context.sendBroadcast(Intent("com.hereliesaz.ideaz.START_INSPECTION"))
    }

    fun stopInspection(context: Context) {
        Log.d(TAG, "stopInspection called")
        context.sendBroadcast(Intent("com.hereliesaz.ideaz.STOP_INSPECTION"))
        confirmCancelTask()
    }

    fun startBuild(context: Context, projectDir: File? = null) {
        Log.d(TAG, "startBuild called")
        if (isBuildServiceBound) {
            viewModelScope.launch {
                _buildLog.value = "[INFO] Status: Building...\n"
                val targetDir = projectDir ?: getOrCreateProject(context)
                Log.d(TAG, "Project directory: ${targetDir.absolutePath}")
                buildService?.startBuild(targetDir.absolutePath, buildCallback)
            }
        } else {
            Log.w(TAG, "startBuild: Build service not bound")
            _buildLog.value += "[INFO] Status: Service not bound\n"
        }
    }

    private fun getOrCreateProject(context: Context): File {
        val appName = settingsViewModel.getAppName()
        if (appName.isNullOrBlank()) {
             // Fallback to legacy behavior if no app name set
             return File(extractLegacyProject(context))
        }

        val projectDir = context.filesDir.resolve(appName)
        if (!projectDir.exists()) {
            _buildLog.value += "[INFO] Project '$appName' not found. Creating from template...\n"
            val typeStr = settingsViewModel.getProjectType()
            val type = ProjectType.fromString(typeStr)
            createProjectFromTemplateInternal(context, type, projectDir)
        }
        return projectDir
    }

    private fun extractLegacyProject(context: Context): String {
        Log.d(TAG, "extractLegacyProject called")
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

    private fun createProjectFromTemplateInternal(context: Context, type: ProjectType, projectDir: File) {
        val templatePath = when (type) {
            ProjectType.WEB -> "templates/web"
            ProjectType.REACT_NATIVE -> "templates/react_native"
            ProjectType.FLUTTER -> "templates/flutter"
            else -> "project" // Default to Android (legacy path)
        }

        Log.d(TAG, "Creating project from template: $templatePath")
        projectDir.mkdirs()

        // Copy template files. Using copyAsset recursively.
        context.assets.list(templatePath)?.forEach {
            copyAsset(context, "$templatePath/$it", projectDir.resolve(it).absolutePath)
        }
    }

    private fun copyAsset(context: Context, assetPath: String, destPath: String) {
        Log.d(TAG, "copyAsset from '$assetPath' to '$destPath'")
        val assetManager = context.assets
        try {
            val files = assetManager.list(assetPath)
            if (files.isNullOrEmpty()) {
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

    fun clearBuildCaches(context: Context) {
        viewModelScope.launch {
            _buildLog.value += "[INFO] Clearing build caches...\n"
            try {
                val buildDir = File(context.filesDir, "build")
                val cacheDir = File(context.filesDir, "cache")
                val repoDir = File(context.filesDir, "local-repo")

                if (buildDir.exists()) buildDir.deleteRecursively()
                if (cacheDir.exists()) cacheDir.deleteRecursively()
                if (repoDir.exists()) repoDir.deleteRecursively()

                _buildLog.value += "[INFO] Build caches cleared successfully.\n"
            } catch (e: Exception) {
                Log.e(TAG, "Failed to clear caches", e)
                _buildLog.value += "[INFO] Error clearing caches: ${e.message}\n"
            }
        }
    }

    fun initializeProject(prompt: String?) {
        viewModelScope.launch {
            _buildLog.value += "[INFO] Checking for updates...\n"
            try {
                val appName = settingsViewModel.getAppName()
                if (!appName.isNullOrBlank()) {
                    val projectDir = getApplication<Application>().filesDir.resolve(appName)
                    if (projectDir.exists()) {
                        withContext(Dispatchers.IO) {
                            GitManager(projectDir).pull()
                        }
                        _buildLog.value += "[INFO] Project updated.\n"
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to pull", e)
                _buildLog.value += "[INFO] Warning: Failed to update project: ${e.message}\n"
            }

            if (!prompt.isNullOrBlank()) {
                sendPrompt(prompt, isInitialization = true)
            } else {
                _buildLog.value += "[INFO] No initial prompt provided. Skipping AI initialization.\n"
            }
        }
    }

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
            _aiLog.value = ""

            when (model.id) {
                AiModels.JULES_DEFAULT -> {
                    Log.d(TAG, "Using Jules API for contextless prompt")
                    try {
                        val appName = settingsViewModel.getAppName()
                        val githubUser = settingsViewModel.getGithubUser()

                        if (appName.isNullOrBlank() || githubUser.isNullOrBlank()) {
                            _buildLog.value += "[INFO] AI Status: Error: Project not configured (App Name or GitHub User missing). Please go to Setup tab.\n"
                            return@launch
                        }

                        val branchName = settingsViewModel.getBranchName()
                        val sourceString = "sources/github/$githubUser/$appName"

                        val promptText = prompt ?: ""
                        if (promptText.isBlank()) {
                            _buildLog.value += "[INFO] AI Status: Error: Prompt cannot be empty.\n"
                            return@launch
                        }

                        // Check active session
                        val activeId = _activeSessionId.value
                        if (activeId != null) {
                            _buildLog.value += "[INFO] Sending message to existing session $activeId...\n"
                            JulesApiClient.sendMessage(activeId, promptText)
                            pollForPatch(activeId, _buildLog)
                            return@launch
                        }

                        val request = CreateSessionRequest(
                            prompt = promptText,
                            sourceContext = SourceContext(
                                source = sourceString,
                                githubRepoContext = GitHubRepoContext(startingBranch = branchName)
                            )
                        )

                        val session = JulesApiClient.createSession(request)
                        val sessionId = session.name.substringAfterLast("/")

                        _buildLog.value += "[INFO] Jules session created. ID: $sessionId\n"
                        _activeSessionId.value = sessionId
                        _buildLog.value += "[INFO] AI Status: Session created. Waiting for patch...\n"

                    } catch (e: Exception) {
                        Log.e(TAG, "Error creating Jules session", e)
                        _buildLog.value += "[INFO] AI Status: Error: ${e.message}\n"
                    }
                }
                AiModels.GEMINI_FLASH -> {
                    Log.d(TAG, "Using Gemini Flash for contextless prompt")
                    val apiKey = settingsViewModel.getGoogleApiKey()
                    if (apiKey != null) {
                        val responseText = GeminiApiClient.generateContent(prompt ?: "", apiKey)
                        _buildLog.value += "[INFO] AI Response: $responseText\n"
                    }
                }
                AiModels.GEMINI_CLI -> {
                    Log.d(TAG, "Using Gemini CLI for contextless prompt")
                    val responseText = com.hereliesaz.ideaz.api.GeminiCliClient.generateContent(getApplication(), prompt ?: "")
                    _buildLog.value += "[INFO] AI Response: $responseText\n"
                    _buildLog.value += "[INFO] AI Status: Idle\n"
                }
            }
        }
    }

    private fun startContextualAITask(richPrompt: String) {
        Log.d(TAG, "startContextualAITask")
        pendingRect?.let {
            sendOverlayBroadcast(Intent("com.hereliesaz.ideaz.SHOW_LOG_UI").apply { putExtra("RECT", it) })
        }
        pendingRect = null

        logToOverlay("Sending prompt to AI...")

        val model = getAssignedModelForTask(SettingsViewModel.KEY_AI_ASSIGNMENT_OVERLAY)
        if (model == null) {
            logToOverlay("Error: No AI model assigned.")
            return
        }
        Log.d(TAG, "Assigned model for overlay task: ${model.displayName}")

        if (settingsViewModel.getApiKey(model.requiredKey).isNullOrBlank()) {
            logToOverlay("Error: API Key missing.")
            return
        }

        contextualTaskJob = viewModelScope.launch {
            when (model.id) {
                AiModels.JULES_DEFAULT -> {
                    Log.d(TAG, "Using Jules API for overlay task")
                    try {
                        val appName = settingsViewModel.getAppName()
                        val githubUser = settingsViewModel.getGithubUser()

                        if (appName.isNullOrBlank() || githubUser.isNullOrBlank()) {
                            logToOverlay("Error: Project not configured (App Name or GitHub User missing). Please go to Setup tab.")
                            return@launch
                        }

                        val branchName = settingsViewModel.getBranchName()
                        val sourceString = "sources/github/$githubUser/$appName"

                        val request = CreateSessionRequest(
                            prompt = richPrompt,
                            sourceContext = SourceContext(
                                source = sourceString,
                                githubRepoContext = GitHubRepoContext(startingBranch = branchName)
                            )
                        )

                        val session = JulesApiClient.createSession(request)
                        val sessionName = session.name

                        logToOverlay("Session created. Waiting for patch...")
                        pollForPatch(sessionName, "OVERLAY")

                    } catch (e: Exception) {
                        Log.e(TAG, "Error creating Jules session for overlay task", e)
                        logToOverlay("Error: ${e.message}")
                        logToOverlay("Task Finished.")
                    }
                }
                AiModels.GEMINI_FLASH -> {
                    Log.d(TAG, "Using Gemini Flash for overlay task")
                    val apiKey = settingsViewModel.getApiKey(model.requiredKey)
                    if (apiKey != null) {
                        val response = GeminiApiClient.generateContent(richPrompt, apiKey)
                        logToOverlay(response)
                    }
                }
                AiModels.GEMINI_CLI -> {
                    val response = com.hereliesaz.ideaz.api.GeminiCliClient.generateContent(getApplication(), richPrompt)
                    logToOverlay(response)
                }
            }
        }
    }

    fun requestCancelTask() {
        Log.d(TAG, "requestCancelTask called")
        if (settingsViewModel.getShowCancelWarning()) {
            Log.d(TAG, "Showing cancel warning dialog")
            _showCancelDialog.value = true
        } else {
            Log.d(TAG, "Skipping cancel warning dialog and confirming cancellation")
            confirmCancelTask()
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
        if (resultCode == AndroidActivity.RESULT_OK && data != null) {
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
        pendingRichPrompt = null

        val finalRichPrompt = """
        $prompt
        
        [IMAGE: data:image/png;base64,$base64]
        """.trimIndent()

        startContextualAITask(finalRichPrompt)
    }

    private fun getAssignedModelForTask(taskKey: String): AiModel? {
        Log.d(TAG, "getAssignedModelForTask called for taskKey: $taskKey")
        val modelId = settingsViewModel.getAiAssignment(taskKey)
        Log.d(TAG, "Retrieved modelId: $modelId")
        val model = AiModels.findById(modelId)
        Log.d(TAG, "Found model: ${model?.displayName}")
        return model
    }

    fun fetchOwnedSources() {
        viewModelScope.launch {
            _isLoadingSources.value = true
            Log.d(TAG, "fetchOwnedSources: Fetching sources...")
            try {
                val response = JulesApiClient.listSources()
                _ownedSources.value = response.sources ?: emptyList()
                Log.d(TAG, "fetchOwnedSources: Success. Found ${response.sources?.size ?: 0} sources.")
            } catch (e: Exception) {
                Log.e(TAG, "fetchOwnedSources: Failed to fetch sources", e)
                _ownedSources.value = emptyList()
            } finally {
                _isLoadingSources.value = false
            }
        }
    }

    fun fetchSessions() {
        viewModelScope.launch {
            try {
                val response = JulesApiClient.listSessions()
                val appName = settingsViewModel.getAppName()
                val githubUser = settingsViewModel.getGithubUser()
                val currentSource = "sources/github/$githubUser/$appName"

                val filtered = response.sessions?.filter {
                    it.sourceContext.source.equals(currentSource, ignoreCase = true)
                } ?: emptyList()

                _availableSessions.value = filtered
            } catch (e: Exception) {
                Log.e(TAG, "Failed to list sessions", e)
            }
        }
    }

    fun setActiveSession(sessionId: String) {
        _activeSessionId.value = sessionId
        _buildLog.value += "[INFO] Active session set to: $sessionId\n"
    }

    fun loadLastProject(context: Context) {
        val lastApp = settingsViewModel.getAppName()
        if (!lastApp.isNullOrBlank()) {
            Log.d(TAG, "Auto-loading last project: $lastApp")
            loadProject(lastApp)
        }
    }

    fun cloneProject(url: String, name: String) {
        viewModelScope.launch {
            _buildLog.value += "[INFO] Cloning project from $url...\n"
            try {
                val projectDir = getApplication<Application>().filesDir.resolve(name)
                if (projectDir.exists()) {
                    projectDir.deleteRecursively()
                }
                projectDir.mkdirs()

                val urlParts = url.split("/")
                val owner = urlParts[urlParts.size - 2]
                val repo = urlParts.last().removeSuffix(".git")

                val gitManager = GitManager(projectDir)
                gitManager.clone(owner, repo)
                settingsViewModel.addProject(name)
                _buildLog.value += "[INFO] Project '$name' cloned successfully.\n"
            } catch (e: Exception) {
                Log.e(TAG, "Failed to clone project", e)
                _buildLog.value += "[INFO] Error cloning project: ${e.message}\n"
            }
        }
    }

    fun loadProject(projectName: String) {
        viewModelScope.launch {
            _buildLog.value += "[INFO] Loading project '$projectName'...\n"
            try {
                val projectDir = getApplication<Application>().filesDir.resolve(projectName)
                if (!projectDir.exists()) {
                    _buildLog.value += "[INFO] Error: Project '$projectName' not found.\n"
                    return@launch
                }
                settingsViewModel.setAppName(projectName)
                settingsViewModel.setGithubUser("")

                val type = ProjectAnalyzer.detectProjectType(projectDir)
                settingsViewModel.setProjectType(type.displayName)

                val pkg = ProjectAnalyzer.detectPackageName(projectDir)
                if (pkg != null) {
                    settingsViewModel.saveTargetPackageName(pkg)
                }

                fetchSessions()

                _buildLog.value += "[INFO] Project '$projectName' loaded successfully (Type: ${type.displayName}).\n"
            } catch (e: Exception) {
                Log.e(TAG, "Failed to load project", e)
                _buildLog.value += "[INFO] Error loading project: ${e.message}\n"
            }
        }
    }

    fun loadProjectAndBuild(context: Context, projectName: String) {
        viewModelScope.launch {
            _buildLog.value += "[INFO] Loading project '$projectName'...\n"
            try {
                val projectDir = getApplication<Application>().filesDir.resolve(projectName)
                if (!projectDir.exists()) {
                    _buildLog.value += "[INFO] Error: Project '$projectName' not found.\n"
                    return@launch
                }
                settingsViewModel.setAppName(projectName)
                settingsViewModel.setGithubUser("")

                val type = ProjectAnalyzer.detectProjectType(projectDir)
                settingsViewModel.setProjectType(type.displayName)

                val pkg = ProjectAnalyzer.detectPackageName(projectDir)
                if (pkg != null) {
                    settingsViewModel.saveTargetPackageName(pkg)
                }

                fetchSessions()

                _buildLog.value += "[INFO] Project '$projectName' loaded successfully (Type: ${type.displayName}).\n"

                startBuild(context, projectDir)

            } catch (e: Exception) {
                Log.e(TAG, "Failed to load project", e)
                _buildLog.value += "[INFO] Error loading project: ${e.message}\n"
            }
        }
    }

    fun createProjectFromTemplate(context: Context, templateType: String, projectName: String) {
        viewModelScope.launch {
            _buildLog.value += "[INFO] Creating project '$projectName' from template '$templateType'...\n"
            try {
                val projectDir = context.filesDir.resolve(projectName)
                if (projectDir.exists()) {
                    _buildLog.value += "[INFO] Cleaning up existing directory...\n"
                    projectDir.deleteRecursively()
                }

                val type = when (templateType.lowercase()) {
                    "web" -> ProjectType.WEB
                    "react_native" -> ProjectType.REACT_NATIVE
                    "flutter" -> ProjectType.FLUTTER
                    else -> ProjectType.ANDROID
                }

                createProjectFromTemplateInternal(context, type, projectDir)

                settingsViewModel.setAppName(projectName)
                settingsViewModel.setGithubUser("") // Local project
                settingsViewModel.setProjectType(type.name)
                _buildLog.value += "[INFO] Project '$projectName' created successfully.\n"

            } catch (e: Exception) {
                Log.e(TAG, "Failed to create project from template", e)
                _buildLog.value += "[INFO] Error creating project: ${e.message}\n"
            }
        }
    }

    fun debugBuild() {
        Log.d(TAG, "debugBuild called")
        val model = getAssignedModelForTask(SettingsViewModel.KEY_AI_ASSIGNMENT_CONTEXTLESS)
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
                        val appName = settingsViewModel.getAppName()
                        val githubUser = settingsViewModel.getGithubUser()

                        if (appName.isNullOrBlank() || githubUser.isNullOrBlank()) {
                            _buildLog.value += "AI Status: Error: Project not configured (App Name or GitHub User missing). Please go to Setup tab.\n"
                            return@launch
                        }

                        val branchName = settingsViewModel.getBranchName()
                        val sourceString = "sources/github/$githubUser/$appName"

                        val request = CreateSessionRequest(
                            prompt = buildLog.value,
                            sourceContext = SourceContext(
                                source = sourceString,
                                githubRepoContext = GitHubRepoContext(startingBranch = branchName)
                            )
                        )

                        val session = JulesApiClient.createSession(request)
                        val sessionId = session.name.substringAfterLast("/")

                        _buildLog.value += "AI Status: Debug info sent. Waiting for new patch...\n"

                    } catch (e: Exception) {
                        Log.e(TAG, "Error during Jules debug", e)
                        _buildLog.value += "AI Status: Error debugging: ${e.message}\n"
                    }
                }
                AiModels.GEMINI_FLASH -> {
                    Log.d(TAG, "Debugging with Gemini Flash")
                    val apiKey = settingsViewModel.getApiKey(model.requiredKey)
                    if (apiKey != null) {
                        val response = GeminiApiClient.generateContent(buildLog.value, apiKey)
                        logToOverlay(response)
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

    private fun pollForPatch(sessionId: String, logTarget: Any, attempt: Int = 1) {
        val maxAttempts = 20
        Log.d(TAG, "Polling for patch for session $sessionId, attempt $attempt")
        logTo(logTarget, "[INFO] AI Status: Waiting for patch... (Attempt $attempt)")

        viewModelScope.launch {
            if (attempt > maxAttempts) {
                logTo(logTarget, "[INFO] AI Status: Error: Timed out waiting for patch.")
                if (logTarget == "OVERLAY") sendOverlayBroadcast(Intent("com.hereliesaz.ideaz.TASK_FINISHED"))
                return@launch
            }

            try {
                // FALLBACK LOGIC for 404 issue
                var response: ListActivitiesResponse? = null
                try {
                    response = JulesApiClient.listActivities(sessionId)
                } catch (e: Exception) {
                    if (e.message?.contains("404") == true) {
                        Log.w(TAG, "API listActivities 404. Attempting CLI fallback.")
                        // Try to strip prefix for CLI if needed, or use full name. CLI usually takes ID or Name.
                        // Assuming CLI handles the lookup.
                        val cliOutput = JulesCliClient.listActivities(getApplication(), sessionId)
                        if (cliOutput != null) {
                            response = json.decodeFromString<ListActivitiesResponse>(cliOutput)
                        }
                    } else {
                        throw e
                    }
                }

                val patchActivity = response?.activities?.find { activity ->
                    activity.artifacts?.any { it.changeSet?.gitPatch?.unidiffPatch != null } == true
                }

                if (patchActivity != null) {
                    val patchContent = patchActivity.artifacts
                        ?.firstOrNull { it.changeSet?.gitPatch?.unidiffPatch != null }
                        ?.changeSet?.gitPatch?.unidiffPatch

                    if (patchContent != null) {
                        logTo(logTarget, "[INFO] AI Status: Patch is ready! Applying...")
                        applyPatch(getApplication(), patchContent, logTarget)
                    } else {
                        delay(15000) // Tripled wait period
                        pollForPatch(sessionId, logTarget, attempt + 1)
                    }
                } else {
                    delay(15000) // Tripled wait period
                    pollForPatch(sessionId, logTarget, attempt + 1)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error polling for patch", e)
                val urlInfo = if (e is retrofit2.HttpException) {
                    "URL: ${e.response()?.raw()?.request?.url} - "
                } else ""
                logTo(logTarget, "[INFO] AI Status: Error polling for patch: $urlInfo${e.message}")
                if (logTarget == "OVERLAY") sendOverlayBroadcast(Intent("com.hereliesaz.ideaz.TASK_FINISHED"))
            }
        }
    }

    private fun applyPatch(context: Context, patchContent: String, logTarget: Any) {
        Log.d(TAG, "applyPatch called")
        viewModelScope.launch {
            try {
                logTo(logTarget, "[INFO] AI Status: Applying patch...")
                val appName = settingsViewModel.getAppName()
                val projectDir = if (!appName.isNullOrBlank()) {
                    context.filesDir.resolve(appName)
                } else {
                    context.filesDir.resolve("project")
                }

                val gitManager = GitManager(projectDir)
                gitManager.applyPatch(patchContent)
                logTo(logTarget, "[INFO] AI Status: Patch applied. Rebuilding...")
                startBuild(context, projectDir)

            } catch (e: Exception) {
                Log.e(TAG, "Error applying patch", e)
                logTo(logTarget, "[INFO] AI Status: Error applying patch: ${e.message}")
                if (logTarget == "OVERLAY") sendOverlayBroadcast(Intent("com.hereliesaz.ideaz.TASK_FINISHED"))
            }
        }
    }
}
