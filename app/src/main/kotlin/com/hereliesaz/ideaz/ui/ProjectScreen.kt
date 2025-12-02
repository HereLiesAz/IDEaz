package com.hereliesaz.ideaz.ui

import android.util.Log
import android.widget.Toast
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import android.os.Environment
import androidx.compose.foundation.background
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
import androidx.compose.material3.Switch
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import kotlinx.coroutines.withContext
import kotlinx.coroutines.Dispatchers

private const val TAG = "ProjectScreen"

private const val DOCS_PROMPT = "Examine all source code and documentation in this repository. Once you understand everything there is to know about this project, I want you to create an AGENTS.md file if there isn't one, and add a /docs/ folder in the root of this repository. Then I want you to create these files in the docs folder: AGENT_GUIDE.md, TODO.md, UI_UX.md, auth.md, conduct.md, data_layer.md, fauxpas.md, file_descriptions.md, misc.md, performance.md, screens.md, task_flow.md, testing.md, and workflow.md. Based on your studies and understanding of the project, I want you to populate all of those files with every little detail possible. And then, I want you to add to the AGENTS file an index of what is in the docs folder. Be explicit about the fact that the files in that folder are an extention of the AGENTS.md file, and every bit as important. After that, I want you to add exhaustive documentation across the code base. Lastly, for good  measure, make sure the beginning of the AGENTS.md specifies that the AI absolutely MUST get a complete code review AND a passing build with tests, and MUST keep all documents and documentation up to date, before committing--WITHOUT exception. (Please note that if you've received this command and any part of these instructions already exists, do your best to add robustness and comprehensive reach to what already exists.)"

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
    var isCreateTabEnabled by remember { mutableStateOf(false) }

    var isNewProjectFlow by remember { mutableStateOf(false) }

    val tabs = remember(hasToken, isCreateTabEnabled) {
        if (hasToken && isCreateTabEnabled) {
            listOf("Create", "Setup", "Clone", "Load")
        } else {
            listOf("Setup", "Clone", "Load")
        }
    }

    var tabIndex by remember { mutableStateOf(0) }

    val currentAppNameState by settingsViewModel.currentAppName.collectAsState()
    val currentPackageName by settingsViewModel.targetPackageName.collectAsState()
    val loadingProgress by viewModel.loadingProgress.collectAsState()

    var appName by remember { mutableStateOf("") }
    var githubUser by remember { mutableStateOf("") }
    var branchName by remember { mutableStateOf("main") }
    var packageName by remember { mutableStateOf("com.example.app") }
    var selectedType by remember { mutableStateOf(ProjectType.ANDROID) }
    var initialPrompt by remember { mutableStateOf("") }

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
    val availableSessions by viewModel.availableSessions.collectAsState()

    LaunchedEffect(Unit) {
        viewModel.fetchOwnedSources()
        viewModel.fetchSessions()
    }

    LaunchedEffect(allSources) {
        if (githubUser.isBlank() && allSources.isNotEmpty()) {
            val candidate = allSources.first().githubRepo?.owner
            if (!candidate.isNullOrBlank()) {
                githubUser = candidate
                settingsViewModel.setGithubUser(candidate)
            }
        }
    }

    var cloneUrl by remember { mutableStateOf("") }
    val projectList = settingsViewModel.getProjectList()
    val ownedSources = allSources.filter {
        val repo = it.githubRepo
        repo != null && !projectList.contains("${repo.owner}/${repo.repo}")
    }

    var projectMetadataList by remember { mutableStateOf<List<ProjectMetadata>>(emptyList()) }
    var projectToDelete by remember { mutableStateOf<String?>(null) }

    val localProjects by settingsViewModel.localProjects.collectAsState()

    val currentTabName = tabs.getOrElse(tabIndex) { "Setup" }
    val isCreateTab = currentTabName == "Create"
    val isSetupTab = currentTabName == "Setup"
    val isCloneTab = currentTabName == "Clone"
    val isLoadTab = currentTabName == "Load"

    LaunchedEffect(tabIndex, localProjects) {
        if (isLoadTab) {
            viewModel.scanLocalProjects()
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

    Column(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        Spacer(modifier = Modifier.height(32.dp))

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(all = 8.dp)
                .weight(1f) // Explicit weight ensures children have finite height constraint
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
                    Tab(text = { Text(title, color = MaterialTheme.colorScheme.onBackground) },
                        selected = tabIndex == index,
                        onClick = { tabIndex = index }
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            if (isCreateTab) {
                LazyColumn(
                    modifier = Modifier
                        .weight(1f)
                        .padding(horizontal = 16.dp)
                ) {
                    item {
                        Text("Create New Repository", style = MaterialTheme.typography.headlineSmall)
                        Spacer(modifier = Modifier.height(16.dp))

                        AzTextBox(
                            value = appName,
                            onValueChange = { appName = it },
                            hint = "Repository Name (App Name)",
                            onSubmit = {}
                        )
                        Spacer(modifier = Modifier.height(8.dp))

                        AzTextBox(
                            value = repoDescription,
                            onValueChange = { repoDescription = it },
                            hint = "Description",
                            onSubmit = {}
                        )
                        Spacer(modifier = Modifier.height(8.dp))

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
                                            expanded = false
                                        },
                                        contentPadding = ExposedDropdownMenuDefaults.ItemContentPadding
                                    )
                                }
                            }
                        }
                        Spacer(modifier = Modifier.height(8.dp))

                        AzTextBox(
                            value = packageName,
                            onValueChange = { packageName = it },
                            hint = "Package Name",
                            onSubmit = {}
                        )

                        Spacer(modifier = Modifier.height(16.dp))

                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("Private Repository")
                            Spacer(modifier = Modifier.weight(1f))
                            Switch(checked = isPrivateRepo, onCheckedChange = { isPrivateRepo = it })
                        }

                        Spacer(modifier = Modifier.height(24.dp))

                        AzButton(
                            onClick = {
                                viewModel.createGitHubRepository(
                                    appName,
                                    repoDescription,
                                    isPrivateRepo,
                                    selectedType,
                                    packageName,
                                    context
                                ) {
                                    isCreateTabEnabled = false
                                    isNewProjectFlow = true
                                    tabIndex = 0
                                }
                                Toast.makeText(context, "Creating repository...", Toast.LENGTH_SHORT).show()
                            },
                            text = "Create & Continue",
                            shape = AzButtonShape.RECTANGLE,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            } else if (isSetupTab) {
                LazyColumn(
                    modifier = Modifier
                        .weight(1f)
                        .padding(horizontal = 16.dp)
                ) {
                    item {
                        Text(
                            "Project Repository",
                            style = MaterialTheme.typography.headlineSmall,
                            color = MaterialTheme.colorScheme.onBackground
                        )
                        Spacer(modifier = Modifier.height(8.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            AzButton(
                                modifier = Modifier.weight(1f),
                                onClick = {
                                    if (hasToken) {
                                        isCreateTabEnabled = true
                                        tabIndex = 0
                                    } else {
                                        Toast.makeText(context, "Please add GitHub Token in Settings", Toast.LENGTH_SHORT).show()
                                    }
                                },
                                text = "Create",
                                shape = AzButtonShape.RECTANGLE
                            )
                            AzButton(
                                modifier = Modifier.weight(1f),
                                onClick = {
                                    isNewProjectFlow = false
                                    val cloneIndex = tabs.indexOf("Clone")
                                    if (cloneIndex != -1) tabIndex = cloneIndex
                                },
                                text = "Clone",
                                shape = AzButtonShape.RECTANGLE
                            )
                            AzButton(
                                modifier = Modifier.weight(1f),
                                onClick = {
                                    isNewProjectFlow = false
                                    val loadIndex = tabs.indexOf("Load")
                                    if (loadIndex != -1) tabIndex = loadIndex
                                },
                                text = "Load",
                                shape = AzButtonShape.RECTANGLE
                            )
                        }
                        Spacer(modifier = Modifier.height(24.dp))

                        Text(
                            "Project Configuration",
                            style = MaterialTheme.typography.headlineSmall,
                            color = MaterialTheme.colorScheme.onBackground
                        )
                        Spacer(modifier = Modifier.height(16.dp))

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

                        if (isNewProjectFlow) {
                            AzTextBox(
                                value = initialPrompt,
                                onValueChange = { initialPrompt = it },
                                hint = "Initial Prompt / Instruction (Optional)",
                                onSubmit = {},
                                modifier = Modifier.fillMaxWidth()
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                        }

                        AzButton(
                            onClick = {
                                viewModel.saveAndInitialize(
                                    appName,
                                    githubUser,
                                    branchName,
                                    packageName,
                                    selectedType,
                                    context,
                                    if (isNewProjectFlow) initialPrompt else null
                                )
                                isNewProjectFlow = false
                                onBuildTriggered()
                                Toast.makeText(context, "Saving & Initializing...", Toast.LENGTH_SHORT).show()
                            },
                            text = "Save & Initialize",
                            shape = AzButtonShape.RECTANGLE,
                            modifier = Modifier.fillMaxWidth()
                        )

                        Spacer(modifier = Modifier.height(24.dp))
                    }

                    if (availableSessions.isNotEmpty()) {
                        item {
                            Text(
                                "Active Sessions",
                                style = MaterialTheme.typography.headlineSmall,
                                color = MaterialTheme.colorScheme.onBackground
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                        }

                        items(availableSessions) { session ->
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

                    item {
                        Spacer(modifier = Modifier.height(24.dp))
                        AzButton(
                            onClick = {
                                viewModel.sendPrompt(DOCS_PROMPT)
                                Toast.makeText(context, "Requesting docs...", Toast.LENGTH_SHORT).show()
                            },
                            text = "Add Docs",
                            shape = AzButtonShape.RECTANGLE,
                            modifier = Modifier.fillMaxWidth()
                        )
                        Spacer(modifier = Modifier.height(24.dp))
                    }
                }
            } else if (isCloneTab) {
                // --- CLONE TAB ---
                LazyColumn(
                    modifier = Modifier.weight(1f)
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

                                            val setupIndex = tabs.indexOf("Setup")
                                            if (setupIndex != -1) tabIndex = setupIndex
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
            } else if (isLoadTab) {
                // --- LOAD TAB ---
                val openProjectLauncher = rememberLauncherForActivityResult(
                    contract = ActivityResultContracts.OpenDocumentTree()
                ) { uri ->
                    if (uri != null) {
                        viewModel.registerExternalProject(uri)
                        Toast.makeText(context, "Registering project...", Toast.LENGTH_SHORT).show()
                    }
                }

                val isExternalStorageManager = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
                    Environment.isExternalStorageManager()
                } else {
                    true
                }

                LazyColumn(modifier = Modifier.weight(1f)) {
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
                                        viewModel.loadProject(project.name) {
                                            // After loading, go to Setup tab
                                            val setupIndex = tabs.indexOf("Setup")
                                            if (setupIndex != -1) tabIndex = setupIndex
                                        }
                                        Toast.makeText(context, "Project loaded.", Toast.LENGTH_SHORT).show()
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

                    item {
                        Spacer(modifier = Modifier.height(16.dp))
                        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R && !isExternalStorageManager) {
                            AzButton(
                                onClick = {
                                    try {
                                        val intent = Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION)
                                        intent.addCategory("android.intent.category.DEFAULT")
                                        intent.data = Uri.parse("package:${context.packageName}")
                                        context.startActivity(intent)
                                    } catch (e: Exception) {
                                        val intent = Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION)
                                        context.startActivity(intent)
                                    }
                                },
                                text = "Grant Storage Permission",
                                shape = AzButtonShape.RECTANGLE,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(bottom = 16.dp)
                            )
                        } else {
                            AzButton(
                                onClick = { openProjectLauncher.launch(null) },
                                text = "Add External Project",
                                shape = AzButtonShape.RECTANGLE,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(bottom = 16.dp)
                            )
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
