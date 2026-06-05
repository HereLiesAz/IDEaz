package com.hereliesaz.ideaz.ui.delegates

import android.app.Application
import android.content.Context
import android.util.Log
import com.hereliesaz.ideaz.api.CreateRepoRequest
import com.hereliesaz.ideaz.api.CreateSecretRequest
import com.hereliesaz.ideaz.api.GitHubApiClient
import com.hereliesaz.ideaz.api.GitHubRepoResponse
import com.hereliesaz.ideaz.api.GitHubPermissions
import com.hereliesaz.ideaz.api.Source
import com.hereliesaz.ideaz.jules.JulesApiClient
import com.hereliesaz.ideaz.git.GitManager
import com.hereliesaz.ideaz.models.ProjectType
import com.hereliesaz.ideaz.ui.AiModels
import com.hereliesaz.ideaz.ui.ProjectMetadata
import com.hereliesaz.ideaz.ui.SettingsViewModel
import com.hereliesaz.ideaz.utils.GithubSecretBox
import com.hereliesaz.ideaz.utils.ProjectConfigManager
import com.hereliesaz.ideaz.utils.ProjectInitializer
import com.hereliesaz.ideaz.utils.RepoMapper
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File

/**
 * Delegate responsible for repository management and initialization.
 *
 * **Key Responsibilities:**
 * - **Discovery:** listing repositories from Jules/GitHub.
 * - **Creation:** Creating new repos, forking existing ones.
 * - **Setup:** Initializing local project state, generating package names, and ensuring config files exist.
 * - **Security:** Encrypting and uploading secrets to GitHub Actions.
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

    // --- StateFlows ---

    private val _ownedRepos = MutableStateFlow<List<GitHubRepoResponse>>(emptyList())
    /** List of repositories owned by the authenticated GitHub user. */
    val ownedRepos = _ownedRepos.asStateFlow()

    // --- Public Operations ---

    /**
     * Fetches the list of repositories available to the user.
     *
     * **Strategy:**
     * 1. **Try Jules API:** If a Jules Project ID is configured, attempts to list sources from the Jules service.
     *    This is preferred as it provides agent-optimized metadata.
     * 2. **Fallback to GitHub API:** If Jules fails or is not configured, fetches repositories directly from GitHub.
     */
    fun fetchGitHubRepos() {
        scope.launch {
            onLoadingProgress(0)
            try {
                val julesProjectId = settingsViewModel.getJulesProjectId()

                // 1. Jules API Path
                if (!julesProjectId.isNullOrBlank()) {
                     try {
                         val response = JulesApiClient.listSources()
                         val mappedRepos = response.sources?.mapNotNull { RepoMapper.mapSourceToRepoResponse(it) } ?: emptyList()
                         _ownedRepos.value = mappedRepos
                         return@launch
                     } catch (e: Exception) {
                         onOverlayLog("Jules API Error: ${e.message}. Falling back to GitHub.")
                     }
                }

                val token = settingsViewModel.getGithubToken()
                if (token.isNullOrBlank()) {
                    onOverlayLog("Error: No GitHub Token found.")
                    return@launch
                }

                // 2. Fallback Path (Direct GitHub)
                val service = GitHubApiClient.createService(token)
                val repos = service.listRepos()
                _ownedRepos.value = repos

            } catch (e: Exception) {
                onOverlayLog("Error fetching repos: ${e.message}")
            } finally {
                onLoadingProgress(null)
            }
        }
    }

    /**
     * Creates a new repository on GitHub and initializes the local project state.
     *
     * @param appName The name of the new repository.
     * @param description A short description.
     * @param isPrivate Whether the repo should be private.
     * @param projectType The type of project (Android, Web, etc.).
     * @param packageName The target package name (e.g., com.example.app).
     * @param context Context for file operations.
     * @param onSuccess Callback invoked with the new owner and branch name.
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
                val templateRepo = com.hereliesaz.ideaz.utils.TemplateRegistry.repoFor(projectType)
                val configuredUser = settingsViewModel.getGithubUser()?.takeIf { it.isNotBlank() }

                // Prefer generating the new repo from its official template (the
                // remote starts pre-populated with the starter). Fall back to an
                // empty auto-init repo if no template is registered or generation
                // fails (e.g. the template isn't marked as a template repo).
                var generated = false
                val repo = if (templateRepo != null) {
                    try {
                        onOverlayLog("Creating $appName from ${com.hereliesaz.ideaz.utils.TemplateRegistry.OWNER}/$templateRepo...")
                        val generatedRepo = service.generateFromTemplate(
                            com.hereliesaz.ideaz.utils.TemplateRegistry.OWNER,
                            templateRepo,
                            com.hereliesaz.ideaz.api.GenerateFromTemplateRequest(
                                owner = configuredUser,
                                name = appName,
                                description = description,
                                private = isPrivate
                            )
                        )
                        generated = true
                        generatedRepo
                    } catch (e: Exception) {
                        onLog("Template generate failed (${e.message}); creating an empty repository instead.")
                        service.createRepo(
                            CreateRepoRequest(name = appName, description = description, private = isPrivate, autoInit = true)
                        )
                    }
                } else {
                    service.createRepo(
                        CreateRepoRequest(name = appName, description = description, private = isPrivate, autoInit = true)
                    )
                }

                val derivedOwner = repo.fullName.split("/")[0]
                val branch = repo.defaultBranch ?: "main"

                // Update Local Config
                settingsViewModel.setAppName(appName)
                settingsViewModel.setGithubUser(derivedOwner)
                settingsViewModel.saveProjectConfig(appName, derivedOwner, branch)
                settingsViewModel.saveTargetPackageName(packageName)
                settingsViewModel.setProjectType(projectType.name)

                // When generated from a template, mirror the populated remote
                // locally by cloning. Otherwise the caller scaffolds the empty
                // project from the bundled template.
                if (generated) {
                    val projectDir = settingsViewModel.getProjectPath(appName)
                    cloneWithRetry(projectDir, derivedOwner, appName, token)
                    // The Android template ships the placeholder package; rename
                    // it to the user's chosen package in the cloned copy.
                    if (projectType == ProjectType.ANDROID) {
                        withContext(Dispatchers.IO) {
                            com.hereliesaz.ideaz.utils.TemplateManager.applyAndroidPlaceholders(
                                projectDir, packageName, appName
                            )
                        }
                    }
                }

                onOverlayLog("Repository created: ${repo.htmlUrl}")
                onSuccess(derivedOwner, branch)

            } catch (e: Exception) {
                onOverlayLog("Failed to create repository: ${e.message}")
            } finally {
                onLoadingProgress(null)
            }
        }
    }

    /**
     * Clones [owner]/[repo] into [projectDir], retrying with exponential backoff.
     * A freshly generated repository can take a moment to become clonable, so we
     * retry before giving up. No-op if the directory is already a git repo.
     */
    private suspend fun cloneWithRetry(projectDir: File, owner: String, repo: String, token: String?) {
        withContext(Dispatchers.IO) {
            val git = GitManager(projectDir)
            if (git.isRepo()) return@withContext
            var delayMs = 2000L
            var lastError: Exception? = null
            var attempt = 0
            var success = false
            while (attempt < 4 && !success) {
                try {
                    onOverlayLog("Cloning $owner/$repo...")
                    git.clone(owner, repo, owner, token) { p, t -> onGitProgress(p, t) }
                    onOverlayLog("Clone complete.")
                    success = true
                } catch (e: Exception) {
                    lastError = e
                    if (attempt < 3) {
                        delay(delayMs)
                        delayMs *= 2
                    }
                }
                attempt++
            }
            if (!success) onOverlayLog("Clone failed after retries: ${lastError?.message}")
        }
    }

    /**
     * Forks an existing GitHub repository to the user's account.
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

                // Generate a sanitized package name since we can't reliably parse it yet
                var sanitizedUser = newOwner.replace(Regex("[^a-zA-Z0-9]"), "").lowercase()
                if (sanitizedUser.isEmpty() || sanitizedUser[0].isDigit()) sanitizedUser = "_$sanitizedUser"

                var sanitizedApp = response.name.replace(Regex("[^a-zA-Z0-9]"), "").lowercase()
                if (sanitizedApp.isEmpty() || sanitizedApp[0].isDigit()) sanitizedApp = "_$sanitizedApp"

                val generatedPackage = "com.$sanitizedUser.$sanitizedApp"
                settingsViewModel.saveTargetPackageName(generatedPackage)

                // Clone the fork locally so there is an on-device project to edit
                // and preview. Without this the fork existed only on GitHub and the
                // flow dead-ended (no local files, nothing to initialise/preview).
                val projectDir = settingsViewModel.getProjectPath(response.name)
                if (!GitManager(projectDir).isRepo()) {
                    onOverlayLog("Cloning $newOwner/${response.name}...")
                    cloneWithRetry(projectDir, newOwner, response.name, token)
                    if (!GitManager(projectDir).isRepo()) {
                        throw Exception("Failed to clone the forked repository locally.")
                    }
                }

                onSuccess(newOwner, response.name, newBranch)

            } catch (e: Exception) {
                onOverlayLog("Fork failed: ${e.message}")
            } finally {
                onLoadingProgress(null)
            }
        }
    }

    /**
     * Selects an existing repository for setup, inferring settings from metadata and cloning if necessary.
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

                // Generate heuristic package name
                val sanitizedUser = owner.replace(Regex("[^a-zA-Z0-9]"), "").lowercase()
                val sanitizedApp = appName.replace(Regex("[^a-zA-Z0-9]"), "").lowercase()
                val generatedPackage = "com.$sanitizedUser.$sanitizedApp"
                settingsViewModel.saveTargetPackageName(generatedPackage)

                // Check if already cloned locally
                val projectDir = settingsViewModel.getProjectPath(appName)
                val git = GitManager(projectDir)
                if (!git.isRepo()) {
                    onOverlayLog("Cloning $owner/$appName...")
                    val token = settingsViewModel.getGithubToken()

                    // Perform clone on IO thread to prevent ANR
                    withContext(Dispatchers.IO) {
                        try {
                            git.clone(owner, appName, owner, token) { p, t ->
                                onGitProgress(p, t)
                            }
                        } catch (e: Exception) {
                            throw Exception("Clone failed: ${e.message}", e)
                        }
                    }
                    onOverlayLog("Clone complete.")
                }

                onSuccess(owner, defaultBranch)
            } catch (e: Exception) {
                onOverlayLog("Error loading repository: ${e.message}")
            } finally {
                onLoadingProgress(null)
            }
        }
    }

    /**
     * Forces the regeneration and push of initialization files.
     * This includes:
     * - `.github/workflows/` (CI/CD)
     * - `setup_env.sh` (Environment)
     * - `AGENTS.md` (AI Instructions)
     * - `CrashReporter` injection
     */
    fun forceUpdateInitFiles() {
        scope.launch(Dispatchers.IO) {
            val appName = settingsViewModel.getAppName() ?: return@launch
            val projectDir = settingsViewModel.getProjectPath(appName)
            val type = ProjectType.fromString(settingsViewModel.getProjectType())
            val packageName = settingsViewModel.getTargetPackageName() ?: "com.example.app"

            // 1. Generate Files
            ProjectConfigManager.ensureWorkflow(projectDir, type)
            ProjectConfigManager.ensureSetupScript(projectDir)
            ProjectConfigManager.ensureAgentsSetupMd(projectDir)

            // 2. Inject Code (Crash Reporter)
            if (type == ProjectType.ANDROID) {
                ProjectInitializer.injectCrashReporting(application, projectDir, packageName, settingsViewModel)
            }

            // 3. Git Operations (Init, Commit, Push)
            try {
                val git = GitManager(projectDir)
                val token = settingsViewModel.getGithubToken()
                val user = settingsViewModel.getGithubUser()

                // Init if needed
                if (!git.isRepo()) {
                    onOverlayLog("Initializing local repository...")
                    git.init()
                    if (user != null && !appName.isBlank()) {
                        val remoteUrl = "https://github.com/$user/$appName.git"
                        git.addRemote("origin", remoteUrl)

                        // Sync default branch name
                        try {
                            if (!token.isNullOrBlank()) {
                                val service = GitHubApiClient.createService(token)
                                val repoInfo = service.getRepo(user, appName)
                                val remoteDefaultBranch = repoInfo.defaultBranch ?: "main"
                                val localBranch = git.getCurrentBranch() ?: "master"

                                if (localBranch != remoteDefaultBranch) {
                                    onOverlayLog("Renaming local branch '$localBranch' to match remote '$remoteDefaultBranch'...")
                                    git.renameCurrentBranch(remoteDefaultBranch)
                                }
                            }
                        } catch (e: Exception) {
                            // Ignore remote fetch errors during init
                        }
                    }
                }

                // Commit & Push
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
     * Encrypts and uploads project secrets (API keys, keystore) to the repo's
     * GitHub Actions secrets.
     *
     * Each value is sealed for the repository's Actions public key using
     * [GithubSecretBox] (libsodium-compatible `crypto_box_seal`) and PUT via the
     * Actions secrets API. Failures are surfaced per-secret to the build log
     * rather than failing silently.
     */
    fun uploadProjectSecrets(owner: String, repo: String) {
        scope.launch(Dispatchers.Default) {
            try {
                onLog("Uploading project secrets to GitHub...")
                val token = settingsViewModel.getGithubToken()
                if (token.isNullOrBlank()) {
                    onLog("Error: GitHub Token not found. Cannot upload secrets.")
                    return@launch
                }

                val service = GitHubApiClient.createService(token)

                // 1. Fetch the repository's Actions public key.
                val publicKey = try {
                    service.getRepoPublicKey(owner, repo)
                } catch (e: Exception) {
                    onLog("Error fetching public key: ${e.message}")
                    return@launch
                }
                val publicKeyBytes = android.util.Base64.decode(publicKey.key, android.util.Base64.NO_WRAP)

                // 2. Collect the secrets to publish.
                val secrets = mutableMapOf<String, String>()
                settingsViewModel.getApiKey()?.let { secrets["JULES_API_KEY"] = it }
                settingsViewModel.getApiKey(AiModels.GEMINI.requiredKey)?.let { secrets["GEMINI_API_KEY"] = it }
                settingsViewModel.getApiKey("GOOGLE_API_KEY")?.let { secrets["GOOGLE_API_KEY"] = it }
                settingsViewModel.getJulesProjectId()?.let { secrets["JULES_PROJECT_ID"] = it }

                val keystorePath = settingsViewModel.getKeystorePath()
                if (keystorePath != null && File(keystorePath).exists()) {
                    val ksBytes = File(keystorePath).readBytes()
                    secrets["IDEAZ_DEBUG_KEYSTORE_BASE64"] =
                        android.util.Base64.encodeToString(ksBytes, android.util.Base64.NO_WRAP)
                }
                secrets["IDEAZ_DEBUG_KEYSTORE_PASSWORD"] = settingsViewModel.getKeystorePass()
                secrets["IDEAZ_DEBUG_KEY_ALIAS"] = settingsViewModel.getKeyAlias()
                secrets["IDEAZ_DEBUG_KEY_PASSWORD"] = settingsViewModel.getKeyPass()

                // 3. Seal each value for the public key and upload it.
                var uploaded = 0
                secrets.forEach { (name, value) ->
                    try {
                        val sealed = GithubSecretBox.seal(value.toByteArray(Charsets.UTF_8), publicKeyBytes)
                        val encryptedBase64 = android.util.Base64.encodeToString(sealed, android.util.Base64.NO_WRAP)
                        val resp = service.createSecret(
                            owner, repo, name, CreateSecretRequest(encryptedBase64, publicKey.keyId)
                        )
                        if (resp.isSuccessful) uploaded++ else onLog("Failed to upload $name: HTTP ${resp.code()}")
                    } catch (e: Exception) {
                        onLog("Error encrypting/uploading $name: ${e.message}")
                    }
                }
                val msg = "Secrets uploaded ($uploaded/${secrets.size}) to $owner/$repo."
                onLog(msg)
                onOverlayLog(msg)
            } catch (e: Throwable) {
                onLog("Error uploading secrets: ${e.message}")
            }
        }
    }

    /**
     * Scans the app's internal files directory for project folders.
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
     * Used for the "Load Project" UI.
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
