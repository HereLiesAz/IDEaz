package com.hereliesaz.ideaz.ui.delegates

import com.hereliesaz.ideaz.git.GitManager
import com.hereliesaz.ideaz.ui.SettingsViewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Delegate responsible for Git operations (fetch, pull, push, stash)
 * and managing Git state (history, branches, status).
 *
 * This delegate acts as a high-level wrapper around [GitManager] (which wraps JGit),
 * ensuring all disk I/O and network operations are performed off the main thread.
 *
 * @param settingsViewModel ViewModel to access Git credentials and project path.
 * @param scope CoroutineScope (unused in suspend functions, kept for compatibility if needed).
 * @param onLog Callback to pipe Git logs to the UI (usually prefixed with `[GIT]`).
 * @param onProgress Callback to report operation progress (0-100, or null when done).
 */
class GitDelegate(
    private val settingsViewModel: SettingsViewModel,
    private val scope: CoroutineScope,
    private val onLog: (String) -> Unit,
    private val onProgress: (Int?) -> Unit
) {

    // --- StateFlows ---

    private val _commitHistory = MutableStateFlow<List<String>>(emptyList())
    /** List of recent commits in the current branch (SHA + Message). */
    val commitHistory = _commitHistory.asStateFlow()

    private val _branches = MutableStateFlow<List<String>>(emptyList())
    /** List of available local and remote branches. */
    val branches = _branches.asStateFlow()

    private val _gitStatus = MutableStateFlow<List<String>>(emptyList())
    /** Current status of the working directory (list of modified/untracked files). */
    val gitStatus = _gitStatus.asStateFlow()

    // --- Helper Methods ---

    /**
     * Resolves the [GitManager] instance for the currently active project.
     * Returns null if no project is loaded.
     */
    private fun getGitManager(): GitManager? {
        val appName = settingsViewModel.getAppName() ?: return null
        val projectDir = settingsViewModel.getProjectPath(appName)
        return GitManager(projectDir)
    }

    private fun reportProgress(percent: Int, task: String) {
        onProgress(if (percent >= 100) null else percent)
        onLog("[GIT] $task\n")
    }

    // --- Public Operations ---

    /**
     * Refreshes the Git data (history, branches, status) from the repository.
     * Should be called after any operation that modifies the repo state.
     */
    suspend fun refreshGitData() = withContext(Dispatchers.IO) {
        val git = getGitManager() ?: return@withContext
        try {
            // Sync current branch to settings so the UI knows what we are on
            val currentBranch = git.getCurrentBranch()
            if (currentBranch != null) {
                settingsViewModel.saveBranchName(currentBranch)
            }

            _commitHistory.value = git.getCommitHistory()
            _branches.value = git.getBranches()
            _gitStatus.value = git.getStatus()
        } catch (e: Exception) {
            // Log silently, likely just not a repo yet
        }
    }

    /**
     * Applies a unified diff patch to the local repository using JGit's patching mechanism.
     * This is the primary way the AI agent modifies code.
     *
     * @param diff The Unified Diff string.
     * @return True if applied successfully, False otherwise.
     */
    suspend fun applyUnidiffPatch(diff: String): Boolean {
        return withContext(Dispatchers.IO) {
            try {
                getGitManager()?.applyPatch(diff)
                refreshGitData()
                onLog("[GIT] Unidiff patch applied successfully.\n")
                true
            } catch (e: Exception) {
                onLog("[GIT] Unidiff patch failed: ${e.message}\n")
                false
            }
        }
    }


    /**
     * Fetches changes from the remote repository.
     * Uses credentials from [SettingsViewModel].
     */
    suspend fun fetch() = withContext(Dispatchers.IO) {
        try {
            val git = getGitManager() ?: return@withContext
            val token = settingsViewModel.getGithubToken()
            val user = settingsViewModel.getGithubUser()
            git.fetch(user, token) { p, t -> reportProgress(p, t) }
            refreshGitData()
        } catch (e: Exception) {
            onLog("[GIT] Fetch Error: ${e.message}\n")
        }
    }

    /**
     * Pulls changes from the remote repository (Fetch + Merge).
     */
    suspend fun pull() = withContext(Dispatchers.IO) {
        try {
            val git = getGitManager() ?: return@withContext
            val token = settingsViewModel.getGithubToken()
            val user = settingsViewModel.getGithubUser()
            git.pull(user, token) { p, t -> reportProgress(p, t) }
            refreshGitData()
        } catch (e: Exception) {
            onLog("[GIT] Pull Error: ${e.message}\n")
        }
    }

    /**
     * Commits all current changes with the specified message.
     * Automatically performs `git add .` before committing.
     *
     * @param message The commit message.
     * @return true if successful or no changes to commit, false if failed.
     */
    suspend fun commit(message: String): Boolean = withContext(Dispatchers.IO) {
        try {
            val git = getGitManager() ?: return@withContext false
            if (git.hasChanges()) {
                git.addAll()
                git.commit(message)
                onLog("[GIT] Committed: $message\n")
            } else {
                onLog("[GIT] No changes to commit.\n")
            }
            refreshGitData()
            true
        } catch (e: Exception) {
            onLog("[GIT] Commit Error: ${e.message}\n")
            e.printStackTrace()
            false
        }
    }

    /**
     * Pushes local changes to the remote repository.
     */
    suspend fun push() = withContext(Dispatchers.IO) {
        try {
            val git = getGitManager() ?: return@withContext
            val token = settingsViewModel.getGithubToken()
            val user = settingsViewModel.getGithubUser()
            if (token != null) {
                git.push(user, token) { p, t -> reportProgress(p, t) }
                onLog("[GIT] Push successful.\n")
            } else {
                onLog("[GIT] Error: Missing Auth.\n")
            }
            refreshGitData()
        } catch (e: Exception) {
            onLog("[GIT] Push Error: ${e.message}\n")
        }
    }

    /**
     * Stashes current changes to a temporary storage stack.
     */
    suspend fun stash(message: String?) = withContext(Dispatchers.IO) {
        getGitManager()?.stash(message)
        refreshGitData()
        onLog("[GIT] Stashed changes.\n")
    }

    /**
     * Applies the latest stash from the stack.
     */
    suspend fun unstash() = withContext(Dispatchers.IO) {
        getGitManager()?.unstash()
        refreshGitData()
        onLog("[GIT] Unstashed changes.\n")
    }

    /**
     * Switches to the specified branch.
     */
    suspend fun switchBranch(branch: String) = withContext(Dispatchers.IO) {
        getGitManager()?.checkout(branch)
        settingsViewModel.saveBranchName(branch)
        refreshGitData()
        onLog("[GIT] Switched to $branch.\n")
    }

    /**
     * Retrieves the current HEAD SHA. Useful for version comparisons and build tracking.
     */
    suspend fun getHeadSha(): String? = withContext(Dispatchers.IO) {
        getGitManager()?.getHeadSha()
    }
}
