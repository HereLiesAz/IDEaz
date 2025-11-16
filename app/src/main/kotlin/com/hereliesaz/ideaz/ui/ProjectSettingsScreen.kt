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
// --- FIX: Use AutoMirrored version ---
import androidx.compose.material.icons.automirrored.filled.ArrowBack
// --- END FIX ---
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
// --- FIX: Use HorizontalDivider ---
import androidx.compose.material3.HorizontalDivider
// --- END FIX ---
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.hereliesaz.ideaz.api.Source
import com.hereliesaz.ideaz.ui.SettingsViewModel

@Composable
fun ProjectSettingsScreen(
    navController: NavController,
    viewModel: MainViewModel,
    settingsViewModel: SettingsViewModel
) {
    val appName by settingsViewModel.appName.collectAsState()
    val githubUser by settingsViewModel.githubUser.collectAsState()
    val githubToken by settingsViewModel.githubToken.collectAsState()
    val geminiApiKey by settingsViewModel.geminiApiKey.collectAsState()

    var appNameState by remember { mutableStateOf(appName) }
    var githubUserState by remember { mutableStateOf(githubUser) }
    var githubTokenState by remember { mutableStateOf(githubToken) }
    var geminiApiKeyState by remember { mutableStateOf(geminiApiKey) }

    var sourcesLoading by remember { mutableStateOf(false) }
    val sources by viewModel.ownedSources.collectAsState()

    // State for "Clone" tab
    var cloneUrl by remember { mutableStateOf("") }
    val projectList = settingsViewModel.getProjectList()

    // --- FIX: Observe sources from ViewModel ---
    val ownedSources by viewModel.ownedSources.collectAsState()
    // --- END FIX ---
    LaunchedEffect(Unit) {
        sourcesLoading = true
        viewModel.fetchOwnedSources()
        sourcesLoading = false
    }

    LaunchedEffect(appName, githubUser, githubToken, geminiApiKey) {
        appNameState = appName
        githubUserState = githubUser
        githubTokenState = githubToken
        geminiApiKeyState = geminiApiKey
    }

    val onSave = {
        val currentBranch = settingsViewModel.getBranchName()
        settingsViewModel.saveProjectConfig(appNameState, githubUserState, currentBranch)
        settingsViewModel.saveApiKey(githubTokenState)
        settingsViewModel.saveGoogleApiKey(geminiApiKeyState)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        // Header
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { navController.popBackStack() }) {
                // --- FIX: Use AutoMirrored Icon ---
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                // --- END FIX ---
            }
            Spacer(modifier = Modifier.width(8.dp))
            Text("Project Settings", style = MaterialTheme.typography.headlineSmall)
        }

        Spacer(modifier = Modifier.height(16.dp))
        // --- FIX: Use HorizontalDivider ---
        HorizontalDivider()
        // --- END FIX ---
        Spacer(modifier = Modifier.height(16.dp))

        // Settings Fields
        LazyColumn(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item {
                Text("Project Configuration", style = MaterialTheme.typography.titleMedium)
                Spacer(modifier = Modifier.height(8.dp))
                TextField(
                    value = appNameState,
                    onValueChange = { appNameState = it },
                    label = { Text("App Name") },
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(8.dp))
                TextField(
                    value = githubUserState,
                    onValueChange = { githubUserState = it },
                    label = { Text("GitHub User") },
                    modifier = Modifier.fillMaxWidth()
                )
            }

            item {
                Text("API Keys", style = MaterialTheme.typography.titleMedium)
                Spacer(modifier = Modifier.height(8.dp))
                TextField(
                    value = githubTokenState,
                    onValueChange = { githubTokenState = it },
                    label = { Text("GitHub Token (Jules)") },
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text("ghp_...") }
                )
                Spacer(modifier = Modifier.height(8.dp))
                TextField(
                    value = geminiApiKeyState,
                    onValueChange = { geminiApiKeyState = it },
                    label = { Text("Gemini API Key") },
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text("AIza...") }
                )
            }

            item {
                Text("Active Jules Sessions", style = MaterialTheme.typography.titleMedium)
                Spacer(modifier = Modifier.height(8.dp))

                if (sourcesLoading) {
                    Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                } else if (sources.isEmpty()) {
                    Text("No active sessions found or failed to load.")
                } else {
                    Column {
                        sources.forEach { source ->
                            SessionItem(source = source)
                        }
                    }
                }
            }

            item {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Warning,
                        contentDescription = "Warning",
                        modifier = Modifier.size(48.dp),
                        tint = MaterialTheme.colorScheme.error
                    )
                    Text(
                        "Clear Build Caches",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.error
                    )
                    Text(
                        "This will delete all local build artifacts, " +
                                "dependency caches, and the local-repo. " +
                                "This action is irreversible and can help resolve " +
                                "persistent build issues.",
                        style = MaterialTheme.typography.bodySmall
                    )
                    Button(
                        onClick = {
                            viewModel.clearBuildCaches(navController.context)
                        }
                    ) {
                        Text("Clear Caches")
                    }
                }
            }
        }

        // Save Button
        Spacer(modifier = Modifier.height(16.dp))
        Button(
            onClick = {
                onSave()
                navController.popBackStack()
            },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Save and Go Back")
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