package com.hereliesaz.ideaz.ui

import android.util.Log
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import com.hereliesaz.aznavrail.AzButton
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.Alignment
import com.hereliesaz.aznavrail.model.AzButtonShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Delete
import com.hereliesaz.aznavrail.AzTextBox
import com.hereliesaz.ideaz.models.ProjectType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private const val TAG = "ProjectScreen"

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProjectScreen(
    viewModel: MainViewModel,
    settingsViewModel: SettingsViewModel,
    onBuildTriggered: () -> Unit
) {
    Log.d(TAG, "ProjectScreen: Composing")

    val context = LocalContext.current
    val hasToken = !settingsViewModel.getGithubToken().isNullOrBlank()

    val tabs = listOf("Connect", "Recent", "Create")
    var tabIndex by remember { mutableStateOf(0) }

    val currentAppNameState by settingsViewModel.currentAppName.collectAsState()
    val currentPackageName by settingsViewModel.targetPackageName.collectAsState()
    val loadingProgress by viewModel.loadingProgress.collectAsState()

    var appName by remember { mutableStateOf("") }
    var githubUser by remember { mutableStateOf("") }
    var branchName by remember { mutableStateOf("main") }
    var packageName by remember { mutableStateOf("com.example.app") }
    var selectedType by remember { mutableStateOf(ProjectType.ANDROID) }

    var repoDescription by remember { mutableStateOf("Created with IDEaz") }
    var isPrivateRepo by remember { mutableStateOf(false) }

    LaunchedEffect(currentAppNameState, currentPackageName) {
        appName = settingsViewModel.getAppName() ?: "IDEazProject"
        githubUser = settingsViewModel.getGithubUser() ?: ""
        branchName = settingsViewModel.getBranchName()
        packageName = currentPackageName ?: "com.example.helloworld"
        selectedType = ProjectType.fromString(settingsViewModel.getProjectType())
    }

    val allSources by viewModel.ownedSources.collectAsState()
    val isLoadingSources by viewModel.isLoadingSources.collectAsState()

    LaunchedEffect(Unit) {
        if (hasToken) viewModel.fetchOwnedSources()
    }

    val currentTabName = tabs.getOrElse(tabIndex) { "Connect" }

    if (loadingProgress != null) {
        AlertDialog(
            onDismissRequest = {},
            title = { Text("Working...") },
            text = {
                Column {
                    LinearProgressIndicator(
                        progress = { (loadingProgress ?: 0f) / 100f },
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text("${loadingProgress}% complete")
                }
            },
            confirmButton = {}
        )
    }

    Column(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        Spacer(modifier = Modifier.height(32.dp))

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(all = 8.dp)
                .weight(1f)
        ) {
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
                    Tab(text = { Text(title) },
                        selected = tabIndex == index,
                        onClick = { tabIndex = index }
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            if (currentTabName == "Connect") {
                LazyColumn(modifier = Modifier.weight(1f)) {
                    item {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                "Repositories",
                                style = MaterialTheme.typography.headlineSmall,
                                color = MaterialTheme.colorScheme.onBackground
                            )
                            Spacer(modifier = Modifier.weight(1f))
                            IconButton(onClick = { viewModel.fetchOwnedSources() }) {
                                Icon(Icons.Default.Refresh, contentDescription = "Reload")
                            }
                        }
                    }

                    if (isLoadingSources) {
                        item {
                            Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                                CircularProgressIndicator()
                            }
                        }
                    } else if (allSources.isEmpty()) {
                        item {
                            Text("No repositories found.", color = MaterialTheme.colorScheme.onBackground)
                        }
                    } else {
                        items(allSources) { source ->
                            val repo = source.githubRepo
                            if (repo != null) {
                                Card(
                                    modifier = Modifier
                                        .padding(bottom = 8.dp)
                                        .fillMaxWidth()
                                        .clickable {
                                            viewModel.connectProject(repo.owner, repo.repo, repo.defaultBranch?.displayName ?: "main")
                                            Toast.makeText(context, "Connected to ${repo.repo}", Toast.LENGTH_SHORT).show()
                                            onBuildTriggered()
                                        },
                                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                                ) {
                                    Column(modifier = Modifier.padding(16.dp)) {
                                        Text(text = "${repo.owner}/${repo.repo}", style = MaterialTheme.typography.titleMedium)
                                        Text(text = "Branch: ${repo.defaultBranch?.displayName ?: "main"}", style = MaterialTheme.typography.bodySmall)
                                    }
                                }
                            }
                        }
                    }
                }
            } else if (currentTabName == "Recent") {
                val recentProjects = settingsViewModel.getProjectList().toList()
                LazyColumn(modifier = Modifier.weight(1f)) {
                    if (recentProjects.isEmpty()) {
                        item { Text("No recent projects.", color = MaterialTheme.colorScheme.onBackground) }
                    } else {
                        items(recentProjects) { name ->
                            Card(
                                modifier = Modifier
                                    .padding(bottom = 8.dp)
                                    .fillMaxWidth()
                                    .clickable {
                                        viewModel.connectProject(settingsViewModel.getGithubUser() ?: "", name, "main")
                                        Toast.makeText(context, "Reconnected to $name", Toast.LENGTH_SHORT).show()
                                        onBuildTriggered()
                                    },
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                            ) {
                                Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                                    Text(text = name, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                                    IconButton(onClick = { settingsViewModel.removeProject(name) }) {
                                        Icon(Icons.Default.Delete, contentDescription = "Forget")
                                    }
                                }
                            }
                        }
                    }
                }
            } else if (currentTabName == "Create") {
                LazyColumn(modifier = Modifier.weight(1f).padding(horizontal = 16.dp)) {
                    item {
                        Text("Create New Repository", style = MaterialTheme.typography.headlineSmall)
                        Spacer(modifier = Modifier.height(16.dp))

                        AzTextBox(value = appName, onValueChange = { appName = it }, hint = "App Name", onSubmit = {})
                        Spacer(modifier = Modifier.height(8.dp))
                        AzTextBox(value = repoDescription, onValueChange = { repoDescription = it }, hint = "Description", onSubmit = {})
                        Spacer(modifier = Modifier.height(8.dp))
                        AzTextBox(value = packageName, onValueChange = { packageName = it }, hint = "Package Name", onSubmit = {})
                        Spacer(modifier = Modifier.height(16.dp))

                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("Private Repo")
                            Spacer(modifier = Modifier.weight(1f))
                            Switch(checked = isPrivateRepo, onCheckedChange = { isPrivateRepo = it })
                        }

                        Spacer(modifier = Modifier.height(24.dp))

                        AzButton(
                            onClick = {
                                viewModel.createGitHubRepository(appName, repoDescription, isPrivateRepo, selectedType, packageName, context) {
                                    Toast.makeText(context, "Repo Created", Toast.LENGTH_SHORT).show()
                                    onBuildTriggered()
                                }
                            },
                            text = "Create & Connect",
                            shape = AzButtonShape.RECTANGLE,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            }
        }
    }
}
