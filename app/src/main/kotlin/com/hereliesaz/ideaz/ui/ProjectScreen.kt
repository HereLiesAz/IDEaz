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
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.foundation.layout.Box
import androidx.compose.material3.CircularProgressIndicator
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
import com.hereliesaz.aznavrail.AzTextBox

private const val TAG = "ProjectScreen"

@Composable
fun ProjectScreen(
    viewModel: MainViewModel,
    settingsViewModel: SettingsViewModel,
    onBuildTriggered: () -> Unit
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

    // NEW: Collect sources from ViewModel and use local githubUser state for filtering
    val allSources by viewModel.ownedSources.collectAsState()
    val isLoadingSources by viewModel.isLoadingSources.collectAsState()
    val availableSessions by viewModel.availableSessions.collectAsState()

    // Auto-refresh sources when entering the screen
    LaunchedEffect(Unit) {
        viewModel.fetchOwnedSources()
    }

    // State for "Clone" tab
    var cloneUrl by remember { mutableStateOf("") }
    val projectList = settingsViewModel.getProjectList()
    val ownedSources = allSources.filter {
        val repo = it.githubRepo
        // REMOVED: repo.owner == githubUser check is redundant and breaks filtering when githubUser is unset
        repo != null &&
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
            // Display currently selected repository
            if (appName.isNotBlank()) {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 16.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant,
                    )
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = "Current Repository",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = if (githubUser.isNotBlank()) "$githubUser/$appName" else appName,
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = "Branch: $branchName",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

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
                        submitButtonContent = { Text("Save") },
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
                            Toast.makeText(context, "Project saved.", Toast.LENGTH_SHORT).show()
                            viewModel.initializeProject(initialPromptValue)
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
                        // This builds and installs the current project
                        viewModel.startBuild(context)
                        onBuildTriggered()
                        Toast.makeText(context, "Building...", Toast.LENGTH_SHORT).show()
                    }, text = "Build", shape = AzButtonShape.RECTANGLE)

                    Spacer(modifier = Modifier.height(24.dp))

                    if (availableSessions.isNotEmpty()) {
                        Text(
                            "Active Sessions",
                            style = MaterialTheme.typography.headlineSmall,
                            color = MaterialTheme.colorScheme.onBackground
                        )
                        Spacer(modifier = Modifier.height(8.dp))

                        availableSessions.forEach { session ->
                            val sessionId = session.name.substringAfterLast("/")
                            Card(
                                modifier = Modifier
                                    .padding(bottom = 8.dp)
                                    .fillMaxWidth()
                                    .clickable {
                                        viewModel.setActiveSession(sessionId)
                                        Toast.makeText(context, "Resumed session: $sessionId", Toast.LENGTH_SHORT).show()
                                    },
                                colors = CardDefaults.cardColors(
                                    containerColor = MaterialTheme.colorScheme.surface,
                                    contentColor = MaterialTheme.colorScheme.onSurface
                                ),
                                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                            ) {
                                Column(modifier = Modifier.padding(16.dp)) {
                                    Text(
                                        text = session.title ?: "Untitled Session",
                                        style = MaterialTheme.typography.titleMedium
                                    )
                                    Text(
                                        text = "ID: $sessionId",
                                        style = MaterialTheme.typography.bodySmall
                                    )
                                    if (session.updateTime != null) {
                                        Text(
                                            text = "Last Updated: ${session.updateTime}",
                                            style = MaterialTheme.typography.bodySmall
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                // --- CLONE TAB ---
                1 -> LazyColumn(
                    modifier = Modifier.fillMaxSize()
                ) {
                    item {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            AzTextBox(
                                value = cloneUrl,
                                onValueChange = { cloneUrl = it },
                                hint = "https://github.com/user/repo",
                                modifier = Modifier.weight(1f),
                                onSubmit = {
                                    if (cloneUrl.isNotBlank() && cloneUrl.startsWith("https://github.com/")) {
                                        val forkedUrl = cloneUrl.removeSuffix("/") + "/fork"
                                        Toast.makeText(context, "Forking at: $forkedUrl", Toast.LENGTH_LONG).show()
                                    } else {
                                        Toast.makeText(
                                            context,
                                            "Please enter a valid GitHub URL.",
                                            Toast.LENGTH_LONG
                                        ).show()
                                    }
                                },
                                submitButtonContent = { Text("Fork") }
                            )
                        }
                    }

                    item {
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
                    }

                    if (isLoadingSources) {
                        item {
                            Box(
                                modifier = Modifier.fillMaxWidth(),
                                contentAlignment = Alignment.Center
                            ) {
                                CircularProgressIndicator()
                            }
                        }
                    } else if (ownedSources.isEmpty()) {
                        item {
                            Text(
                                "No other repositories found on your Jules account.",
                                color = MaterialTheme.colorScheme.onBackground
                            )
                        }
                    } else {
                        items(ownedSources) { source ->
                            val repo = source.githubRepo
                            if (repo != null) {
                                Card(
                                    modifier = Modifier
                                        .padding(bottom = 8.dp)
                                        .fillMaxWidth()
                                        .clickable {
                                            appName = repo.repo
                                            githubUser = repo.owner
                                            branchName = repo.defaultBranch?.displayName ?: "main"
                                            viewModel.cloneOrPullProject(repo.owner, repo.repo, branchName ?: "main")
                                            Toast.makeText(
                                                context,
                                                "Repository selected. Syncing...",
                                                Toast.LENGTH_SHORT
                                            ).show()
                                        },
                                    colors = CardDefaults.cardColors(
                                        containerColor = MaterialTheme.colorScheme.surface,
                                        contentColor = MaterialTheme.colorScheme.onSurface
                                    ),
                                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                                ) {
                                    Column(modifier = Modifier.padding(16.dp)) {
                                        Text(
                                            text = "${repo.owner}/${repo.repo}",
                                            style = MaterialTheme.typography.titleMedium
                                        )
                                        Text(
                                            text = "Branch: ${repo.defaultBranch?.displayName ?: "main"}",
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                                        )
                                    }
                                }
                            }
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