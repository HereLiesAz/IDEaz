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
     * Uploads project secrets (API Keys, Keystore) to GitHub for remote builds.
     * Uses Sodium for client-side encryption.
     */
    fun uploadProjectSecrets(owner: String, repo: String) {
        scope.launch {
            try {
                onLog("Uploading project secrets to GitHub...")
                val token = settingsViewModel.getGithubToken()
                if (token.isNullOrBlank()) {
                    onLog("Error: GitHub Token not found. Cannot upload secrets.")
                    return@launch
                }

                val service = GitHubApiClient.createService(token)

                // 1. Fetch Public Key
                val publicKey = try {
                    service.getRepoPublicKey(owner, repo)
                } catch (e: Exception) {
                    onLog("Error fetching public key: ${e.message}")
                    return@launch
                }

                // 2. Prepare Secrets
                val secrets = mutableMapOf<String, String>()
                settingsViewModel.getApiKey()?.let { secrets["JULES_API_KEY"] = it }
                settingsViewModel.getApiKey(com.hereliesaz.ideaz.ui.AiModels.GEMINI.requiredKey)?.let { secrets["GEMINI_API_KEY"] = it }
                settingsViewModel.getApiKey("GOOGLE_API_KEY")?.let { secrets["GOOGLE_API_KEY"] = it }
                settingsViewModel.getJulesProjectId()?.let { secrets["JULES_PROJECT_ID"] = it }

                // Keystore Secrets
                val keystorePath = settingsViewModel.getKeystorePath()
                if (keystorePath != null && File(keystorePath).exists()) {
                    val ksBytes = File(keystorePath).readBytes()
                    val ksBase64 = android.util.Base64.encodeToString(ksBytes, android.util.Base64.NO_WRAP)
                    secrets["IDEAZ_DEBUG_KEYSTORE_BASE64"] = ksBase64
                }
                secrets["IDEAZ_DEBUG_KEYSTORE_PASSWORD"] = settingsViewModel.getKeystorePass()
                secrets["IDEAZ_DEBUG_KEY_ALIAS"] = settingsViewModel.getKeyAlias()
                secrets["IDEAZ_DEBUG_KEY_PASSWORD"] = settingsViewModel.getKeyPass()

                // 3. Encrypt and Upload
                val sodium = com.goterl.lazysodium.LazySodiumAndroid(com.goterl.lazysodium.SodiumAndroid())

                secrets.forEach { (name, value) ->
                    try {
                        val encryptedBytes = ByteArray(com.goterl.lazysodium.interfaces.Box.SEALBYTES + value.length)
                        val keyBytes = android.util.Base64.decode(publicKey.key, android.util.Base64.NO_WRAP)

                        // Use sealed box encryption
                        // Note: LazySodium expects hex or bytes. We have bytes.
                        // Ideally: sodium.cryptoBoxSeal(encryptedBytes, value.toByteArray(), value.length.toLong(), keyBytes)
                        // But simpler wrapper:
                        val encryptedHex = sodium.cryptoBoxSealEasy(value, com.goterl.lazysodium.utils.Key.fromBytes(keyBytes))
                        // GitHub expects Base64 of the encrypted binary
                        // cryptoBoxSealEasy returns HEX string. We need to convert Hex -> Bytes -> Base64.
                        // Or use sodium.cryptoBoxSeal which writes to byte array.

                        // Let's retry with raw bytes to match GitHub's requirement (libsodium sealed box)
                        val res = sodium.cryptoBoxSealEasy(value, com.goterl.lazysodium.utils.Key.fromBytes(keyBytes))
                        // Convert Hex (LazySodium default output) to Base64
                        // We need to implement a HexToBytes helper or use a library one if available.
                        // LazySodium has Key.fromHexString().

                        // Actually, standard approach:
                        // encrypted_value = Base64(Sodium.seal(value, public_key))
                        // LazySodium's cryptoBoxSealEasy returns a hex string.
                        // We need to convert that hex string back to bytes, then base64 encode it.

                        val encryptedDataBytes = com.goterl.lazysodium.utils.Key.fromHexString(res).asBytes
                        val encryptedBase64 = android.util.Base64.encodeToString(encryptedDataBytes, android.util.Base64.NO_WRAP)

                        val req = com.hereliesaz.ideaz.api.CreateSecretRequest(encryptedBase64, publicKey.keyId)
                        val resp = service.createSecret(owner, repo, name, req)

                        if (!resp.isSuccessful) {
                            onLog("Failed to upload $name: ${resp.code()}")
                        }
                    } catch (e: Exception) {
                        onLog("Error encrypting/uploading $name: ${e.message}")
                    }
                }
                onLog("Secrets uploaded successfully.")

            } catch (e: Exception) {
                onLog("Error uploading secrets: ${e.message}")
                e.printStackTrace()
            }
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
