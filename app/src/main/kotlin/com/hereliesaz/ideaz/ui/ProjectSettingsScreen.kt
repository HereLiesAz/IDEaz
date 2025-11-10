package com.hereliesaz.ideaz.ui

import android.util.Log
import android.widget.Toast
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import com.hereliesaz.aznavrail.AzButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.hereliesaz.ideaz.api.Source
import androidx.compose.ui.platform.LocalContext
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import java.net.URL
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.ui.Alignment

private const val TAG = "ProjectSettingsScreen"

@Composable
fun ProjectSettingsScreen(
    viewModel: MainViewModel,
    sources: List<Source>,
    settingsViewModel: SettingsViewModel
) {
    Log.d(TAG, "ProjectSettingsScreen: Composing")
    Log.d(TAG, "ProjectSettingsScreen: MainViewModel hash: ${viewModel.hashCode()}")
    Log.d(TAG, "ProjectSettingsScreen: SettingsViewModel hash: ${settingsViewModel.hashCode()}")

    val context = LocalContext.current
    var tabIndex by remember { mutableStateOf(0) }
    val tabs = listOf("Create", "Clone", "Load")

    // Central state for project config
    val projectConfig = rememberSaveable(saver = SettingsViewModel.SnapshotStateMapSaver) {
        mutableStateMapOf(
            "appName" to (settingsViewModel.getAppName() ?: "IDEazProject"),
            "githubUser" to (settingsViewModel.getGithubUser() ?: ""),
            "branchName" to settingsViewModel.getBranchName(),
            "packageName" to (settingsViewModel.getTargetPackageName() ?: "com.example.helloworld"),
            "initialPrompt" to "",
            "cloneUrl" to ""
        )
    }
    val projectList = settingsViewModel.getProjectList()
    val ownedSources = sources.filter {
        val repo = it.githubRepo
        repo != null &&
                repo.owner == settingsViewModel.getGithubUser() &&
                !projectList.contains("${repo.owner}/${repo.repo}")
    }

    // State for "Load" tab
    val loadableProjects = projectList.toList()
    val screenHeight = LocalConfiguration.current.screenHeightDp.dp

    Column {
        Spacer(modifier = Modifier.height(screenHeight * 0.1f))
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(all = 8.dp)
                .verticalScroll(rememberScrollState())
        ) {
                TabRow(selectedTabIndex = tabIndex) {
                    tabs.forEachIndexed { index, title ->
                        Tab(text = { Text(title, color = MaterialTheme.colorScheme.onBackground) },
                            selected = tabIndex == index,
                            onClick = { tabIndex = index }
                        )
                    }
                }

                Column {
                    when (tabIndex) {
                        // --- CREATE TAB ---
                        0 -> Column(modifier = Modifier.padding(top = 16.dp)) {
                            Text("Create or Update Project", color = MaterialTheme.colorScheme.onBackground)
                            Spacer(modifier = Modifier.height(16.dp))

                            TextField(
                                value = projectConfig.getValue("appName"),
                                onValueChange = { projectConfig["appName"] = it },
                                label = { Text("App Name (Repo Name)") }
                            )
                            Spacer(modifier = Modifier.height(8.dp))

                            TextField(
                                value = projectConfig.getValue("githubUser"),
                                onValueChange = { projectConfig["githubUser"] = it },
                                label = { Text("GitHub User or Org") }
                            )
                            Spacer(modifier = Modifier.height(8.dp))

                            TextField(
                                value = projectConfig.getValue("branchName"),
                                onValueChange = { projectConfig["branchName"] = it },
                                label = { Text("Branch Name") }
                            )
                            Spacer(modifier = Modifier.height(8.dp))

                            TextField(
                                value = projectConfig.getValue("packageName"),
                                onValueChange = { projectConfig["packageName"] = it },
                                label = { Text("Package Name") }
                            )
                            Spacer(modifier = Modifier.height(16.dp))

                            AzButton(onClick = {
                                settingsViewModel.saveProjectConfig(
                                    projectConfig.getValue("appName"),
                                    projectConfig.getValue("githubUser"),
                                    projectConfig.getValue("branchName")
                                )
                                settingsViewModel.saveTargetPackageName(projectConfig.getValue("packageName"))
                                Toast.makeText(context, "Project Config Saved", Toast.LENGTH_SHORT).show()
                            }, text = "Save Config")

                            Spacer(modifier = Modifier.height(24.dp))
                            Text("Initial Prompt", color = MaterialTheme.colorScheme.onBackground)
                            TextField(
                                value = projectConfig.getValue("initialPrompt"),
                                onValueChange = { projectConfig["initialPrompt"] = it },
                                label = { Text("Describe your app...") },
                                modifier = Modifier.height(150.dp)
                            )
                            Spacer(modifier = Modifier.height(16.dp))

                            AzButton(onClick = {
                                // This just builds and installs the current template project
                                // It's the "first APK"
                                viewModel.startBuild(context)
                                Toast.makeText(context, "Building template...", Toast.LENGTH_SHORT).show()
                            }, text = "Install/Build Template")
                            Spacer(modifier = Modifier.height(8.dp))

                            AzButton(onClick = {
                                // Save config just in case, then send prompt
                                settingsViewModel.saveProjectConfig(
                                    projectConfig.getValue("appName"),
                                    projectConfig.getValue("githubUser"),
                                    projectConfig.getValue("branchName")
                                )
                                settingsViewModel.saveTargetPackageName(projectConfig.getValue("packageName"))
                                // This is a "Project Initialization" prompt (the "second APK")
                                viewModel.sendPrompt(projectConfig.getValue("initialPrompt"), isInitialization = true)
                            }, text = "Create Project & Build")
                        }

                        // --- CLONE TAB ---
                        1 -> Column(modifier = Modifier.padding(top = 16.dp)) {
                            Text("Fork External Repo (Not Supported)", color = MaterialTheme.colorScheme.onBackground)
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                TextField(
                                    value = projectConfig.getValue("cloneUrl"),
                                    onValueChange = { projectConfig["cloneUrl"] = it },
                                    label = { Text("Other User's Repo URL") },
                                    placeholder = { Text("https://github.com/user/repo") },
                                    modifier = Modifier.weight(1f)
                                )
                                AzButton(onClick = {
                                    val currentUser = settingsViewModel.getGithubUser()
                                    var owner: String? = null
                                    try {
                                        val path = URL(projectConfig.getValue("cloneUrl")).path.removePrefix("/").removeSuffix(".git")
                                        owner = path.split("/").getOrNull(0)
                                    } catch (e: Exception) { /* Malformed URL */ }

                                    if (owner != null && owner == currentUser) {
                                        Toast.makeText(context, "This is your repo. Select it from the list below or the 'Load' tab.", Toast.LENGTH_LONG).show()
                                    } else {
                                        Toast.makeText(context, "Forking is not supported. Please fork on GitHub and register the forked repo with Jules first.", Toast.LENGTH_LONG).show()
                                    }
                                }, text = "Fork")
                            }

                            Spacer(modifier = Modifier.height(24.dp))
                            Text("Your Available Repositories", color = MaterialTheme.colorScheme.onBackground)
                            if (ownedSources.isEmpty()) {
                                Text("No other repositories found on your Jules account.", color = MaterialTheme.colorScheme.onBackground)
                            } else {
                                ownedSources.forEach { source ->
                                    val repo = source.githubRepo!!
                                    AzButton(
                                        onClick = {
                                            projectConfig["appName"] = repo.repo
                                            projectConfig["githubUser"] = repo.owner
                                            projectConfig["branchName"] = repo.defaultBranch.displayName
                                            Toast.makeText(context, "Config loaded. Go to 'Create' tab to save.", Toast.LENGTH_LONG).show()
                                            tabIndex = 0 // Switch to Create tab
                                        },
                                        text = "${repo.owner}/${repo.repo} (Branch: ${repo.defaultBranch.displayName})",
                                        modifier = Modifier.padding(bottom = 8.dp).fillMaxWidth()
                                    )
                                }
                            }
                        }

                        // --- LOAD TAB ---
                        2 -> Column(modifier = Modifier.padding(top = 16.dp)) {
                            Text("Load Saved Project", color = MaterialTheme.colorScheme.onBackground)
                            Spacer(modifier = Modifier.height(16.dp))

                            if (loadableProjects.isEmpty()) {
                                Text("No saved projects found.", color = MaterialTheme.colorScheme.onBackground)
                            } else {
                                loadableProjects.forEach { projectString ->
                                    AzButton(
                                        onClick = {
                                            val parts = projectString.split("/")
                                            if (parts.size == 2) {
                                                projectConfig["githubUser"] = parts[0]
                                                projectConfig["appName"] = parts[1]
                                                projectConfig["branchName"] = settingsViewModel.getBranchName() // Load saved branch
                                                Toast.makeText(context, "Config loaded. Go to 'Create' tab to save or build.", Toast.LENGTH_LONG).show()
                                                tabIndex = 0 // Switch to Create tab
                                            }
                                        },
                                        text = projectString,
                                        modifier = Modifier.padding(bottom = 8.dp).fillMaxWidth()
                                    )
                                }
                            }
                        }
                    }
                }
            }
    }
}