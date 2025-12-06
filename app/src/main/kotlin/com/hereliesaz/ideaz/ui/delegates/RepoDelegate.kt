package com.hereliesaz.ideaz.ui.delegates

import android.app.Application
import android.content.Context
import android.util.Log
import com.hereliesaz.ideaz.api.CreateRepoRequest
import com.hereliesaz.ideaz.api.CreateSecretRequest
import com.hereliesaz.ideaz.api.GitHubApiClient
import com.hereliesaz.ideaz.api.GitHubRepoResponse
import com.hereliesaz.ideaz.git.GitManager
import com.hereliesaz.ideaz.models.ProjectType
import com.hereliesaz.ideaz.ui.ProjectMetadata
import com.hereliesaz.ideaz.ui.SettingsViewModel
import com.hereliesaz.ideaz.utils.ProjectConfigManager
import com.goterl.lazysodium.LazySodiumAndroid
import com.goterl.lazysodium.SodiumAndroid
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

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
    val ownedRepos = _ownedRepos.asStateFlow()

    fun fetchGitHubRepos() {
        scope.launch {
            onLoadingProgress(0)
            try {
                val token = settingsViewModel.getGithubToken()
                if (!token.isNullOrBlank()) {
                    val service = GitHubApiClient.createService(token)
                    val repos = service.listRepos()
                    _ownedRepos.value = repos
                } else {
                    onOverlayLog("Error: No GitHub Token found.")
                }
            } catch (e: Exception) {
                onOverlayLog("Error fetching repos: ${e.message}")
            } finally {
                onLoadingProgress(null)
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

    fun forceUpdateInitFiles() {
        scope.launch(Dispatchers.IO) {
            val appName = settingsViewModel.getAppName() ?: return@launch
            val projectDir = settingsViewModel.getProjectPath(appName)
            val type = ProjectType.fromString(settingsViewModel.getProjectType())

            ProjectConfigManager.ensureWorkflow(application, projectDir, type)
            ProjectConfigManager.ensureSetupScript(projectDir)
            ProjectConfigManager.ensureAgentsSetupMd(projectDir)

            try {
                val git = GitManager(projectDir)
                if (git.hasChanges()) {
                    git.addAll()
                    git.commit("IDEaz: Update Init Files & Workflows")
                    val token = settingsViewModel.getGithubToken()
                    val user = settingsViewModel.getGithubUser()
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

    fun uploadProjectSecrets(owner: String, repo: String) {
        scope.launch(Dispatchers.IO) {
            try {
                val token = settingsViewModel.getGithubToken()
                if (token.isNullOrBlank()) {
                    onOverlayLog("Cannot upload secrets: No GitHub Token")
                    return@launch
                }

                // FIX: Force JNA to use the bundled library, NOT the system one.
                // This fixes the "Incompatible JNA native library" crash.
                System.setProperty("jna.nosys", "true")
                try {
                    System.setProperty("jna.boot.library.path", application.applicationInfo.nativeLibraryDir)
                } catch (e: Exception) {
                    Log.w("RepoDelegate", "Failed to set JNA path", e)
                }

                val service = GitHubApiClient.createService(token)

                // CRITICAL: Wrap Encryption logic in a broad catch block to prevent crashes
                try {
                    val publicKey = service.getRepoPublicKey(owner, repo)
                    val keyId = publicKey.keyId
                    val keyBytes = android.util.Base64.decode(publicKey.key, android.util.Base64.DEFAULT)

                    // This line triggers JNA loading. If it fails, we catch Throwable.
                    val lazySodium = LazySodiumAndroid(SodiumAndroid())
                    val sealBytes = 48

                    suspend fun encryptAndUpload(name: String, value: String) {
                        try {
                            val valueBytes = value.toByteArray(Charsets.UTF_8)
                            val encryptedBytes = ByteArray(sealBytes + valueBytes.size)
                            lazySodium.cryptoBoxSeal(encryptedBytes, valueBytes, valueBytes.size.toLong(), keyBytes)
                            val encryptedBase64 = android.util.Base64.encodeToString(encryptedBytes, android.util.Base64.NO_WRAP)
                            service.createSecret(owner, repo, name, CreateSecretRequest(encryptedBase64, keyId))
                            onOverlayLog("Uploaded secret: $name")
                        } catch (e: Exception) {
                            onOverlayLog("Failed to upload secret $name")
                        }
                    }

                    val geminiKey = settingsViewModel.getApiKey()
                    if (!geminiKey.isNullOrBlank()) encryptAndUpload("GEMINI_API_KEY", geminiKey)

                    val googleKey = settingsViewModel.getGoogleApiKey()
                    if (!googleKey.isNullOrBlank()) encryptAndUpload("GOOGLE_API_KEY", googleKey)

                } catch (t: Throwable) {
                    // CATCH JNA/Sodium Errors here so the app doesn't die.
                    onOverlayLog("Warning: Secrets upload skipped (Encryption unavailable on this device).")
                    Log.e("RepoDelegate", "JNA/Sodium Error", t)
                }

            } catch (e: Exception) {
                onOverlayLog("Error uploading secrets: ${e.message}")
            }
        }
    }

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

    fun getLocalProjectsWithMetadata(): List<ProjectMetadata> {
        val root = application.filesDir
        val projects = settingsViewModel.getProjectList()
        return projects.mapNotNull { name ->
            val dir = File(root, name)
            if (dir.exists()) ProjectMetadata(name, dir.walkTopDown().sumOf { it.length() }) else null
        }
    }
}