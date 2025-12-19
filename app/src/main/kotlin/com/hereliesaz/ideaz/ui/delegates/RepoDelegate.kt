package com.hereliesaz.ideaz.ui.delegates

import android.app.Application
import android.content.Context
import android.widget.Toast
import com.hereliesaz.ideaz.api.GitHubRepoResponse
import com.hereliesaz.ideaz.models.ProjectItem
import com.hereliesaz.ideaz.models.ProjectType
import com.hereliesaz.ideaz.ui.ProjectMetadata
import com.hereliesaz.ideaz.ui.SettingsViewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

class RepoDelegate(
    private val application: Application,
    private val settings: SettingsViewModel,
    private val scope: CoroutineScope,
    private val onLog: (String) -> Unit,
    private val onAiLog: (String) -> Unit,
    private val onProgress: (Int?) -> Unit,
    private val onGitProgress: (Int, String) -> Unit
) {

    private val _ownedRepos = MutableStateFlow<List<GitHubRepoResponse>>(emptyList())
    val ownedRepos = _ownedRepos.asStateFlow()

    private val _localProjects = MutableStateFlow<List<ProjectMetadata>>(emptyList())
    val localProjects = _localProjects.asStateFlow()

    fun fetchGitHubRepos() {
        onProgress(0)
        scope.launch(Dispatchers.IO) {
            try {
                onLog("Fetching repositories...")
                // Correct constructor usage for stub
                val dummy = listOf(
                    GitHubRepoResponse(
                        id = 1,
                        name = "IDEaz",
                        fullName = "HereLiesAz/IDEaz",
                        htmlUrl = "https://github.com/HereLiesAz/IDEaz",
                        private = false,
                        defaultBranch = "main"
                    )
                )
                _ownedRepos.value = dummy
                withContext(Dispatchers.Main) { onProgress(null) }
            } catch (e: Exception) {
                onLog("Error fetching repos: ${e.message}")
                withContext(Dispatchers.Main) { onProgress(null) }
            }
        }
    }

    fun selectRepositoryForSetup(repo: GitHubRepoResponse, callback: (String, String) -> Unit) {
        settings.setAppName(repo.name)
        // Correct usage of full_name property
        settings.setGithubUser(repo.fullName.split("/")[0])
        // Construct clone_url from htmlUrl
        callback(repo.name, repo.htmlUrl + ".git")
    }

    fun createGitHubRepository(
        name: String, desc: String, private: Boolean,
        type: ProjectType, pkg: String, ctx: Context,
        callback: (String, String) -> Unit
    ) {
        onLog("Creating repository $name...")
        scope.launch {
            callback(name, "https://github.com/user/$name.git")
        }
    }

    fun forkRepository(owner: String, repo: String, callback: (String, String, String) -> Unit) {
        onLog("Forking $owner/$repo...")
        callback("Me", repo, "https://github.com/Me/$repo.git")
    }

    fun scanLocalProjects() {}

    fun getLocalProjectsWithMetadata(): List<ProjectMetadata> {
        return emptyList()
    }

    fun forceUpdateInitFiles() {
        onLog("Forcing update of init files...")
    }

    fun deleteProject(path: String) {
        onLog("Deleting project at $path...")
    }

    fun uploadProjectSecrets(owner: String, repo: String) {
        onLog("Uploading secrets to $owner/$repo...")
    }

    fun getProjectConfig(path: String): Pair<ProjectType, String> {
        return Pair(ProjectType.ANDROID, "com.example.app")
    }

    fun createProject(name: String, path: String, type: ProjectType, packageName: String) {}
}