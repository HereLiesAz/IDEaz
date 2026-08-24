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
import com.hereliesaz.ideaz.git.GitManager
import com.hereliesaz.ideaz.ui.AiModels
import com.hereliesaz.ideaz.ui.ProjectMetadata
import com.hereliesaz.ideaz.ui.SettingsViewModel
import com.hereliesaz.ideaz.utils.GithubSecretBox
import com.hereliesaz.ideaz.utils.ProjectAnalyzer
import com.hereliesaz.ideaz.utils.ProjectConfigManager
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
 * - **Setup:** Initializing local project state and ensuring config files exist.
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

    private val _repoFetchError = MutableStateFlow<String?>(null)
    /**
     * Non-null after [fetchGitHubRepos] fails (network error, bad token, etc.).
     * An empty [ownedRepos] with a null error means the account genuinely has none -
     * without this, both cases rendered as the same "No repositories found." message.
     */
    val repoFetchError = _repoFetchError.asStateFlow()

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
            _repoFetchError.value = null
            try {
                val token = settingsViewModel.getGithubToken()
                if (token.isNullOrBlank()) {
                    onOverlayLog("Error: No GitHub Token found.")
                    return@launch
                }

                val service = GitHubApiClient.createService(token)
                val repos = service.listRepos()
                _ownedRepos.value = repos

            } catch (e: Exception) {
                onOverlayLog("Error fetching repos: ${e.message}")
                // Distinct from "the account genuinely has zero repos" - previously
                // only logged to the overlay console, so an offline user with a
                // valid token saw the exact same "No repositories found." empty
                // state as someone who really has none, with no retry affordance
                // beyond the generic refresh icon.
                _repoFetchError.value = e.message ?: "Could not load repositories."
                // CloneTab only renders the error/retry state when ownedRepos is
                // empty - leaving a stale list in place (e.g. after a token is
                // revoked and the next fetch throws 401) silently hid this error
                // behind the last-known-good list.
                _ownedRepos.value = emptyList()
            } finally {
                onLoadingProgress(null)
            }
        }
    }

    /**
     * Makes sure the current project has a GitHub repository and an `origin`
     * remote pointing at it, creating the repository when it does not exist yet.
     *
     * Creating a project used to go through GitHub first: the Create button was
     * hard-gated on a token, called `generateFromTemplate` to make a
     * pre-populated remote, then cloned it back down. That meant a named project
     * could not be scaffolded at all without an account — the README promised
     * otherwise, and the local path only worked if you accepted the default
     * project name, because the App Name field is read-only outside Create mode.
     *
     * Projects are created locally now, and this runs on the first Deploy, which
     * is the point where a token has an obvious purpose.
     *
     * @return true when the project is ready to push; false when it is not (no
     *   token, or the API call failed) — the reason is reported to the log.
     */
    suspend fun ensureRemoteRepository(appName: String): Boolean = withContext(Dispatchers.IO) {
        val git = GitManager(settingsViewModel.getProjectPath(appName))
        if (git.remoteUrl("origin") != null) return@withContext true

        val token = settingsViewModel.getGithubToken()
        if (token.isNullOrBlank()) {
            onOverlayLog(
                "Deploy needs a GitHub token to publish $appName. Add one in Settings — " +
                    "creating, editing and previewing all work without an account."
            )
            return@withContext false
        }

        try {
            val service = GitHubApiClient.createService(token)
            val configuredUser = settingsViewModel.getGithubUser()?.takeIf { it.isNotBlank() }

            // Reuse the repository when it already exists — a re-Deploy after the
            // local remote was dropped, or a repo the user made by hand. Only
            // create when the lookup genuinely fails.
            val existing = configuredUser?.let {
                try { service.getRepo(it, appName) } catch (e: Exception) { null }
            }

            val repo = existing ?: run {
                onOverlayLog("Creating $appName on GitHub...")
                service.createRepo(
                    CreateRepoRequest(
                        name = appName,
                        description = settingsViewModel.getRepoDescription(),
                        private = false,
                        // No auto-init: the local project already has commits, and
                        // an auto-initialised remote would need a merge before the
                        // first push could fast-forward.
                        autoInit = false,
                    )
                )
            }

            val owner = repo.fullName.split("/")[0]
            val branch = git.getCurrentBranch() ?: repo.defaultBranch ?: "main"
            settingsViewModel.setGithubUser(owner)
            settingsViewModel.saveProjectConfig(appName, owner, branch)

            // addRemote() opens the local .git directory directly and throws
            // if there isn't one. Without this guard, a project that reached
            // here without ever being git-inited (e.g. loaded some other
            // way) hit that exception after the GitHub repo above had
            // already been created - reported as "could not create the
            // repository" even though it now existed, and every retry
            // re-found that same repo via the lookup above and failed here
            // again, permanently: nothing on this path ever fixed the local
            // directory.
            if (!git.isRepo()) git.init()

            git.addRemote("origin", "https://github.com/$owner/$appName.git")
            onOverlayLog("Linked $appName to $owner/$appName.")
            true
        } catch (e: Exception) {
            onOverlayLog("Could not create the GitHub repository: ${e.message}")
            false
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
            if (!success) {
                val msg = "Clone failed after retries: ${lastError?.message}"
                onOverlayLog(msg)
                // Previously this only logged and returned normally, so both call
                // sites went straight on to report "Repository created" / invoke
                // onSuccess and proceed to saveAndInitialize against what is still
                // an empty directory - a repo/fork that visibly exists on GitHub
                // but was never actually pulled down. Throwing routes into the
                // callers' existing catch blocks instead.
                throw IllegalStateException(msg, lastError)
            }
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

                // Re-derive from what is actually on disk, the same check
                // MainViewModel.loadProject makes. This used to set a global
                // ProjectType and refuse anything outside `ProjectType.selectable`;
                // without the re-detection a cloned repo silently inherited the
                // previously open project's type, and secrets were uploaded and CI
                // workflows pushed for a project the release did not support.
                if (!withContext(Dispatchers.IO) { ProjectAnalyzer.isPreviewable(projectDir) }) {
                    onOverlayLog(
                        "$appName has no index.html or package.json, so IDEaz cannot " +
                            "preview it. The repository was cloned but not initialized."
                    )
                    return@launch
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
     * Forces the regeneration and push of initialization files:
     * `.github/workflows/web_ci_pages.yml`, `AGENTS_SETUP.md` and
     * `version.properties`.
     *
     * `setup_env.sh` (a JDK + Android SDK bootstrap) and the `CrashReporter`
     * injection used to ride along here. Both existed for the Android edit
     * target and are gone with it.
     */
    fun forceUpdateInitFiles() {
        scope.launch(Dispatchers.IO) {
            val appName = settingsViewModel.getAppName() ?: return@launch
            val projectDir = settingsViewModel.getProjectPath(appName)

            // Generate files - collect exactly what changed so the commit below
            // stages only these paths, never unrelated uncommitted work sitting
            // in the same working tree (see GitManager.addPaths).
            val writtenPaths = mutableListOf<String>()
            writtenPaths += ProjectConfigManager.ensureVersioning(projectDir)
            writtenPaths += ProjectConfigManager.ensureWorkflow(projectDir)
            writtenPaths += ProjectConfigManager.ensureAgentsSetupMd(projectDir)

            if (writtenPaths.isEmpty()) {
                onOverlayLog("Init files already up to date — nothing to regenerate.")
                return@launch
            }
            onOverlayLog("Regenerated: ${writtenPaths.joinToString(", ")}")

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

                // Commit & Push — staged to exactly writtenPaths, not addAll(),
                // so this can never silently sweep up unrelated pending work.
                git.addPaths(writtenPaths)
                git.commit("IDEaz: Update Init Files & Workflows")
                if (token != null && user != null) {
                    git.push(user, token) { progress, task -> onGitProgress(progress, task) }
                    onOverlayLog("Init files pushed successfully.")
                }
            } catch (e: Exception) {
                onOverlayLog("Error pushing init files: ${e.message}")
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
