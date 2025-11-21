package com.hereliesaz.ideaz.ui

import android.util.Log
import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
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
import com.hereliesaz.aznavrail.model.AzButtonShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import com.hereliesaz.aznavrail.AzTextBox
import com.hereliesaz.ideaz.models.ProjectType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.TextButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material.icons.filled.Delete
import kotlinx.coroutines.withContext
import kotlinx.coroutines.Dispatchers

private const val TAG = "ProjectScreen"

@OptIn(ExperimentalMaterial3Api::class)
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

    // Central state for project config - Synced with SettingsViewModel
    val currentAppNameState by settingsViewModel.currentAppName.collectAsState()
    val loadingProgress by viewModel.loadingProgress.collectAsState()

    var appName by remember { mutableStateOf("") }
    var githubUser by remember { mutableStateOf("") }
    var branchName by remember { mutableStateOf("") }
    var packageName by remember { mutableStateOf("") }
    var selectedType by remember { mutableStateOf(ProjectType.ANDROID) }

    // Sync local state when current app name changes (e.g. project loaded)
    LaunchedEffect(currentAppNameState) {
        appName = settingsViewModel.getAppName() ?: "IDEazProject"
        githubUser = settingsViewModel.getGithubUser() ?: ""
        branchName = settingsViewModel.getBranchName()
        packageName = settingsViewModel.getTargetPackageName() ?: "com.example.helloworld"
        selectedType = ProjectType.fromString(settingsViewModel.getProjectType())
    }

    // NEW: Collect sources from ViewModel and use local githubUser state for filtering
    val allSources by viewModel.ownedSources.collectAsState()
    val isLoadingSources by viewModel.isLoadingSources.collectAsState()
    val availableSessions by viewModel.availableSessions.collectAsState()

    // Auto-refresh sources when entering the screen
    LaunchedEffect(Unit) {
        viewModel.fetchOwnedSources()
        viewModel.fetchSessions()
    }

    // Auto-populate GitHub User
    LaunchedEffect(allSources) {
        if (githubUser.isBlank() && allSources.isNotEmpty()) {
            val candidate = allSources.first().githubRepo?.owner
            if (!candidate.isNullOrBlank()) {
                githubUser = candidate
                settingsViewModel.setGithubUser(candidate)
            }
        }
    }

    // State for "Clone" tab
    var cloneUrl by remember { mutableStateOf("") }
    val projectList = settingsViewModel.getProjectList()
    val ownedSources = allSources.filter {
        val repo = it.githubRepo
        repo != null && !projectList.contains("${repo.owner}/${repo.repo}")
    }

    // State for "Load" tab
    var projectMetadataList by remember { mutableStateOf<List<ProjectMetadata>>(emptyList()) }
    var projectToDelete by remember { mutableStateOf<String?>(null) }

    // Observe local projects list to trigger refresh
    val localProjects by settingsViewModel.localProjects.collectAsState()

    LaunchedEffect(tabIndex, localProjects) {
        if (tabIndex == 2) {
            withContext(Dispatchers.IO) {
                projectMetadataList = viewModel.getLocalProjectsWithMetadata()
            }
        }
    }

    val screenHeight = LocalConfiguration.current.screenHeightDp.dp

    if (loadingProgress != null) {
        AlertDialog(
            onDismissRequest = {},
            title = { Text("Working...") },
            text = {
                Column {
                    LinearProgressIndicator(
                        progress = { (loadingProgress ?: 0) / 100f },
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text("${loadingProgress}% complete")
                }
            },
            confirmButton = {}
        )
    }

    if (projectToDelete != null) {
        AlertDialog(
            onDismissRequest = { projectToDelete = null },
            title = { Text("Delete Project?") },
            text = { Text("Are you sure you want to delete '$projectToDelete' from the device? The project will still be available on GitHub.") },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.deleteProject(projectToDelete!!)
                    projectToDelete = null
                }) { Text("Yes (Delete)") }
            },
            dismissButton = {
                Row {
                    TextButton(onClick = {
                        viewModel.syncAndDeleteProject(projectToDelete!!)
                        projectToDelete = null
                    }) { Text("Sync") }
                    TextButton(onClick = { projectToDelete = null }) { Text("No") }
                }
            }
        )
    }

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
                        .padding(16.dp)
                ) {
                    Text(
                        "Project Configuration",
                        style = MaterialTheme.typography.headlineSmall,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                    Spacer(modifier = Modifier.height(16.dp))

                    // App Name
                    AzTextBox(
                        value = appName,
                        onValueChange = {
                            appName = it
                            settingsViewModel.setAppName(it)
                        },
                        hint = "App Name",
                        onSubmit = {}
                    )
                    Spacer(modifier = Modifier.height(8.dp))

                    // GitHub User
                    AzTextBox(
                        value = githubUser,
                        onValueChange = {
                            githubUser = it
                            settingsViewModel.setGithubUser(it)
                        },
                        hint = "GitHub User",
                        onSubmit = {}
                    )
                    Spacer(modifier = Modifier.height(8.dp))

                    // Branch
                    AzTextBox(
                        value = branchName,
                        onValueChange = {
                            branchName = it
                            settingsViewModel.saveProjectConfig(appName, githubUser, it)
                        },
                        hint = "Branch (e.g., main)",
                        onSubmit = {}
                    )
                    Spacer(modifier = Modifier.height(8.dp))

                    // Package
                    AzTextBox(
                        value = packageName,
                        onValueChange = {
                            packageName = it
                            settingsViewModel.saveTargetPackageName(it)
                        },
                        hint = "Package Name",
                        onSubmit = {}
                    )
                    Spacer(modifier = Modifier.height(16.dp))

                    // Project Type Dropdown
                    var expanded by remember { mutableStateOf(false) }
                    val projectTypes = ProjectType.values().toList()

                    ExposedDropdownMenuBox(
                        expanded = expanded,
                        onExpandedChange = { expanded = !expanded },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        TextField(
                            modifier = Modifier.menuAnchor().fillMaxWidth(),
                            readOnly = true,
                            value = selectedType.displayName,
                            onValueChange = {},
                            label = { Text("Project Type") },
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                            colors = ExposedDropdownMenuDefaults.textFieldColors()
                        )
                        ExposedDropdownMenu(
                            expanded = expanded,
                            onDismissRequest = { expanded = false }
                        ) {
                            projectTypes.forEach { type ->
                                DropdownMenuItem(
                                    text = { Text(type.displayName) },
                                    onClick = {
                                        selectedType = type
                                        settingsViewModel.setProjectType(type.name)
                                        expanded = false
                                    },
                                    contentPadding = ExposedDropdownMenuDefaults.ItemContentPadding
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(24.dp))

                    Row {
                        AzButton(
                            onClick = {
                                settingsViewModel.saveProjectConfig(appName, githubUser, branchName)
                                settingsViewModel.setProjectType(selectedType.name)
                                settingsViewModel.saveTargetPackageName(packageName)

                                Toast.makeText(context, "Configuration saved.", Toast.LENGTH_SHORT).show()
                                viewModel.initializeProject("")
                                viewModel.fetchSessions()
                            },
                            text = "Save & Initialize",
                            shape = AzButtonShape.RECTANGLE,
                            modifier = Modifier.weight(1f)
                        )
                        Spacer(modifier = Modifier.padding(8.dp))
                        AzButton(
                            onClick = {
                                viewModel.startBuild(context)
                                onBuildTriggered()
                                Toast.makeText(context, "Building...", Toast.LENGTH_SHORT).show()
                            },
                            text = "Build",
                            shape = AzButtonShape.RECTANGLE,
                            modifier = Modifier.weight(1f)
                        )
                    }

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

                                    Spacer(modifier = Modifier.height(8.dp))

                                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                                        if (session.outputs?.any { it.pullRequest != null } == true) {
                                            AzButton(
                                                onClick = { viewModel.trySession(session) },
                                                text = "Try",
                                                shape = AzButtonShape.RECTANGLE,
                                                modifier = Modifier.padding(end = 8.dp)
                                            )
                                            AzButton(
                                                onClick = { viewModel.acceptSession(session) },
                                                text = "Accept",
                                                shape = AzButtonShape.RECTANGLE,
                                                modifier = Modifier.padding(end = 8.dp)
                                            )
                                        }
                                        AzButton(
                                            onClick = { viewModel.deleteSession(session) },
                                            text = "Delete",
                                            shape = AzButtonShape.RECTANGLE
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
                    if (projectMetadataList.isEmpty()) {
                        item {
                            Spacer(modifier = Modifier.height(16.dp))
                            Text("No saved projects found.", color = MaterialTheme.colorScheme.onBackground)
                        }
                    } else {
                        items(projectMetadataList) { project ->
                            Card(
                                modifier = Modifier
                                    .padding(bottom = 8.dp)
                                    .fillMaxWidth()
                                    .clickable {
                                        viewModel.loadProjectAndBuild(context, project.name)
                                        Toast.makeText(context, "Loading and building project...", Toast.LENGTH_SHORT).show()
                                    },
                                colors = CardDefaults.cardColors(
                                    containerColor = MaterialTheme.colorScheme.surface,
                                    contentColor = MaterialTheme.colorScheme.onSurface
                                ),
                                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                            ) {
                                Row(
                                    modifier = Modifier.padding(16.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = project.name,
                                            style = MaterialTheme.typography.titleMedium
                                        )
                                        Text(
                                            text = "Size: ${formatSize(project.sizeBytes)}",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                                        )
                                    }
                                    IconButton(onClick = { projectToDelete = project.name }) {
                                        Icon(Icons.Default.Delete, contentDescription = "Delete Project")
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

fun formatSize(size: Long): String {
    if (size <= 0) return "0 B"
    val units = arrayOf("B", "KB", "MB", "GB", "TB")
    val digitGroups = (Math.log10(size.toDouble()) / Math.log10(1024.0)).toInt()
    return java.text.DecimalFormat("#,##0.#").format(size / Math.pow(1024.0, digitGroups.toDouble())) + " " + units[digitGroups]
}
