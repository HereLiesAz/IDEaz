package com.hereliesaz.ideaz.ui.delegates

import com.hereliesaz.ideaz.git.GitManager
import com.hereliesaz.ideaz.ui.SettingsViewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Delegate responsible for Git operations (fetch, pull, push, stash)
 * and managing Git state (history, branches, status).
 *
 * @param settingsViewModel ViewModel to access Git credentials and project path.
 * @param scope CoroutineScope for background Git operations.
 * @param onLog Callback to pipe Git logs to the UI.
 * @param onProgress Callback to report operation progress.
 */
class GitDelegate(
    private val settingsViewModel: SettingsViewModel,
    private val scope: CoroutineScope,
    private val onLog: (String) -> Unit,
    private val onProgress: (Int?) -> Unit
) {

    private val _commitHistory = MutableStateFlow<List<String>>(emptyList())
    /** List of recent commits in the current branch. */
    val commitHistory = _commitHistory.asStateFlow()

    private val _branches = MutableStateFlow<List<String>>(emptyList())
    /** List of available local/remote branches. */
    val branches = _branches.asStateFlow()

    private val _gitStatus = MutableStateFlow<List<String>>(emptyList())
    /** Current status of the working directory (modified files). */
    val gitStatus = _gitStatus.asStateFlow()

    private fun getGitManager(): GitManager? {
        val appName = settingsViewModel.getAppName() ?: return null
        val projectDir = settingsViewModel.getProjectPath(appName)
        return GitManager(projectDir)
    }

    private fun reportProgress(percent: Int, task: String) {
        scope.launch {
            onProgress(if (percent >= 100) null else percent)
            onLog("[GIT] $task\n")
        }
    }

    /**
     * Refreshes the Git data (history, branches, status) from the repository.
     */
    fun refreshGitData() {
        scope.launch(Dispatchers.IO) {
            val git = getGitManager() ?: return@launch
            try {
                // Sync current branch to settings
                val currentBranch = git.getCurrentBranch()
                if (currentBranch != null) {
                    settingsViewModel.saveBranchName(currentBranch)
                }

                _commitHistory.value = git.getCommitHistory()
                _branches.value = git.getBranches()
                _gitStatus.value = git.getStatus()
            } catch (e: Exception) {
                // Log silently
            }
        }
    }

    /**
     * Applies a unified diff patch to the local repository using JGit.
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
     */
    fun fetch() {
        scope.launch(Dispatchers.IO) {
            try {
                val git = getGitManager() ?: return@launch
                val token = settingsViewModel.getGithubToken()
                val user = settingsViewModel.getGithubUser()
                git.fetch(user, token) { p, t -> reportProgress(p, t) }
                refreshGitData()
            } catch (e: Exception) {
                onLog("[GIT] Fetch Error: ${e.message}\n")
            }
        }
    }

    /**
     * Pulls changes from the remote repository.
     */
    fun pull() {
        scope.launch(Dispatchers.IO) {
            try {
                val git = getGitManager() ?: return@launch
                val token = settingsViewModel.getGithubToken()
                val user = settingsViewModel.getGithubUser()
                git.pull(user, token) { p, t -> reportProgress(p, t) }
                refreshGitData()
            } catch (e: Exception) {
                onLog("[GIT] Pull Error: ${e.message}\n")
            }
        }
    }

    /**
     * Commits changes with a message.
     */
    fun commit(message: String) {
        scope.launch(Dispatchers.IO) {
            try {
                val git = getGitManager() ?: return@launch
                if (git.hasChanges()) {
                    git.addAll()
                    git.commit(message)
                    onLog("[GIT] Committed: $message\n")
                } else {
                    onLog("[GIT] No changes to commit.\n")
                }
                refreshGitData()
            } catch (e: Exception) {
                onLog("[GIT] Commit Error: ${e.message}\n")
            }
        }
    }

    /**
     * Pushes local changes to the remote repository.
     */
    fun push() {
        scope.launch(Dispatchers.IO) {
            try {
                val git = getGitManager() ?: return@launch
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
    }

    /**
     * Stashes current changes.
     */
    fun stash(message: String?) {
        scope.launch(Dispatchers.IO) {
            getGitManager()?.stash(message)
            refreshGitData()
            onLog("[GIT] Stashed changes.\n")
        }
    }

    /**
     * Applies the latest stash.
     */
    fun unstash() {
        scope.launch(Dispatchers.IO) {
            getGitManager()?.unstash()
            refreshGitData()
            onLog("[GIT] Unstashed changes.\n")
        }
    }

    /**
     * Switches to the specified branch.
     */
    fun switchBranch(branch: String) {
        scope.launch(Dispatchers.IO) {
            getGitManager()?.checkout(branch)
            settingsViewModel.saveBranchName(branch)
            refreshGitData()
            onLog("[GIT] Switched to $branch.\n")
        }
    }
}
