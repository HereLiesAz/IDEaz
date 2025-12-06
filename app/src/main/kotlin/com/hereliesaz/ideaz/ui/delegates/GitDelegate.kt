package com.hereliesaz.ideaz.ui.delegates

import com.hereliesaz.ideaz.git.GitManager
import com.hereliesaz.ideaz.jules.Patch
import com.hereliesaz.ideaz.ui.SettingsViewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

class GitDelegate(
    private val settingsViewModel: SettingsViewModel,
    private val scope: CoroutineScope,
    private val onLog: (String) -> Unit,
    private val onProgress: (Int?) -> Unit
) {

    private val _commitHistory = MutableStateFlow<List<String>>(emptyList())
    val commitHistory = _commitHistory.asStateFlow()

    private val _branches = MutableStateFlow<List<String>>(emptyList())
    val branches = _branches.asStateFlow()

    private val _gitStatus = MutableStateFlow<List<String>>(emptyList())
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

    fun refreshGitData() {
        scope.launch(Dispatchers.IO) {
            val git = getGitManager() ?: return@launch
            try {
                _commitHistory.value = git.getCommitHistory()
                _branches.value = git.getBranches()
                _gitStatus.value = git.getStatus()
            } catch (e: Exception) {
                // Log silently
            }
        }
    }

    suspend fun applyPatch(patch: Patch): Boolean {
        return withContext(Dispatchers.IO) {
            try {
                val appName = settingsViewModel.getAppName() ?: return@withContext false
                val projectDir = settingsViewModel.getProjectPath(appName)

                patch.actions.forEach { action ->
                    val file = File(projectDir, action.filePath)
                    when (action.type) {
                        "CREATE_FILE" -> {
                            file.parentFile?.mkdirs()
                            file.writeText(action.content)
                        }
                        "UPDATE_FILE" -> {
                            if (file.exists()) file.writeText(action.content)
                        }
                        "DELETE_FILE" -> {
                            if (file.exists()) file.delete()
                        }
                    }
                }
                refreshGitData()
                true
            } catch (e: Exception) {
                onLog("[GIT] Patch failed: ${e.message}\n")
                false
            }
        }
    }

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

    fun stash(message: String?) {
        scope.launch(Dispatchers.IO) {
            getGitManager()?.stash(message)
            refreshGitData()
            onLog("[GIT] Stashed changes.\n")
        }
    }

    fun unstash() {
        scope.launch(Dispatchers.IO) {
            getGitManager()?.unstash()
            refreshGitData()
            onLog("[GIT] Unstashed changes.\n")
        }
    }

    fun switchBranch(branch: String) {
        scope.launch(Dispatchers.IO) {
            getGitManager()?.checkout(branch)
            settingsViewModel.saveBranchName(branch)
            refreshGitData()
            onLog("[GIT] Switched to $branch.\n")
        }
    }
}