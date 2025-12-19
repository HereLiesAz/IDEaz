package com.hereliesaz.ideaz.ui.delegates

import android.app.Application
import android.content.Context
import android.util.Log
import com.hereliesaz.ideaz.api.CreateRepoRequest
import com.hereliesaz.ideaz.api.GitHubApiClient
import com.hereliesaz.ideaz.api.GitHubRepoResponse
import com.hereliesaz.ideaz.api.GitHubPermissions
import com.hereliesaz.ideaz.api.Source
import com.hereliesaz.ideaz.jules.JulesApiClient
import com.hereliesaz.ideaz.git.GitManager
import com.hereliesaz.ideaz.models.ProjectType
import com.hereliesaz.ideaz.ui.ProjectMetadata
import com.hereliesaz.ideaz.ui.SettingsViewModel
import com.hereliesaz.ideaz.utils.ProjectConfigManager
import com.hereliesaz.ideaz.utils.ProjectInitializer
import com.hereliesaz.ideaz.utils.RepoMapper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Delegate responsible for repository management:
 * - Fetching/Creating GitHub repositories.
 * - Initializing local projects.
 * - Scanning/Managing local project directories.
 * - Uploading secrets (currently stubbed).
 *
 * @param application The Application context.
 * @param settingsViewModel ViewModel for accessing settings.
 * @param scope CoroutineScope for background tasks.
 * @param onLog Callback for general logs.
 * @param onOverlayLog Callback for overlay logs.
 * @param onLoadingProgress Callback to show loading indicator.
 * @param onGitProgress Callback to show Git operation progress.
 */
class RepoDelegate(
    private val application: Application,
    private val settingsViewModel: SettingsViewModel,
    private val scope: CoroutineScope,
    private val onLog: (String) -> Unit,
    private val onOverlayLog: (String) -> Unit,
    private val onLoadingProgress: (Int?) -> Unit,
    private val onGitProgress: (Int, String) -> Unit
) {

    private val _ownedRepos = MutableStateFlow<List<GitHubRepoResponse>>(emptyList())
    /** List of repositories owned by the authenticated GitHub user. */
    val ownedRepos = _ownedRepos.asStateFlow()

    /**
     * Fetches the list of repositories from GitHub or Jules API.
     * Prefers Jules API if configured.
     */
    fun fetchGitHubRepos() {
        scope.launch {
            onLoadingProgress(0)
            try {
                val julesProjectId = settingsViewModel.getJulesProjectId()

                if (!julesProjectId.isNullOrBlank()) {
                     // Use Jules API
                     try {
                         val response = JulesApiClient.listSources(julesProjectId)
                         val mappedRepos = response.sources?.mapNotNull { RepoMapper.mapSourceToRepoResponse(it) } ?: emptyList()
                         _ownedRepos.value = mappedRepos
                         return@launch
                     } catch (e: Exception) {
                         onOverlayLog("Jules API Error: ${e.message}. Falling back to GitHub.")
                     }
                }

                // Fallback to GitHub API
                val token = settingsViewModel.getGithubToken()
                if (token.isNullOrBlank()) {
                    onOverlayLog("Error: No GitHub Token found.")
                }

                // Try Jules API First
                val projectId = settingsViewModel.getJulesProjectId()
                var julesSuccess = false
                if (!projectId.isNullOrBlank()) {
                    try {
                        val response = JulesApiClient.listSources(projectId)
                        val julesRepos = response.sources?.mapNotNull { source ->
                            source.githubRepo?.let {
                                GitHubRepoResponse(
                                    id = 0,
                                    name = it.repo,
                                    fullName = "${it.owner}/${it.repo}",
                                    htmlUrl = "https://github.com/${it.owner}/${it.repo}",
                                    cloneUrl = "https://github.com/${it.owner}/${it.repo}.git",
                                    defaultBranch = it.defaultBranch?.displayName ?: "main",
                                    permissions = null // Jules doesn't return permissions yet
                                )
                            }
                        } ?: emptyList()

                        if (julesRepos.isNotEmpty()) {
                            _ownedRepos.value = julesRepos
                            julesSuccess = true
                        }
                    } catch (e: Exception) {
                        onOverlayLog("Jules API failed to list sources: ${e.message}. Falling back to GitHub.")
                    }
                }

                // Fallback to GitHub API
                if (!julesSuccess && !token.isNullOrBlank()) {
                    val service = GitHubApiClient.createService(token)
                    val repos = service.listRepos()
                    _ownedRepos.value = repos
                }

            } catch (e: Exception) {
                onOverlayLog("Error fetching repos: ${e.message}")
            } finally {
                onLoadingProgress(null)
            }
        }
    }

    /**
     * Creates a new repository on GitHub and initializes the local project state.
     */
    fun createGitHubRepository(
        appName: String,
        description: String,
        isPrivate: Boolean,
        projectType: ProjectType,
        packageName: String,
        context: Context,
        onSuccess: (owner: String, branch: String) -> Unit
    ) {
        scope.launch {
            onLoadingProgress(0)
            try {
                val token = settingsViewModel.getGithubToken()
                if (token.isNullOrBlank()) {
                    onOverlayLog("Error: No GitHub Token found.")
                    return@launch
                }

                val service = GitHubApiClient.createService(token)
                val request = CreateRepoRequest(
                    name = appName,
                    description = description,
                    private = isPrivate,
                    autoInit = true
                )

                val repo = service.createRepo(request)
                val derivedOwner = repo.fullName.split("/")[0]

                settingsViewModel.setAppName(appName)
                settingsViewModel.setGithubUser(derivedOwner)
                settingsViewModel.saveProjectConfig(appName, derivedOwner, repo.defaultBranch ?: "main")
                settingsViewModel.saveTargetPackageName(packageName)
                settingsViewModel.setProjectType(projectType.name)

                onOverlayLog("Repository created: ${repo.htmlUrl}")
                onSuccess(derivedOwner, repo.defaultBranch ?: "main")

            } catch (e: Exception) {
                onOverlayLog("Failed to create repository: ${e.message}")
            } finally {
                onLoadingProgress(null)
            }
        }
    }

    /**
     * Forks a GitHub repository.
     */
    fun forkRepository(
        owner: String,
        repoName: String,
        onSuccess: (newOwner: String, newRepoName: String, branch: String) -> Unit
    ) {
        scope.launch {
            onLoadingProgress(0)
            try {
                val token = settingsViewModel.getGithubToken()
                if (token.isNullOrBlank()) {
                    onOverlayLog("Error: No GitHub Token found.")
                    return@launch
                }

                val service = GitHubApiClient.createService(token)
                onOverlayLog("Forking $owner/$repoName...")

                val request = com.hereliesaz.ideaz.api.ForkRepoRequest()
                val response = service.forkRepo(owner, repoName, request)

                val newOwner = response.fullName.split("/")[0]
                val newBranch = response.defaultBranch ?: "main"

                onOverlayLog("Fork successful: ${response.htmlUrl}")

                settingsViewModel.setAppName(response.name)
                settingsViewModel.setGithubUser(newOwner)
                settingsViewModel.saveProjectConfig(response.name, newOwner, newBranch)

                // Sanitize and set package name
                var sanitizedUser = newOwner.replace(Regex("[^a-zA-Z0-9]"), "").lowercase()
                if (sanitizedUser.isEmpty() || sanitizedUser[0].isDigit()) sanitizedUser = "_$sanitizedUser"

                var sanitizedApp = response.name.replace(Regex("[^a-zA-Z0-9]"), "").lowercase()
                if (sanitizedApp.isEmpty() || sanitizedApp[0].isDigit()) sanitizedApp = "_$sanitizedApp"

                val generatedPackage = "com.$sanitizedUser.$sanitizedApp"
                settingsViewModel.saveTargetPackageName(generatedPackage)

                onSuccess(newOwner, response.name, newBranch)

            } catch (e: Exception) {
                onOverlayLog("Fork failed: ${e.message}")
            } finally {
                onLoadingProgress(null)
            }
        }
    }

    /**
     * Selects an existing repository for setup, inferring settings from metadata.
     */
    fun selectRepositoryForSetup(repo: GitHubRepoResponse, onSuccess: (owner: String, branch: String) -> Unit) {
        scope.launch {
            onLoadingProgress(0)
            try {
                val owner = repo.fullName.split("/")[0]
                val appName = repo.name
                val defaultBranch = repo.defaultBranch ?: "main"

                settingsViewModel.setAppName(appName)
                settingsViewModel.setGithubUser(owner)
                settingsViewModel.saveProjectConfig(appName, owner, defaultBranch)

                val sanitizedUser = owner.replace(Regex("[^a-zA-Z0-9]"), "").lowercase()
                val sanitizedApp = appName.replace(Regex("[^a-zA-Z0-9]"), "").lowercase()
                val generatedPackage = "com.$sanitizedUser.$sanitizedApp"
                settingsViewModel.saveTargetPackageName(generatedPackage)

                onSuccess(owner, defaultBranch)
            } catch (e: Exception) {
                onOverlayLog("Error loading repository: ${e.message}")
            } finally {
                onLoadingProgress(null)
            }
        }
    }

    /**
     * Forces the regeneration and push of initialization files (CI workflows, setup script).
     */
    fun forceUpdateInitFiles() {
        scope.launch(Dispatchers.IO) {
            val appName = settingsViewModel.getAppName() ?: return@launch
            val projectDir = settingsViewModel.getProjectPath(appName)
            val type = ProjectType.fromString(settingsViewModel.getProjectType())
            val packageName = settingsViewModel.getTargetPackageName() ?: "com.example.app"

            ProjectConfigManager.ensureWorkflow(application, projectDir, type)
            ProjectConfigManager.ensureSetupScript(projectDir)
            ProjectConfigManager.ensureAgentsSetupMd(projectDir)

            // Inject Crash Reporting (Error Handling)
            if (type == ProjectType.ANDROID) {
                ProjectInitializer.injectCrashReporting(application, projectDir, packageName, settingsViewModel)
            }

            try {
                val git = GitManager(projectDir)
                val token = settingsViewModel.getGithubToken()
                val user = settingsViewModel.getGithubUser()

                if (!git.isRepo()) {
                    onOverlayLog("Initializing local repository...")
                    git.init()
                    if (user != null && !appName.isBlank()) {
                        val remoteUrl = "https://github.com/$user/$appName.git"
                        git.addRemote("origin", remoteUrl)
                    }
                }

                if (git.hasChanges()) {
                    git.addAll()
                    git.commit("IDEaz: Update Init Files & Workflows")
                    if (token != null && user != null) {
                        git.push(user, token) { progress, task -> onGitProgress(progress, task) }
                        onOverlayLog("Init files pushed successfully.")
                    }
                }
            } catch (e: Exception) {
                onOverlayLog("Error pushing init files: ${e.message}")
            }
        }
    }

    /**
     * Stub for uploading project secrets.
     * Automated upload (using Sodium/JNA) is currently disabled due to stability issues.
     */
    fun uploadProjectSecrets(owner: String, repo: String) {
        // MANUAL COMPLIANCE: JNA/Sodium automation is disabled for stability.
        scope.launch {
            onOverlayLog("CRITICAL: Manual secret upload is required.")
            onOverlayLog("1. Go to your repo settings on GitHub.")
            onOverlayLog("2. Add secrets: GEMINI_API_KEY, GOOGLE_API_KEY, JULES_PROJECT_ID.")
            onOverlayLog("The remote build will fail without them.")
        }
    }

    /**
     * Scans the app's internal files directory for project folders and updates settings.
     */
    fun scanLocalProjects() {
        scope.launch(Dispatchers.IO) {
            val root = application.filesDir
            val dirs = root.listFiles { file ->
                file.isDirectory && !file.name.startsWith(".") && file.name != "tools" && file.name != "cache" && file.name != "local-repo"
            }?.map { it.name } ?: emptyList()
            withContext(Dispatchers.Main) {
                dirs.forEach { settingsViewModel.addProject(it) }
            }
        }
    }

    /**
     * Returns a list of local projects with metadata (e.g., size).
     */
    fun getLocalProjectsWithMetadata(): List<ProjectMetadata> {
        val root = application.filesDir
        val projects = settingsViewModel.getProjectList()
        return projects.mapNotNull { name ->
            val dir = File(root, name)
            if (dir.exists()) ProjectMetadata(name, dir.walkTopDown().sumOf { it.length() }) else null
        }
    }
}
