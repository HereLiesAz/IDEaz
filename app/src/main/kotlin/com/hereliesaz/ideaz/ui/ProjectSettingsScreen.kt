package com.hereliesaz.ideaz.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.hereliesaz.ideaz.api.Source
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.HazeStyle
import dev.chrisbanes.haze.HazeTint
import dev.chrisbanes.haze.hazeEffect
import dev.chrisbanes.haze.hazeSource
import dev.chrisbanes.haze.rememberHazeState

@Composable
fun ProjectSettingsScreen(
    navController: NavController,
    viewModel: MainViewModel,
    settingsViewModel: SettingsViewModel
) {
    var appNameState by remember { mutableStateOf(settingsViewModel.getAppName() ?: "") }
    var githubUserState by remember { mutableStateOf(settingsViewModel.getGithubUser() ?: "") }
    var sourcesLoading by remember { mutableStateOf(false) }
    val sources by viewModel.ownedSources.collectAsState()
    val localProjects by settingsViewModel.localProjects.collectAsState(initial = emptyList())
    var selectedTabIndex by remember { mutableStateOf(0) }
    val tabs = listOf("Create", "Clone", "Load")

    LaunchedEffect(selectedTabIndex) {
        if (selectedTabIndex == 1 && sources.isEmpty()) {
            sourcesLoading = true
            viewModel.fetchOwnedSources()
            sourcesLoading = false
        }
    }

    val onSave: () -> Unit = {
        val currentBranch = settingsViewModel.getBranchName()
        settingsViewModel.saveProjectConfig(appNameState, githubUserState, currentBranch)
        navController.popBackStack()
    }

    val hazeState = rememberHazeState()

    Box(modifier = Modifier.fillMaxSize()) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .hazeSource(state = hazeState)
        )
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp)
                .hazeEffect(
                    state = hazeState,
                    style = HazeStyle(
                        backgroundColor = MaterialTheme.colorScheme.background.copy(alpha = 0.5f),
                        tints = listOf(HazeTint(Color.Black.copy(alpha = 0.2f)))
                    )
                )
        ) {
            // Header
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = { navController.popBackStack() }) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                }
                Spacer(modifier = Modifier.width(8.dp))
                Text("Project Settings", style = MaterialTheme.typography.headlineSmall)
            }

            Spacer(modifier = Modifier.height(16.dp))
            HorizontalDivider()
            Spacer(modifier = Modifier.height(16.dp))

            TabRow(selectedTabIndex = selectedTabIndex) {
                tabs.forEachIndexed { index, title ->
                    Tab(
                        selected = selectedTabIndex == index,
                        onClick = { selectedTabIndex = index },
                        text = { Text(title) }
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            when (selectedTabIndex) {
                0 -> CreateTab(
                    appName = appNameState,
                    githubUser = githubUserState,
                    onAppNameChange = { appNameState = it },
                    onGithubUserChange = { githubUserState = it }
                )
                1 -> CloneTab(
                    sources = sources,
                    loading = sourcesLoading,
                    onClone = { source ->
                        val url = source.githubRepo?.let { "https://github.com/${it.owner}/${it.repo}" } ?: ""
                        viewModel.cloneProject(url, source.name)
                        navController.popBackStack()
                    }
                )
                2 -> LoadTab(
                    projects = localProjects,
                    onLoad = { projectName ->
                        viewModel.loadProject(projectName)
                        navController.popBackStack()
                    }
                )
            }

            Spacer(modifier = Modifier.weight(1f))
            Button(
                onClick = onSave,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Save and Go Back")
            }
        }
    }
}

@Composable
fun CreateTab(
    appName: String,
    githubUser: String,
    onAppNameChange: (String) -> Unit,
    onGithubUserChange: (String) -> Unit
) {
    Column {
        Text("Project Configuration", style = MaterialTheme.typography.titleMedium)
        Spacer(modifier = Modifier.height(8.dp))
        TextField(
            value = appName,
            onValueChange = onAppNameChange,
            label = { Text("App Name") },
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(modifier = Modifier.height(8.dp))
        TextField(
            value = githubUser,
            onValueChange = onGithubUserChange,
            label = { Text("GitHub User") },
            modifier = Modifier.fillMaxWidth()
        )
    }
}

@Composable
fun CloneTab(
    sources: List<Source>,
    loading: Boolean,
    onClone: (Source) -> Unit
) {
    if (loading) {
        Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
    } else if (sources.isEmpty()) {
        Text("No active sessions found or failed to load.")
    } else {
        LazyColumn {
            items(sources) { source ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onClone(source) }
                        .padding(vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = source.name,
                        style = MaterialTheme.typography.bodyLarge,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }
    }
}

@Composable
fun LoadTab(
    projects: List<String>,
    onLoad: (String) -> Unit
) {
    if (projects.isEmpty()) {
        Text("No local projects found.")
    } else {
        LazyColumn {
            items(projects) { project ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onLoad(project) }
                        .padding(vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = project,
                        style = MaterialTheme.typography.bodyLarge,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }
    }
}

@Composable
fun SessionItem(source: Source) {
    Column(modifier = Modifier.padding(vertical = 8.dp)) {
        Text(
            text = source.name,
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = FontWeight.Medium
        )
    }
}

@Composable
fun SelectableSessionItem(
    session: String,
    selectedSession: String,
    onSessionSelected: (String) -> Unit
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onSessionSelected(session) }
            .padding(vertical = 4.dp)
    ) {
        RadioButton(
            selected = session == selectedSession,
            onClick = { onSessionSelected(session) }
        )
        Spacer(modifier = Modifier.width(8.dp))
        Column {
            Text(
                text = "Session: $session",
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium
            )
            Text(
                text = "ID: 12345-67890", // Example subtext
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}