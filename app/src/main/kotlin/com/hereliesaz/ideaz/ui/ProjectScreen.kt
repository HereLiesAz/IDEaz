package com.hereliesaz.ideaz.ui

import android.util.Log
import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import com.hereliesaz.aznavrail.AzButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.dp
import com.hereliesaz.ideaz.api.Source
import androidx.compose.ui.platform.LocalContext
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Tab
import java.net.URL
import androidx.compose.ui.Alignment
import com.hereliesaz.aznavrail.AzForm
import com.hereliesaz.aznavrail.model.AzButtonShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton

private const val TAG = "ProjectScreen"

@Composable
fun ProjectScreen(
    viewModel: MainViewModel,
    sources: List<Source>,
    settingsViewModel: SettingsViewModel
) {
    Log.d(TAG, "ProjectScreen: Composing")
    Log.d(TAG, "ProjectScreen: MainViewModel hash: ${viewModel.hashCode()}")
    Log.d(TAG, "ProjectScreen: SettingsViewModel hash: ${settingsViewModel.hashCode()}")

    val context = LocalContext.current
    var tabIndex by remember { mutableStateOf(0) }
    val tabs = listOf("Setup", "Clone", "Load")

    // Central state for project config
    var appName by remember { mutableStateOf(settingsViewModel.getAppName() ?: "IDEazProject") }
    var githubUser by remember { mutableStateOf(settingsViewModel.getGithubUser() ?: "") }
    var branchName by remember { mutableStateOf(settingsViewModel.getBranchName()) }
    var packageName by remember {
        mutableStateOf(settingsViewModel.getTargetPackageName() ?: "com.example.helloworld")
    }

    // State for "Clone" tab
    var cloneUrl by remember { mutableStateOf("") }
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

    Column(modifier = Modifier.fillMaxSize()) {
        Spacer(modifier = Modifier.height(screenHeight * 0.1f))

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(all = 8.dp)
                .weight(1f)
        ) {
            PrimaryTabRow(selectedTabIndex = tabIndex) {
                tabs.forEachIndexed { index, title ->
                    Tab(text = { Text(title, color = MaterialTheme.colorScheme.onBackground) },
                        selected = tabIndex == index,
                        onClick = { tabIndex = index }
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            when (tabIndex) {
                // --- SETUP TAB ---
                0 -> Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                ) {
                    AzForm(
                        modifier = Modifier.fillMaxWidth(),
                        formName = "Project Configuration",
                        submitButtonContent = { Text("Build") },
                        onSubmit = { formData ->
                            // If user leaves a field blank, use the existing value from state
                            val finalAppName = formData["appName"]?.takeIf { it.isNotBlank() } ?: appName
                            val finalGithubUser = formData["githubUser"]?.takeIf { it.isNotBlank() } ?: githubUser
                            val finalBranchName = formData["branchName"]?.takeIf { it.isNotBlank() } ?: branchName
                            val finalPackageName = formData["packageName"]?.takeIf { it.isNotBlank() } ?: packageName
                            val initialPromptValue = formData["initialPrompt"] ?: ""

                            // Update state with the new values so hints are correct on recomposition
                            appName = finalAppName
                            githubUser = finalGithubUser
                            branchName = finalBranchName
                            packageName = finalPackageName

                            settingsViewModel.saveProjectConfig(finalAppName, finalGithubUser, finalBranchName)
                            settingsViewModel.saveTargetPackageName(finalPackageName)
                            Toast.makeText(context, "Project saved. Starting build...", Toast.LENGTH_SHORT).show()
                            viewModel.sendPrompt(initialPromptValue, isInitialization = true)
                        }
                    ){
                        entry(entryName = "appName", hint = "App Name (Current: $appName)", multiline = false, secret = false)
                        entry(entryName = "githubUser", hint = "Github User (Current: $githubUser)", multiline = false, secret = false)
                        entry(entryName = "branchName", hint = "Branch (Current: $branchName)", multiline = false, secret = false)
                        entry(entryName = "packageName", hint = "Package (Current: $packageName)", multiline = false, secret = false)
                        entry(entryName = "initialPrompt", hint = "Describe your app.", multiline = true, secret = false)
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    AzButton(onClick = {
                        // This just builds and installs the current template project
                        // It's the "first APK"
                        viewModel.startBuild(context)
                        Toast.makeText(context, "Building template...", Toast.LENGTH_SHORT).show()
                    }, text = "Save Template", shape = AzButtonShape.NONE)
                }

                // --- CLONE TAB ---
                1 -> Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        TextField(
                            value = cloneUrl,
                            onValueChange = { cloneUrl = it },
                            label = { Text("Paste a URL to fork it") },
                            placeholder = { Text("https://github.com/user/repo") },
                            modifier = Modifier.weight(1f)
                        )
                        AzButton(onClick = {
                            if (cloneUrl.isNotBlank() && cloneUrl.startsWith("https://github.com/")) {
                                val forkedUrl = cloneUrl.removeSuffix("/") + "/fork"
                                Toast.makeText(context, "Forking at: $forkedUrl", Toast.LENGTH_LONG).show()
                            } else {
                                Toast.makeText(context, "Please enter a valid GitHub URL.", Toast.LENGTH_LONG).show()
                            }
                        }, text = "Fork")
                    }

                    Spacer(modifier = Modifier.height(24.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            "Repositories",
                            style = MaterialTheme.typography.headlineSmall,
                            color = MaterialTheme.colorScheme.onBackground
                        )
                        Spacer(modifier = Modifier.weight(1f))
                        IconButton(onClick = { viewModel.fetchOwnedSources() }) {
                            Icon(
                                imageVector = Icons.Default.Refresh,
                                contentDescription = "Reload Repositories"
                            )
                        }
                    }
                    if (ownedSources.isEmpty()) {
                        Text("No other repositories found on your Jules account.", color = MaterialTheme.colorScheme.onBackground)
                    } else {
                        ownedSources.forEach { source ->
                            val repo = source.githubRepo!!
                            AzButton(
                                onClick = {
                                    appName = repo.repo
                                    githubUser = repo.owner
                                    branchName = repo.defaultBranch.displayName
                                    Toast.makeText(context, "Config loaded. Go to 'Setup' tab to save.", Toast.LENGTH_LONG).show()
                                    tabIndex = 0 // Switch to Setup tab
                                },
                                text = "${repo.owner}/${repo.repo} (Branch: ${repo.defaultBranch.displayName})",
                                modifier = Modifier.padding(bottom = 8.dp).fillMaxWidth()
                            )
                        }
                    }
                }

                // --- LOAD TAB ---
                2 -> LazyColumn(modifier = Modifier.fillMaxSize()) {
                    if (loadableProjects.isEmpty()) {
                        item {
                            Spacer(modifier = Modifier.height(16.dp))
                            Text("No saved projects found.", color = MaterialTheme.colorScheme.onBackground)
                        }
                    } else {
                        items(loadableProjects) { projectString ->
                            Text(
                                text = projectString,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        val parts = projectString.split("/")
                                        if (parts.size == 2) {
                                            viewModel.loadProjectAndBuild(context, parts[1])
                                            Toast.makeText(context, "Loading and building project...", Toast.LENGTH_SHORT).show()
                                        }
                                    }
                                    .padding(16.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}
