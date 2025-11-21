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
import com.hereliesaz.ideaz.utils.ProjectConfigManager
import com.hereliesaz.ideaz.models.IdeazProjectConfig
import com.hereliesaz.ideaz.api.GitHubApiClient
import com.hereliesaz.ideaz.api.CreateRepoRequest
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.Json
import org.json.JSONObject
import com.hereliesaz.ideaz.utils.GithubIssueReporter

data class ProjectMetadata(
    val name: String,
    val sizeBytes: Long
)

class MainViewModel(
    application: Application,
    val settingsViewModel: SettingsViewModel
) : AndroidViewModel(application) {

    private val TAG = "MainViewModel"

    private val _loadingProgress = MutableStateFlow<Int?>(null)
    val loadingProgress = _loadingProgress.asStateFlow()

    private val gitMutex = Mutex()

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

    // --- NEW: Centralized Error Handling with API Support ---
    private fun handleIdeError(e: Exception, contextMessage: String) {
        Log.e(TAG, contextMessage, e)
        _buildLog.value += "[IDE ERROR] $contextMessage: ${e.message}\n"

        if (settingsViewModel.getAutoReportBugs()) {
            _buildLog.value += "[IDE] Reporting internal error to GitHub...\n"
            viewModelScope.launch(Dispatchers.IO) {
                val token = settingsViewModel.getGithubToken()
                val result = GithubIssueReporter.reportError(getApplication(), token, e, contextMessage)
                _buildLog.value += "[IDE] $result\n"
            }
        } else {
            _buildLog.value += "[IDE] Auto-report is disabled in settings.\n"
        }
    }
    // --- END NEW ---

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

                _buildLog.value += "[IDE] Waiting for installation to complete to launch app...\n"
            }
        }

        override fun onFailure(log: String) {
            Log.e(TAG, "onFailure: Build failed with log:\n$log")
            viewModelScope.launch {
                _buildLog.value += "\n[IDE] Build failed:\n$log\n"
                _buildLog.value += "[IDE] Status: Build Failed\n"
                contextualTaskJob = null
                logToOverlay("Build failed. See global log to debug.")

                // --- FIX: Distinguish System vs User Errors ---
                // If the logs contain specific system failures (tools missing), report to GitHub.
                // Otherwise, assume user code error and use AI Debugger.
                if (log.contains("tools not found", ignoreCase = true)) {
                    handleIdeError(Exception("Build Toolchain Verification Failed: $log"), "Build Toolchain Error")
                } else {
                    _buildLog.value += "[IDE] AI Status: Build failed, asking AI to debug...\n"
                    debugBuild()
                }
                // --- END FIX ---
            }
        }
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
                handleIdeError(e, "Failed to fetch sources")
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
                // Log but don't crash or report aggressively
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

    fun cloneOrPullProject(owner: String, repo: String, branch: String) {
        val appName = repo
        settingsViewModel.saveProjectConfig(appName, owner, branch)
        settingsViewModel.addProject("$owner/$appName")
        settingsViewModel.setAppName(appName)
        settingsViewModel.setGithubUser(owner)

        val token = settingsViewModel.getGithubToken()
        val authUser = settingsViewModel.getGithubUser()

        viewModelScope.launch {
            gitMutex.withLock {
                _buildLog.value += "[INFO] Selecting repository '$owner/$appName'...\n"
                val projectDir = getApplication<Application>().filesDir.resolve(appName)

                try {
                    if (projectDir.exists() && File(projectDir, ".git").exists()) {
                        _buildLog.value += "[INFO] Project exists. Pulling latest changes...\n"
                        withContext(Dispatchers.IO) {
                            GitManager(projectDir).pull(authUser, token) { percent, task ->
                                _loadingProgress.value = percent
                            }
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
                            GitManager(projectDir).clone(owner, repo, authUser, token) { percent, task ->
                                _loadingProgress.value = percent
                            }
                        }
                        _buildLog.value += "[INFO] Clone complete.\n"
                    }
                    _loadingProgress.value = null

                    val loadedConfig = ProjectConfigManager.loadConfig(projectDir)
                    if (loadedConfig != null) {
                        _buildLog.value += "[INFO] Loaded project config from .ideaz\n"
                        settingsViewModel.setProjectType(loadedConfig.projectType)
                        if (loadedConfig.packageName != null) {
                            settingsViewModel.saveTargetPackageName(loadedConfig.packageName)
                        }
                    } else {
                        val type = ProjectAnalyzer.detectProjectType(projectDir)
                        settingsViewModel.setProjectType(type.name)
                        _buildLog.value += "[INFO] Detected project type: ${type.displayName}\n"

                        val pkg = ProjectAnalyzer.detectPackageName(projectDir)
                        if (pkg != null) {
                            settingsViewModel.saveTargetPackageName(pkg)
                            _buildLog.value += "[INFO] Detected package name: $pkg\n"
                        }

                        saveProjectConfigToFile(projectDir, type.name, pkg ?: "com.example", branch)
                    }

                    fetchSessions()
                    startBuild(getApplication())

                } catch (e: Exception) {
                    handleIdeError(e, "Failed to clone/pull project")
                }
            }
        }
    }

    fun createGitHubRepository(
        appName: String,
        description: String,
        isPrivate: Boolean,
        projectType: ProjectType,
        packageName: String,
        context: Context,
        onSuccess: () -> Unit
    ) {
        viewModelScope.launch {
            val token = settingsViewModel.getGithubToken()
            if (token.isNullOrBlank()) {
                _buildLog.value += "[ERROR] GitHub token required to create repository.\n"
                return@launch
            }

            _buildLog.value += "[INFO] Creating repository '$appName' on GitHub...\n"

            try {
                val api = GitHubApiClient.createService(token)
                val response = withContext(Dispatchers.IO) {
                    api.createRepo(CreateRepoRequest(name = appName, description = description, private = isPrivate))
                }

                _buildLog.value += "[INFO] Repository created: ${response.htmlUrl}\n"

                settingsViewModel.saveProjectConfig(appName, response.fullName.split("/")[0], response.defaultBranch ?: "main")
                settingsViewModel.saveTargetPackageName(packageName)
                settingsViewModel.setProjectType(projectType.name)
                settingsViewModel.setAppName(appName)

                val projectDir = context.filesDir.resolve(appName)

                gitMutex.withLock {
                    if (projectDir.exists()) projectDir.deleteRecursively()
                    projectDir.mkdirs()

                    _buildLog.value += "[INFO] Cloning new repository...\n"
                    withContext(Dispatchers.IO) {
                        GitManager(projectDir).clone(
                            response.fullName.split("/")[0],
                            appName,
                            settingsViewModel.getGithubUser(),
                            token
                        )
                    }

                    _buildLog.value += "[INFO] Applying ${projectType.displayName} template...\n"
                    createProjectFromTemplateInternal(context, projectType, projectDir)

                    ProjectConfigManager.ensureGitIgnore(projectDir)
                    saveProjectConfigToFile(projectDir, projectType.name, packageName, response.defaultBranch ?: "main")

                    _buildLog.value += "[INFO] Pushing initial commit...\n"
                    withContext(Dispatchers.IO) {
                        val git = GitManager(projectDir)
                        git.addAll()
                        git.commit("Initial commit via IDEaz")
                        git.push(settingsViewModel.getGithubUser(), token)
                    }

                    _buildLog.value += "[INFO] Initialization complete. Proceed to Setup to build.\n"
                }

                withContext(Dispatchers.Main) {
                    onSuccess()
                }

            } catch (e: Exception) {
                handleIdeError(e, "Failed to create repository")
            }
        }
    }

    private fun saveProjectConfigToFile(projectDir: File, type: String, pkg: String?, branch: String) {
        val config = IdeazProjectConfig(
            projectType = type,
            packageName = pkg,
            branch = branch
        )
        ProjectConfigManager.saveConfig(projectDir, config)
    }

    fun saveAndInitialize(
        appName: String,
        user: String,
        branch: String,
        pkg: String,
        type: ProjectType,
        context: Context,
        initialPrompt: String? = null
    ) {
        settingsViewModel.saveProjectConfig(appName, user, branch)
        settingsViewModel.saveTargetPackageName(pkg)
        settingsViewModel.setProjectType(type.name)
        settingsViewModel.setAppName(appName)

        viewModelScope.launch {
            gitMutex.withLock {
                _buildLog.value += "[INFO] Initializing project '$appName'...\n"
                try {
                    val projectDir = getApplication<Application>().filesDir.resolve(appName)
                    val token = settingsViewModel.getGithubToken()

                    if (projectDir.exists()) {
                        _buildLog.value += "[INFO] Project directory exists. Updating...\n"
                        withContext(Dispatchers.IO) {
                            val git = GitManager(projectDir)
                            git.pull(user, token) { percent, task ->
                                _loadingProgress.value = percent
                            }
                        }
                        _loadingProgress.value = null
                        _buildLog.value += "[INFO] Update complete.\n"
                    } else {
                        _buildLog.value += "[INFO] Project directory not found. Creating from template...\n"
                        createProjectFromTemplateInternal(context, type, projectDir)
                    }

                    ProjectConfigManager.ensureGitIgnore(projectDir)
                    saveProjectConfigToFile(projectDir, type.name, pkg, branch)

                    if (!initialPrompt.isNullOrBlank()) {
                        _buildLog.value += "[INFO] Saving initial prompt and sending to AI...\n"
                        logPromptToHistory(initialPrompt, null)
                    } else {
                        _buildLog.value += "[INFO] Starting initial build...\n"
                        startBuild(context, projectDir)
                    }

                } catch (e: Exception) {
                    handleIdeError(e, "Failed during saveAndInitialize")
                }
            }
            if (!initialPrompt.isNullOrBlank()) {
                sendPrompt(initialPrompt, isInitialization = true)
            }
        }
    }

    private fun logPromptToHistory(text: String, screenshotBase64: String?) {
        val appName = settingsViewModel.getAppName()
        if (!appName.isNullOrBlank()) {
            val projectDir = getApplication<Application>().filesDir.resolve(appName)
            if (projectDir.exists()) {
                ProjectConfigManager.appendPromptToHistory(projectDir, text, screenshotBase64)
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

                val loadedConfig = ProjectConfigManager.loadConfig(projectDir)
                if (loadedConfig != null) {
                    settingsViewModel.setProjectType(loadedConfig.projectType)
                    if (loadedConfig.packageName != null) {
                        settingsViewModel.saveTargetPackageName(loadedConfig.packageName)
                    }
                    _buildLog.value += "[INFO] Project config loaded from .ideaz (Type: ${loadedConfig.projectType})\n"
                } else {
                    val type = ProjectAnalyzer.detectProjectType(projectDir)
                    settingsViewModel.setProjectType(type.name)
                    _buildLog.value += "[INFO] Detected project type: ${type.displayName}\n"

                    val pkg = ProjectAnalyzer.detectPackageName(projectDir)
                    if (pkg != null) {
                        settingsViewModel.saveTargetPackageName(pkg)
                    }
                    saveProjectConfigToFile(projectDir, type.name, pkg ?: "com.example", "main")
                }

                fetchSessions()

                _buildLog.value += "[INFO] Project '$projectName' loaded successfully.\n"
            } catch (e: Exception) {
                handleIdeError(e, "Failed to load project")
            }
        }
    }

    fun loadProjectAndBuild(context: Context, projectName: String) {
        loadProject(projectName)
        val projectDir = getApplication<Application>().filesDir.resolve(projectName)
        startBuild(context, projectDir)
    }

    fun deleteSession(session: com.hereliesaz.ideaz.api.Session) {
        viewModelScope.launch {
            try {
                JulesApiClient.deleteSession(session.id)
                fetchSessions()
                _buildLog.value += "[INFO] Session ${session.id} deleted.\n"
            } catch (e: Exception) {
                handleIdeError(e, "Failed to delete session")
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
            gitMutex.withLock {
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
                    handleIdeError(e, "Try session failed")
                }
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
            gitMutex.withLock {
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
                    handleIdeError(e, "Accept session failed")
                }
            }
        }
    }

    fun sendPrompt(prompt: String?, isInitialization: Boolean = false) {
        Log.d(TAG, "sendPrompt called with prompt: '$prompt'")
        val taskKey = if (isInitialization) {
            SettingsViewModel.KEY_AI_ASSIGNMENT_INIT
        } else {
            SettingsViewModel.KEY_AI_ASSIGNMENT_CONTEXTLESS
        }

        _buildLog.value += "\n[INFO] Sending prompt: $prompt\n"

        if (!isInitialization) {
            logPromptToHistory(prompt ?: "", null)
        }

        val model = getAssignedModelForTask(taskKey)
        if (model == null) {
            _buildLog.value += "[INFO] Error: No AI model assigned.\n"
            return
        }

        if (settingsViewModel.getApiKey(model.requiredKey).isNullOrBlank()) {
            _buildLog.value += "[INFO] Error: API Key missing for ${model.displayName}.\n"
            return
        }

        viewModelScope.launch {
            _buildLog.value += "[INFO] AI Status: Sending...\n"
            _aiLog.value = ""

            when (model.id) {
                AiModels.JULES_DEFAULT -> {
                    try {
                        val appName = settingsViewModel.getAppName()
                        val githubUser = settingsViewModel.getGithubUser()

                        if (appName.isNullOrBlank() || githubUser.isNullOrBlank()) {
                            _buildLog.value += "[INFO] AI Status: Error: Project not configured.\n"
                            return@launch
                        }

                        val branchName = settingsViewModel.getBranchName()
                        val sourceString = "sources/github/$githubUser/$appName"
                        val promptText = prompt ?: ""

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
                        pollForPatch(sessionId, _buildLog)

                    } catch (e: Exception) {
                        handleIdeError(e, "Error creating Jules session")
                    }
                }
                AiModels.GEMINI_FLASH -> {
                    val apiKey = settingsViewModel.getGoogleApiKey()
                    if (apiKey != null) {
                        val responseText = GeminiApiClient.generateContent(prompt ?: "", apiKey)
                        _buildLog.value += "[INFO] AI Response: $responseText\n"
                    }
                }
                AiModels.GEMINI_CLI -> {
                    val responseText = com.hereliesaz.ideaz.api.GeminiCliClient.generateContent(getApplication(), prompt ?: "")
                    _buildLog.value += "[INFO] AI Response: $responseText\n"
                }
            }
        }
    }

    fun onNodePromptSubmitted(resourceId: String, prompt: String, bounds: Rect) {
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
        dismissRectPrompt()
        pendingRect = rect
        pendingRichPrompt = """
        Context: Screen area Rect(${rect.left}, ${rect.top}, ${rect.right}, ${rect.bottom})
        
        User Request: "$prompt"
        """.trimIndent()

        takeScreenshot(rect)
    }

    private fun takeScreenshot(rect: Rect) {
        if (!hasScreenCapturePermission()) {
            logToOverlay("Error: Missing screen capture permission.")
            return
        }
        val intent = Intent(getApplication(), ScreenshotService::class.java).apply {
            putExtra(ScreenshotService.EXTRA_RESULT_CODE, screenCaptureResultCode)
            putExtra(ScreenshotService.EXTRA_DATA, screenCaptureData)
            putExtra(ScreenshotService.EXTRA_RECT, rect)
        }
        getApplication<Application>().startForegroundService(intent)
    }

    fun onScreenshotTaken(base64: String) {
        val prompt = pendingRichPrompt ?: "Error: No pending prompt"
        pendingRichPrompt = null
        val finalRichPrompt = "$prompt\n\n[IMAGE: data:image/png;base64,$base64]"

        logPromptToHistory(prompt, base64)

        startContextualAITask(finalRichPrompt)
    }

    private fun startContextualAITask(richPrompt: String) {
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

        if (settingsViewModel.getApiKey(model.requiredKey).isNullOrBlank()) {
            logToOverlay("Error: API Key missing.")
            return
        }

        contextualTaskJob = viewModelScope.launch {
            when (model.id) {
                AiModels.JULES_DEFAULT -> {
                    try {
                        val appName = settingsViewModel.getAppName()
                        val githubUser = settingsViewModel.getGithubUser()
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
                        logToOverlay("Session created. Waiting for patch...")
                        pollForPatch(session.name, "OVERLAY")

                    } catch (e: Exception) {
                        logToOverlay("Error: ${e.message}")
                        logToOverlay("Task Finished.")
                        handleIdeError(e, "Contextual AI Task Failed")
                    }
                }
                AiModels.GEMINI_FLASH -> {
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
        if (settingsViewModel.getShowCancelWarning()) {
            _showCancelDialog.value = true
        } else {
            confirmCancelTask()
        }
    }

    fun confirmCancelTask() {
        contextualTaskJob?.cancel()
        contextualTaskJob = null
        _showCancelDialog.value = false
        logToOverlay("Task cancelled by user.")
        sendOverlayBroadcast(Intent("com.hereliesaz.ideaz.TASK_FINISHED"))
    }

    fun dismissCancelTask() { _showCancelDialog.value = false }
    fun disableCancelWarning() {
        settingsViewModel.setShowCancelWarning(false)
        confirmCancelTask()
    }

    fun hasScreenCapturePermission(): Boolean = screenCaptureData != null
    fun requestScreenCapturePermission() { _requestScreenCapture.value = true }
    fun screenCaptureRequestHandled() { _requestScreenCapture.value = false }

    fun setScreenCapturePermission(resultCode: Int, data: Intent?) {
        if (resultCode == AndroidActivity.RESULT_OK && data != null) {
            screenCaptureResultCode = resultCode
            screenCaptureData = data
        } else {
            screenCaptureResultCode = null
            screenCaptureData = null
            _buildLog.value += "Warning: Screen capture permission denied.\n"
        }
    }

    fun startInspection(context: Context) {
        context.sendBroadcast(Intent("com.hereliesaz.ideaz.START_INSPECTION"))
    }

    fun stopInspection(context: Context) {
        context.sendBroadcast(Intent("com.hereliesaz.ideaz.STOP_INSPECTION"))
        confirmCancelTask()
    }

    fun showRectPrompt(rect: Rect) { _promptForRect.value = rect }
    fun dismissRectPrompt() { _promptForRect.value = null }

    fun startBuild(context: Context, projectDir: File? = null) {
        if (isBuildServiceBound) {
            viewModelScope.launch {
                _buildLog.value = "[INFO] Status: Building...\n"
                val targetDir = projectDir ?: getOrCreateProject(context)
                buildService?.startBuild(targetDir.absolutePath, buildCallback)
            }
        } else {
            _buildLog.value += "[INFO] Status: Service not bound\n"
        }
    }

    private fun getOrCreateProject(context: Context): File {
        val appName = settingsViewModel.getAppName()
        if (appName.isNullOrBlank()) return File(extractLegacyProject(context))
        val projectDir = context.filesDir.resolve(appName)
        if (!projectDir.exists()) {
            val typeStr = settingsViewModel.getProjectType()
            val type = ProjectType.fromString(typeStr)
            createProjectFromTemplateInternal(context, type, projectDir)
        }
        return projectDir
    }

    private fun extractLegacyProject(context: Context): String {
        val projectDir = context.filesDir.resolve("project")
        if (projectDir.exists()) projectDir.deleteRecursively()
        projectDir.mkdirs()
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
            else -> "project"
        }
        projectDir.mkdirs()
        context.assets.list(templatePath)?.forEach {
            copyAsset(context, "$templatePath/$it", projectDir.resolve(it).absolutePath)
        }
    }

    fun createProjectFromTemplate(context: Context, templateType: String, projectName: String) {
        viewModelScope.launch {
            try {
                val projectDir = context.filesDir.resolve(projectName)
                if (projectDir.exists()) projectDir.deleteRecursively()
                val type = when (templateType.lowercase()) {
                    "web" -> ProjectType.WEB
                    "react_native" -> ProjectType.REACT_NATIVE
                    "flutter" -> ProjectType.FLUTTER
                    else -> ProjectType.ANDROID
                }
                createProjectFromTemplateInternal(context, type, projectDir)
                settingsViewModel.setAppName(projectName)
                settingsViewModel.setProjectType(type.name)
                _buildLog.value += "[INFO] Project '$projectName' created.\n"
            } catch (e: Exception) {
                handleIdeError(e, "Error creating project from template")
            }
        }
    }

    private fun copyAsset(context: Context, assetPath: String, destPath: String) {
        val assetManager = context.assets
        try {
            val files = assetManager.list(assetPath)
            if (files.isNullOrEmpty()) {
                assetManager.open(assetPath).use { input ->
                    FileOutputStream(destPath).use { output -> input.copyTo(output) }
                }
            } else {
                val dir = File(destPath)
                if (!dir.exists()) dir.mkdirs()
                files.forEach { copyAsset(context, "$assetPath/$it", "$destPath/$it") }
            }
        } catch (e: IOException) {
            try {
                assetManager.open(assetPath).use { input ->
                    FileOutputStream(destPath).use { output -> input.copyTo(output) }
                }
            } catch (e2: Exception) {}
        }
    }

    fun clearBuildCaches(context: Context) {
        viewModelScope.launch {
            try {
                val buildDir = File(context.filesDir, "build")
                val cacheDir = File(context.filesDir, "cache")
                val repoDir = File(context.filesDir, "local-repo")
                if (buildDir.exists()) buildDir.deleteRecursively()
                if (cacheDir.exists()) cacheDir.deleteRecursively()
                if (repoDir.exists()) repoDir.deleteRecursively()
                _buildLog.value += "[INFO] Build caches cleared.\n"
            } catch (e: Exception) {
                handleIdeError(e, "Error clearing caches")
            }
        }
    }

    fun debugBuild() {
        val model = getAssignedModelForTask(SettingsViewModel.KEY_AI_ASSIGNMENT_CONTEXTLESS) ?: return
        if (settingsViewModel.getApiKey(model.requiredKey).isNullOrBlank()) {
            _buildLog.value += "Error: API Key missing for debug.\n"
            return
        }
        viewModelScope.launch {
            _buildLog.value += "AI Status: Debugging build failure...\n"
            when (model.id) {
                AiModels.JULES_DEFAULT -> {
                    try {
                        val appName = settingsViewModel.getAppName()
                        val githubUser = settingsViewModel.getGithubUser()
                        val branchName = settingsViewModel.getBranchName()
                        val sourceString = "sources/github/$githubUser/$appName"
                        val request = CreateSessionRequest(
                            prompt = buildLog.value,
                            sourceContext = SourceContext(source = sourceString, githubRepoContext = GitHubRepoContext(startingBranch = branchName))
                        )
                        val session = JulesApiClient.createSession(request)
                        _buildLog.value += "AI Status: Debug info sent. Waiting for patch...\n"
                        pollForPatch(session.name.substringAfterLast("/"), _buildLog)
                    } catch (e: Exception) {
                        handleIdeError(e, "Error debugging build")
                    }
                }
                AiModels.GEMINI_FLASH -> {
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
        if (target == null) return
        when (target) {
            is MutableStateFlow<*> -> (target as? MutableStateFlow<String>)?.value += "$message\n"
            "OVERLAY" -> logToOverlay(message)
        }
    }

    private fun logToOverlay(message: String) {
        sendOverlayBroadcast(Intent("com.hereliesaz.ideaz.AI_LOG").apply { putExtra("MESSAGE", message) })
    }

    private fun sendOverlayBroadcast(intent: Intent) {
        getApplication<Application>().sendBroadcast(intent)
    }

    private fun pollForPatch(sessionId: String, logTarget: Any, attempt: Int = 1) {
        if (attempt > 20) {
            logTo(logTarget, "[INFO] AI Status: Error: Timed out waiting for patch.")
            if (logTarget == "OVERLAY") sendOverlayBroadcast(Intent("com.hereliesaz.ideaz.TASK_FINISHED"))
            return
        }
        viewModelScope.launch {
            try {
                val response = JulesApiClient.listActivities(sessionId)
                val patchActivity = response.activities?.find { it.artifacts?.any { a -> a.changeSet?.gitPatch?.unidiffPatch != null } == true }
                if (patchActivity != null) {
                    val patchContent = patchActivity.artifacts?.firstOrNull { it.changeSet?.gitPatch?.unidiffPatch != null }?.changeSet?.gitPatch?.unidiffPatch
                    if (patchContent != null) {
                        logTo(logTarget, "[INFO] AI Status: Patch is ready! Applying...")
                        applyPatch(getApplication(), patchContent, logTarget)
                    } else {
                        delay(15000); pollForPatch(sessionId, logTarget, attempt + 1)
                    }
                } else {
                    delay(15000); pollForPatch(sessionId, logTarget, attempt + 1)
                }
            } catch (e: Exception) {
                logTo(logTarget, "[INFO] AI Status: Error polling for patch: ${e.message}")
                if (logTarget == "OVERLAY") sendOverlayBroadcast(Intent("com.hereliesaz.ideaz.TASK_FINISHED"))
            }
        }
    }

    private fun applyPatch(context: Context, patchContent: String, logTarget: Any) {
        viewModelScope.launch {
            gitMutex.withLock {
                try {
                    logTo(logTarget, "[INFO] AI Status: Applying patch...")
                    val appName = settingsViewModel.getAppName()
                    val projectDir = context.filesDir.resolve(appName ?: "project")
                    val gitManager = GitManager(projectDir)
                    gitManager.applyPatch(patchContent)
                    logTo(logTarget, "[INFO] AI Status: Patch applied. Rebuilding...")
                    startBuild(context, projectDir)
                } catch (e: Exception) {
                    logTo(logTarget, "[INFO] AI Status: Error applying patch: ${e.message}")
                    if (logTarget == "OVERLAY") sendOverlayBroadcast(Intent("com.hereliesaz.ideaz.TASK_FINISHED"))
                    handleIdeError(e, "Error applying patch")
                }
            }
        }
    }

    fun getLocalProjectsWithMetadata(): List<ProjectMetadata> {
        val projects = settingsViewModel.getProjectList()
        return projects.map { name ->
            val dir = getApplication<Application>().filesDir.resolve(name)
            val size = if (dir.exists()) dir.walkTopDown().sumOf { it.length() } else 0L
            ProjectMetadata(name, size)
        }
    }

    fun deleteProject(projectName: String) {
        viewModelScope.launch {
            val projectDir = getApplication<Application>().filesDir.resolve(projectName)
            if (projectDir.exists()) projectDir.deleteRecursively()
            settingsViewModel.removeProject(projectName)
            if (settingsViewModel.getAppName() == projectName) settingsViewModel.setAppName("")
            _buildLog.value += "[INFO] Project '$projectName' deleted.\n"
        }
    }

    fun syncAndDeleteProject(projectName: String) {
        viewModelScope.launch {
            gitMutex.withLock {
                val projectDir = getApplication<Application>().filesDir.resolve(projectName)
                if (projectDir.exists()) {
                    try {
                        withContext(Dispatchers.IO) {
                            val git = GitManager(projectDir)
                            git.addAll()
                            git.commit("Sync before delete")
                            git.push(settingsViewModel.getGithubUser(), settingsViewModel.getGithubToken())
                        }
                        _buildLog.value += "[INFO] Sync complete.\n"
                    } catch (e: Exception) {
                        _buildLog.value += "[INFO] Error syncing project: ${e.message}. Deleting anyway...\n"
                    }
                }
                deleteProject(projectName)
            }
        }
    }

    fun bindBuildService(context: Context) {
        filteredLog = combine(buildLog, aiLog, settingsViewModel.logLevel) { build, ai, level ->
            "$build\n$ai"
        }.stateIn(viewModelScope, SharingStarted.Lazily, "")
        val app = getApplication<Application>()
        Intent(app, BuildService::class.java).also { intent ->
            isServiceRegistered = app.bindService(intent, buildServiceConnection, Context.BIND_AUTO_CREATE)
        }
    }

    fun unbindBuildService(context: Context) {
        if (isServiceRegistered) {
            try { getApplication<Application>().unbindService(buildServiceConnection) } catch (e: Exception) {}
            isServiceRegistered = false
        }
        isBuildServiceBound = false
        buildService = null
    }

    fun clearLog() { _buildLog.value = ""; _aiLog.value = "" }

    private fun getAssignedModelForTask(taskKey: String): AiModel? {
        val modelId = settingsViewModel.getAiAssignment(taskKey)
        return AiModels.findById(modelId)
    }
}