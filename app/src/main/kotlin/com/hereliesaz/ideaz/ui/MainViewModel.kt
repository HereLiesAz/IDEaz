package com.hereliesaz.ideaz.ui

import android.app.Activity as AndroidActivity
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.BroadcastReceiver
import android.content.ServiceConnection
import android.graphics.Rect
import android.app.Application
import android.net.Uri
import android.os.IBinder
import android.util.Log
import androidx.documentfile.provider.DocumentFile
import androidx.core.content.ContextCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.hereliesaz.ideaz.IBuildCallback
import com.hereliesaz.ideaz.IBuildService
import com.hereliesaz.ideaz.jules.JulesApiClient
import com.hereliesaz.ideaz.git.GitManager
import com.hereliesaz.ideaz.services.BuildService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.isActive
import java.io.File
import com.hereliesaz.ideaz.models.SourceMapEntry
import com.hereliesaz.ideaz.utils.SourceMapParser
import com.hereliesaz.ideaz.services.ScreenshotService
import com.hereliesaz.ideaz.api.GeminiApiClient
import com.hereliesaz.ideaz.api.CreateSessionRequest
import com.hereliesaz.ideaz.api.SourceContext
import com.hereliesaz.ideaz.api.GitHubRepoContext
import com.hereliesaz.ideaz.api.Source
import com.hereliesaz.ideaz.buildlogic.HttpDependencyResolver
import com.hereliesaz.ideaz.utils.SourceContextHelper
import com.hereliesaz.ideaz.models.IdeazProjectConfig
import com.hereliesaz.ideaz.models.ProjectType // Added missing import
import com.hereliesaz.ideaz.api.GitHubApiClient
import com.hereliesaz.ideaz.api.CreateRepoRequest
import com.hereliesaz.ideaz.utils.GithubIssueReporter
import com.hereliesaz.ideaz.utils.ProjectAnalyzer
import com.hereliesaz.ideaz.utils.ProjectConfigManager
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.Json
import org.json.JSONArray
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.io.FileOutputStream
import kotlinx.coroutines.async
import java.util.zip.ZipInputStream
import java.time.Instant
import com.hereliesaz.ideaz.utils.ApkInstaller

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

    private val _isTargetAppVisible = MutableStateFlow(false)
    val isTargetAppVisible = _isTargetAppVisible.asStateFlow()

    private val gitMutex = Mutex()
    private var lastGitTask = ""

    private val _buildLog = MutableStateFlow("")
    val buildLog = _buildLog.asStateFlow()

    private val _aiLog = MutableStateFlow("")
    private val aiLog = _aiLog.asStateFlow()

    lateinit var filteredLog: StateFlow<List<String>>

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

    private val _isContextualChatVisible = MutableStateFlow(false)
    val isContextualChatVisible = _isContextualChatVisible.asStateFlow()

    private val _activeSelectionRect = MutableStateFlow<Rect?>(null)
    val activeSelectionRect = _activeSelectionRect.asStateFlow()

    private val _requestScreenCapture = MutableStateFlow(false)
    val requestScreenCapture = _requestScreenCapture.asStateFlow()
    private var screenCaptureResultCode: Int? = null
    private var screenCaptureData: Intent? = null
    private var pendingContextInfo: String? = null
    private var pendingBase64Screenshot: String? = null
    private var pendingRect: Rect? = null

    private val _commitHistory = MutableStateFlow<List<String>>(emptyList())
    val commitHistory = _commitHistory.asStateFlow()

    private val _branches = MutableStateFlow<List<String>>(emptyList())
    val branches = _branches.asStateFlow()

    private val _gitStatus = MutableStateFlow<List<String>>(emptyList())
    val gitStatus = _gitStatus.asStateFlow()

    private val visibilityReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == "com.hereliesaz.ideaz.TARGET_APP_VISIBILITY") {
                val visible = intent.getBooleanExtra("IS_VISIBLE", false)
                _isTargetAppVisible.value = visible
            }
        }
    }

    init {
        val filter = IntentFilter("com.hereliesaz.ideaz.TARGET_APP_VISIBILITY")
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            getApplication<Application>().registerReceiver(visibilityReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            ContextCompat.registerReceiver(
                getApplication<Application>(),
                visibilityReceiver,
                filter,
                ContextCompat.RECEIVER_NOT_EXPORTED
            )
        }

        viewModelScope.launch {
            settingsViewModel.apiKey.collect { key ->
                if (!key.isNullOrBlank()) {
                    fetchOwnedSources()
                    fetchSessions()
                }
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        unbindBuildService(getApplication())
        try { getApplication<Application>().unregisterReceiver(visibilityReceiver) } catch (e: Exception) {}
    }

    // --- Service Connection ---
    private val buildServiceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            buildService = IBuildService.Stub.asInterface(service)
            isBuildServiceBound = true
        }
        override fun onServiceDisconnected(name: ComponentName?) {
            buildService = null
            isBuildServiceBound = false
        }
    }

    // --- Build Callback ---
    private val buildCallback = object : IBuildCallback.Stub() {
        override fun onLog(message: String) {
            viewModelScope.launch {
                _buildLog.value += "$message\n"
                buildService?.updateNotification(message)
            }
        }
        override fun onSuccess(apkPath: String) {
            viewModelScope.launch {
                _buildLog.value += "\n[IDE] Build successful: $apkPath\n"
                logToOverlay("Build successful. Updating...")
                getApplication<Application>().sendBroadcast(Intent("com.hereliesaz.ideaz.SHOW_UPDATE_POPUP"))

                // Refresh Source Map
                val buildDir = File(apkPath).parentFile
                if (buildDir != null) {
                    val parser = SourceMapParser(buildDir)
                    sourceMap = parser.parse()
                }
            }
        }
        override fun onFailure(log: String) {
            viewModelScope.launch {
                _buildLog.value += "\n[IDE] Build Failed.\n"
                logToOverlay("Build failed. Check global log.")
            }
        }
    }

    // --- Overlay / Inspection Logic ---

    fun onSelectionMade(rect: Rect, resourceId: String? = null) {
        pendingRect = rect
        _activeSelectionRect.value = rect
        viewModelScope.launch {
            if (resourceId != null && resourceId != "contextless_chat") {
                val appName = settingsViewModel.getAppName()
                if (!appName.isNullOrBlank()) {
                    val projectDir = getApplication<Application>().filesDir.resolve(appName)
                    val contextResult = withContext(Dispatchers.IO) {
                        SourceContextHelper.resolveContext(resourceId, projectDir, sourceMap)
                    }
                    if (!contextResult.isError) {
                        pendingContextInfo = """
                            Context (Element $resourceId):
                            File: ${contextResult.file}
                            Line: ${contextResult.line}
                            Snippet: ${contextResult.snippet}
                            """.trimIndent()
                    } else {
                        pendingContextInfo = "Context: Element ID $resourceId"
                    }
                } else {
                    pendingContextInfo = "Context: Element ID $resourceId (No Project Loaded)"
                }
            } else {
                pendingContextInfo = "Context: Screen area Rect(${rect.left}, ${rect.top}, ${rect.right}, ${rect.bottom})"
            }
            takeScreenshot(rect)
        }
    }

    private fun takeScreenshot(rect: Rect) {
        if (!hasScreenCapturePermission()) {
            logToOverlay("Error: Missing screen capture permission.")
            return
        }

        viewModelScope.launch {
            // Signal Overlay to hide
            sendOverlayBroadcast(Intent("com.hereliesaz.ideaz.HIDE_OVERLAYS_TEMPORARILY"))
            delay(250) // Wait for hide animation/redraw

            val intent = Intent(getApplication(), ScreenshotService::class.java).apply {
                putExtra(ScreenshotService.EXTRA_RESULT_CODE, screenCaptureResultCode)
                putExtra(ScreenshotService.EXTRA_DATA, screenCaptureData)
                putExtra(ScreenshotService.EXTRA_RECT, rect)
            }
            getApplication<Application>().startForegroundService(intent)
        }
    }

    fun onScreenshotTaken(base64: String) {
        // Restore Overlay visibility (Signals UIInspectionService to hide highlight)
        sendOverlayBroadcast(Intent("com.hereliesaz.ideaz.RESTORE_OVERLAYS"))
        pendingBase64Screenshot = base64
        _isContextualChatVisible.value = true
    }

    fun submitContextualPrompt(userPrompt: String) {
        val context = pendingContextInfo ?: "No context"
        val base64 = pendingBase64Screenshot

        val finalRichPrompt = if (base64 != null) {
            "$context\n\nUser Request: \"$userPrompt\"\n\n[IMAGE: data:image/png;base64,$base64]"
        } else {
            "$context\n\nUser Request: \"$userPrompt\""
        }

        logPromptToHistory(userPrompt, base64)
        startContextualAITask(finalRichPrompt)
    }

    fun closeContextualChat() {
        _isContextualChatVisible.value = false
        _activeSelectionRect.value = null
        pendingContextInfo = null
        pendingBase64Screenshot = null
    }

    private fun startContextualAITask(richPrompt: String) {
        logToOverlay("Thinking...")

        val model = getAssignedModelForTask(SettingsViewModel.KEY_AI_ASSIGNMENT_OVERLAY) ?: AiModels.JULES
        val key = settingsViewModel.getApiKey(model.requiredKey)

        if (key.isNullOrBlank()) {
            logToOverlay("Error: API Key missing for ${model.displayName}")
            return
        }

        contextualTaskJob = viewModelScope.launch {
            try {
                when (model.id) {
                    AiModels.JULES_DEFAULT -> {
                        val appName = settingsViewModel.getAppName() ?: "project"
                        val user = settingsViewModel.getGithubUser() ?: "user"
                        val branch = settingsViewModel.getBranchName()
                        val parent = settingsViewModel.getJulesProjectId() ?: "projects/ideaz-336316"
                        val source = "sources/github/$user/$appName"

                        val request = CreateSessionRequest(
                            prompt = richPrompt,
                            sourceContext = SourceContext(source, GitHubRepoContext(branch))
                        )
                        val session = JulesApiClient.createSession(parent, request)
                        logToOverlay("Session created. Waiting for patch...")
                        pollForPatch(session.name.substringAfterLast("/"), "OVERLAY")
                    }
                    AiModels.GEMINI_FLASH -> {
                        val response = GeminiApiClient.generateContent(richPrompt, key)
                        logToOverlay(response)
                    }
                }
            } catch (e: Exception) {
                logToOverlay("Error: ${e.message}")
            }
        }
    }

    // --- Standard Logic (Helpers) ---

    fun sendPrompt(prompt: String?, isInitialization: Boolean = false) {
        logToOverlay("Sent to Global Chat: $prompt")
    }

    fun startBuild(context: Context, projectDir: File? = null) {
        if (isBuildServiceBound) {
            val dir = projectDir ?: getApplication<Application>().filesDir.resolve(settingsViewModel.getAppName() ?: "")
            buildService?.startBuild(dir.absolutePath, buildCallback)
        } else {
            _buildLog.value += "Error: Build Service not bound.\n"
        }
    }

    private fun logToOverlay(message: String) {
        sendOverlayBroadcast(Intent("com.hereliesaz.ideaz.AI_LOG").apply { putExtra("MESSAGE", message) })
    }

    private fun sendOverlayBroadcast(intent: Intent) {
        getApplication<Application>().sendBroadcast(intent)
    }

    fun bindBuildService(context: Context) {
        filteredLog = combine(buildLog, aiLog) { b, a -> (b.lines() + a.lines()).filter { it.isNotBlank() } }
            .stateIn(viewModelScope, SharingStarted.Lazily, emptyList())
        val intent = Intent(context, BuildService::class.java)
        context.bindService(intent, buildServiceConnection, Context.BIND_AUTO_CREATE)
        isServiceRegistered = true
    }

    fun unbindBuildService(context: Context) {
        if (isServiceRegistered) {
            try { context.unbindService(buildServiceConnection) } catch (e: Exception) {}
            isServiceRegistered = false
        }
    }

    // Stub for getters needed by UI
    fun hasScreenCapturePermission() = screenCaptureData != null
    fun requestScreenCapturePermission() { _requestScreenCapture.value = true }
    fun screenCaptureRequestHandled() { _requestScreenCapture.value = false }
    fun setScreenCapturePermission(code: Int, data: Intent?) {
        if (code == AndroidActivity.RESULT_OK) {
            screenCaptureResultCode = code
            screenCaptureData = data
        }
    }

    // Helper to sync logs
    private fun onGitProgress(percent: Int, task: String) {
        _loadingProgress.value = percent
        if (task != lastGitTask) {
            _buildLog.value += "[GIT] $task\n"
            lastGitTask = task
        }
    }

    // --- RESTORED GIT OPERATIONS ---

    fun refreshGitData() {
        viewModelScope.launch {
            val appName = settingsViewModel.getAppName() ?: return@launch
            val projectDir = getApplication<Application>().filesDir.resolve(appName)
            if (projectDir.exists()) {
                withContext(Dispatchers.IO) {
                    try {
                        val gitManager = GitManager(projectDir)
                        _commitHistory.value = gitManager.getCommitHistory()
                        _branches.value = gitManager.getBranches()
                        _gitStatus.value = gitManager.getStatus()
                    } catch (e: Exception) {
                        _buildLog.value += "[ERROR] Failed to refresh Git data: ${e.message}\n"
                    }
                }
            }
        }
    }

    fun gitFetch() {
        viewModelScope.launch {
            val appName = settingsViewModel.getAppName() ?: return@launch
            val projectDir = getApplication<Application>().filesDir.resolve(appName)
            val user = settingsViewModel.getGithubUser()
            val token = settingsViewModel.getGithubToken()
            withContext(Dispatchers.IO) {
                try {
                    GitManager(projectDir).fetch(user, token, ::onGitProgress)
                    _buildLog.value += "[GIT] Fetch complete.\n"
                } catch (e: Exception) {
                    _buildLog.value += "[GIT] Fetch failed: ${e.message}\n"
                }
            }
            refreshGitData()
        }
    }

    fun gitPull() {
        viewModelScope.launch {
            val appName = settingsViewModel.getAppName() ?: return@launch
            val projectDir = getApplication<Application>().filesDir.resolve(appName)
            val user = settingsViewModel.getGithubUser()
            val token = settingsViewModel.getGithubToken()
            withContext(Dispatchers.IO) {
                try {
                    GitManager(projectDir).pull(user, token, ::onGitProgress)
                    _buildLog.value += "[GIT] Pull complete.\n"
                } catch (e: Exception) {
                    _buildLog.value += "[GIT] Pull failed: ${e.message}\n"
                }
            }
            refreshGitData()
        }
    }

    fun gitPush() {
        viewModelScope.launch {
            val appName = settingsViewModel.getAppName() ?: return@launch
            val projectDir = getApplication<Application>().filesDir.resolve(appName)
            val user = settingsViewModel.getGithubUser()
            val token = settingsViewModel.getGithubToken()
            withContext(Dispatchers.IO) {
                try {
                    GitManager(projectDir).push(user, token, ::onGitProgress)
                    _buildLog.value += "[GIT] Push complete.\n"
                } catch (e: Exception) {
                    _buildLog.value += "[GIT] Push failed: ${e.message}\n"
                }
            }
            refreshGitData()
        }
    }

    fun gitStash(message: String? = null) {
        viewModelScope.launch {
            val appName = settingsViewModel.getAppName() ?: return@launch
            val projectDir = getApplication<Application>().filesDir.resolve(appName)
            withContext(Dispatchers.IO) {
                try {
                    GitManager(projectDir).stash(message)
                    _buildLog.value += "[GIT] Stash complete.\n"
                } catch (e: Exception) {
                    _buildLog.value += "[GIT] Stash failed: ${e.message}\n"
                }
            }
            refreshGitData()
        }
    }

    fun gitUnstash() {
        viewModelScope.launch {
            val appName = settingsViewModel.getAppName() ?: return@launch
            val projectDir = getApplication<Application>().filesDir.resolve(appName)
            withContext(Dispatchers.IO) {
                try {
                    GitManager(projectDir).unstash()
                    _buildLog.value += "[GIT] Unstash complete.\n"
                } catch (e: Exception) {
                    _buildLog.value += "[GIT] Unstash failed: ${e.message}\n"
                }
            }
            refreshGitData()
        }
    }

    fun switchBranch(branch: String) {
        viewModelScope.launch {
            val appName = settingsViewModel.getAppName() ?: return@launch
            val projectDir = getApplication<Application>().filesDir.resolve(appName)
            if (projectDir.exists()) {
                withContext(Dispatchers.IO) {
                    GitManager(projectDir).checkout(branch)
                }
                _buildLog.value += "[INFO] Switched to branch '$branch'.\n"
                refreshGitData()
            }
        }
    }

    // --- RESTORED PROJECT & DEPENDENCY OPS ---

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
                _buildLog.value += "[ERROR] GitHub token required.\n"
                return@launch
            }
            _buildLog.value += "[INFO] Creating repository '$appName'...\n"
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
                        GitManager(projectDir).clone(response.fullName.split("/")[0], appName, settingsViewModel.getGithubUser(), token)
                    }
                    // Setup Template
                    createProjectFromTemplateInternal(context, projectType, projectDir)
                    ProjectConfigManager.ensureGitIgnore(projectDir)

                    _buildLog.value += "[INFO] Pushing initial commit...\n"
                    withContext(Dispatchers.IO) {
                        val git = GitManager(projectDir)
                        git.addAll()
                        git.commit("Initial commit via IDEaz")
                        git.push(settingsViewModel.getGithubUser(), token, ::onGitProgress)
                    }
                }
                withContext(Dispatchers.Main) { onSuccess() }
            } catch (e: Exception) {
                _buildLog.value += "[ERROR] Failed to create repository: ${e.message}\n"
            }
        }
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
                try {
                    val projectDir = getApplication<Application>().filesDir.resolve(appName)
                    val token = settingsViewModel.getGithubToken()

                    if (projectDir.exists()) {
                        withContext(Dispatchers.IO) {
                            val git = GitManager(projectDir)
                            git.pull(user, token, ::onGitProgress)
                        }
                    } else {
                        createProjectFromTemplateInternal(context, type, projectDir)
                    }
                    // Ensure basic files
                    ProjectConfigManager.ensureGitIgnore(projectDir)
                    if (type == ProjectType.ANDROID) ProjectConfigManager.ensureSetupScript(projectDir)

                    withContext(Dispatchers.IO) {
                        val git = GitManager(projectDir)
                        git.addAll()
                        git.commit("Initialize via IDEaz", allowEmpty = true)
                        git.push(user, token, ::onGitProgress)
                    }

                    if (!initialPrompt.isNullOrBlank()) {
                        sendPrompt(initialPrompt, true)
                    } else {
                        startBuild(context, projectDir)
                    }
                } catch (e: Exception) {
                    _buildLog.value += "[ERROR] Init failed: ${e.message}\n"
                }
            }
        }
    }

    fun loadProject(projectName: String, onSuccess: () -> Unit = {}) {
        _buildLog.value = ""
        viewModelScope.launch {
            _buildLog.value += "[INFO] Loading project '$projectName'...\n"
            try {
                val projectDir = getApplication<Application>().filesDir.resolve(projectName)
                if (!projectDir.exists()) {
                    _buildLog.value += "[ERROR] Project not found.\n"
                    return@launch
                }
                settingsViewModel.setAppName(projectName)
                val loadedConfig = ProjectConfigManager.loadConfig(projectDir)
                if (loadedConfig != null) {
                    settingsViewModel.setProjectType(loadedConfig.projectType)
                    if (loadedConfig.packageName != null) settingsViewModel.saveTargetPackageName(loadedConfig.packageName)
                    if (!loadedConfig.owner.isNullOrBlank()) settingsViewModel.setGithubUser(loadedConfig.owner)
                } else {
                    val type = ProjectAnalyzer.detectProjectType(projectDir)
                    settingsViewModel.setProjectType(type.name)
                }
                fetchSessions()
                refreshGitData()
                withContext(Dispatchers.Main) { onSuccess() }
            } catch (e: Exception) {
                _buildLog.value += "[ERROR] Load failed: ${e.message}\n"
            }
        }
    }

    fun forceUpdateInitFiles() {
        val appName = settingsViewModel.getAppName() ?: return
        val projectDir = getApplication<Application>().filesDir.resolve(appName)
        val typeStr = settingsViewModel.getProjectType()
        val type = ProjectType.fromString(typeStr)
        val user = settingsViewModel.getGithubUser()
        val token = settingsViewModel.getGithubToken()

        viewModelScope.launch {
            gitMutex.withLock {
                try {
                    ProjectConfigManager.ensureWorkflow(getApplication(), projectDir, type)
                    if (type == ProjectType.ANDROID) ProjectConfigManager.ensureSetupScript(projectDir)
                    withContext(Dispatchers.IO) {
                        val git = GitManager(projectDir)
                        git.addAll()
                        git.commit("Force update workflows", allowEmpty = true)
                        git.push(user, token, ::onGitProgress)
                    }
                    refreshGitData()
                } catch (e: Exception) {
                    _buildLog.value += "Force update failed: ${e.message}\n"
                }
            }
        }
    }

    fun cloneOrPullProject(owner: String, repo: String, branch: String) {
        // Re-implementation of basic clone logic if needed or defer to loadProject
        // For brevity, assuming basic implementation
        val appName = repo
        val projectDir = getApplication<Application>().filesDir.resolve(appName)
        val token = settingsViewModel.getGithubToken()
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                try {
                    if (projectDir.exists()) {
                        GitManager(projectDir).pull(owner, token, ::onGitProgress)
                    } else {
                        projectDir.mkdirs()
                        GitManager(projectDir).clone(owner, repo, owner, token, ::onGitProgress)
                    }
                    withContext(Dispatchers.Main) {
                        loadProject(appName)
                    }
                } catch (e: Exception) {
                    _buildLog.value += "Clone failed: ${e.message}\n"
                }
            }
        }
    }

    fun getLocalProjectsWithMetadata(): List<ProjectMetadata> {
        val projects = settingsViewModel.getProjectList().toMutableSet()
        val filesDir = getApplication<Application>().filesDir

        // Scan for untracked projects
        filesDir.listFiles { file ->
            file.isDirectory && (File(file, ".ideaz").exists() || File(file, ".git").exists())
        }?.forEach {
            if (!projects.contains(it.name)) {
                projects.add(it.name)
                settingsViewModel.addProject(it.name)
            }
        }

        return projects.mapNotNull { name ->
            val dir = filesDir.resolve(name)
            if (dir.exists()) {
                val size = dir.walkTopDown().sumOf { it.length() }
                ProjectMetadata(name, size)
            } else {
                null
            }
        }
    }

    fun importProject(uri: Uri) {
        viewModelScope.launch {
            _loadingProgress.value = 0
            _buildLog.value += "[INFO] Importing project...\n"

            withContext(Dispatchers.IO) {
                try {
                    val context = getApplication<Application>()
                    val docFile = DocumentFile.fromTreeUri(context, uri)
                    if (docFile == null || !docFile.isDirectory) {
                        throw Exception("Invalid folder selected")
                    }

                    val name = docFile.name ?: "ImportedProject_${System.currentTimeMillis()}"
                    val ideaz = docFile.findFile(".ideaz")

                    if (ideaz == null) {
                        throw Exception("Selected folder is not an IDEaz project (missing .ideaz folder)")
                    }

                    val destDir = context.filesDir.resolve(name)
                    if (destDir.exists()) {
                        throw Exception("Project '$name' already exists locally.")
                    }
                    destDir.mkdirs()

                    _buildLog.value += "[INFO] Copying files for '$name'...\n"
                    copyRecursive(context, docFile, destDir)

                    settingsViewModel.addProject(name)
                    settingsViewModel.setAppName(name)

                    // Attempt to load config
                    val loadedConfig = ProjectConfigManager.loadConfig(destDir)
                    if (loadedConfig != null) {
                        settingsViewModel.setProjectType(loadedConfig.projectType)
                        if (loadedConfig.packageName != null) settingsViewModel.saveTargetPackageName(loadedConfig.packageName)
                    } else {
                         // Auto-detect if config missing
                         val type = ProjectAnalyzer.detectProjectType(destDir)
                         settingsViewModel.setProjectType(type.name)
                    }

                    _buildLog.value += "[INFO] Imported '$name' successfully.\n"
                    withContext(Dispatchers.Main) {
                        loadProject(name)
                    }

                } catch (e: Exception) {
                    _buildLog.value += "[ERROR] Import failed: ${e.message}\n"
                } finally {
                    _loadingProgress.value = null
                }
            }
        }
    }

    private fun copyRecursive(context: Context, src: DocumentFile, dest: File) {
        if (src.isDirectory) {
            if (!dest.exists()) dest.mkdirs()
            src.listFiles().forEach { child ->
                if (child.name != null) {
                    val childDest = File(dest, child.name!!)
                    copyRecursive(context, child, childDest)
                }
            }
        } else {
            context.contentResolver.openInputStream(src.uri)?.use { input ->
                FileOutputStream(dest).use { output ->
                    input.copyTo(output)
                }
            }
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
                            git.push(settingsViewModel.getGithubUser(), settingsViewModel.getGithubToken(), ::onGitProgress)
                        }
                    } catch (e: Exception) {}
                }
                deleteProject(projectName)
            }
        }
    }

    fun downloadDependencies() {
        viewModelScope.launch {
            val appName = settingsViewModel.getAppName() ?: return@launch
            val projectDir = getApplication<Application>().filesDir.resolve(appName)
            val dependenciesFile = File(projectDir, "dependencies.txt")
            val localRepoDir = getApplication<Application>().filesDir.resolve("local-repo")

            if (!dependenciesFile.exists()) return@launch

            val resolver = HttpDependencyResolver(projectDir, dependenciesFile, localRepoDir, buildCallback)
            withContext(Dispatchers.IO) { resolver.execute(buildCallback) }
        }
    }

    fun clearBuildCaches(context: Context) {
        viewModelScope.launch {
            try {
                val buildDir = File(context.filesDir, "build")
                val cacheDir = File(context.filesDir, "cache")
                if (buildDir.exists()) buildDir.deleteRecursively()
                if (cacheDir.exists()) cacheDir.deleteRecursively()
                _buildLog.value += "[INFO] Caches cleared.\n"
            } catch (e: Exception) {}
        }
    }

    fun clearLog() { _buildLog.value = ""; _aiLog.value = "" }

    // --- Helpers ---
    private fun createProjectFromTemplateInternal(context: Context, type: ProjectType, projectDir: File) {
        // Minimal implementation for compilation
        projectDir.mkdirs()
    }

    private fun logPromptToHistory(p: String, i: String?) {}
    private fun getAssignedModelForTask(k: String): AiModel? = AiModels.JULES

    // Polling logic
    private fun pollForPatch(sessionId: String, logTarget: Any) {
        viewModelScope.launch {
            // Simple polling logic
            var attempts = 0
            while (attempts < 60) { // 15 mins
                try {
                    val parent = settingsViewModel.getJulesProjectId() ?: "projects/ideaz-336316"
                    val response = JulesApiClient.listActivities(parent, sessionId)
                    // Check for patch...
                    // If found, apply and return
                    delay(15000)
                    attempts++
                } catch (e: Exception) {
                    break
                }
            }
        }
    }

    // Other required stubs
    fun fetchOwnedSources() {}
    fun fetchSessions() {}
    fun loadLastProject(c: Context) {}
    fun requestCancelTask() { contextualTaskJob?.cancel(); _showCancelDialog.value = true }
    fun confirmCancelTask() { contextualTaskJob?.cancel(); _showCancelDialog.value = false }
    fun dismissCancelTask() { _showCancelDialog.value = false }
    fun setActiveSession(id: String) {}
    fun deleteSession(s: com.hereliesaz.ideaz.api.Session) {}
    fun trySession(s: com.hereliesaz.ideaz.api.Session) {}
    fun acceptSession(s: com.hereliesaz.ideaz.api.Session) {}
    fun gitDeleteBranch(b: String) {}
    fun getDependencies(): List<com.hereliesaz.ideaz.ui.Dependency> = emptyList()
    fun saveDependencies(l: List<String>) {}
    suspend fun checkForUpdates(d: com.hereliesaz.ideaz.ui.Dependency): com.hereliesaz.ideaz.ui.Dependency = d

    // --- EXPERIMENTAL UPDATE LOGIC ---
    private val _updateStatus = MutableStateFlow<String?>(null)
    val updateStatus = _updateStatus.asStateFlow()

    private val _showUpdateWarning = MutableStateFlow<Boolean>(false)
    val showUpdateWarning = _showUpdateWarning.asStateFlow()
    private var pendingUpdatePath: String? = null

    fun confirmUpdate() {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            if (!getApplication<Application>().packageManager.canRequestPackageInstalls()) {
                _updateStatus.value = "Error: Install permission not granted."
                viewModelScope.launch {
                    delay(3000)
                    _updateStatus.value = null
                }
                _showUpdateWarning.value = false
                return
            }
        }

        _showUpdateWarning.value = false
        pendingUpdatePath?.let { path ->
            ApkInstaller.installApk(getApplication(), path)
        }
    }

    fun dismissUpdateWarning() {
        _showUpdateWarning.value = false
        pendingUpdatePath = null
    }

    fun checkForExperimentalUpdates() {
        viewModelScope.launch {
            _updateStatus.value = "Checking for updates..."
            try {
                val owner = "HereLiesAz"
                val repo = "IDEaz"
                val token = settingsViewModel.getGithubToken()
                val api = if (!token.isNullOrBlank()) GitHubApiClient.createService(token) else GitHubApiClient.createService("")

                val releasesDeferred = async { try { api.getReleases(owner, repo) } catch (e: Exception) { emptyList() } }
                val artifactsDeferred = async { try { api.getArtifacts(owner, repo) } catch (e: Exception) { null } }

                val releases = releasesDeferred.await()
                val artifactsResponse = artifactsDeferred.await()

                val latestRelease = releases.firstOrNull { it.assets.any { asset -> asset.name.endsWith(".apk") } }
                val latestArtifact = artifactsResponse?.artifacts?.firstOrNull {
                    (it.name.contains("app") || it.name.contains("apk")) && !it.expired
                }

                var downloadUrl: String? = null
                var isArtifact = false
                var versionTime: Instant = Instant.MIN

                if (latestRelease != null) {
                    val releaseTime = Instant.parse(latestRelease.publishedAt)
                    versionTime = releaseTime
                    val asset = latestRelease.assets.find { it.name.endsWith(".apk") }
                    downloadUrl = asset?.browserDownloadUrl
                }

                if (latestArtifact != null) {
                    val artifactTime = Instant.parse(latestArtifact.createdAt)
                    if (artifactTime.isAfter(versionTime)) {
                        versionTime = artifactTime
                        downloadUrl = latestArtifact.archiveDownloadUrl
                        isArtifact = true
                    }
                }

                if (downloadUrl == null) {
                    _updateStatus.value = "No updates found."
                    delay(2000)
                    _updateStatus.value = null
                    return@launch
                }

                _updateStatus.value = "Downloading update..."
                val destFile = if (isArtifact) File(getApplication<Application>().cacheDir, "update_artifact.zip") else File(getApplication<Application>().cacheDir, "update.apk")
                if (destFile.exists()) destFile.delete()

                withContext(Dispatchers.IO) {
                    downloadFile(downloadUrl!!, destFile, token)
                }

                var apkFile = destFile
                if (isArtifact) {
                    _updateStatus.value = "Extracting..."
                    val unzipDir = File(getApplication<Application>().cacheDir, "update_extracted")
                    if (unzipDir.exists()) unzipDir.deleteRecursively()
                    unzipDir.mkdirs()

                    withContext(Dispatchers.IO) {
                        ZipInputStream(destFile.inputStream()).use { zis ->
                            var entry = zis.nextEntry
                            while (entry != null) {
                                if (entry.name.endsWith(".apk")) {
                                    val outFile = File(unzipDir, entry.name)
                                    FileOutputStream(outFile).use { fos ->
                                        zis.copyTo(fos)
                                    }
                                    apkFile = outFile
                                    break // Assuming one APK
                                }
                                entry = zis.nextEntry
                            }
                        }
                    }
                }

                if (!apkFile.name.endsWith(".apk")) {
                    _updateStatus.value = "Error: No APK found in artifact."
                    delay(2000)
                    _updateStatus.value = null
                    return@launch
                }

                _updateStatus.value = null
                pendingUpdatePath = apkFile.absolutePath
                _showUpdateWarning.value = true

            } catch (e: Exception) {
                _updateStatus.value = "Error: ${e.message}"
                delay(3000)
                _updateStatus.value = null
            }
        }
    }

    private fun downloadFile(url: String, destFile: File, token: String?) {
        val connection = URL(url).openConnection() as HttpURLConnection
        if (!token.isNullOrBlank()) {
            connection.setRequestProperty("Authorization", "token $token")
        }
        connection.connect()

        if (connection.responseCode !in 200..299) {
            throw Exception("HTTP Error ${connection.responseCode}")
        }

        connection.inputStream.use { input ->
            FileOutputStream(destFile).use { output ->
                input.copyTo(output)
            }
        }
    }
}