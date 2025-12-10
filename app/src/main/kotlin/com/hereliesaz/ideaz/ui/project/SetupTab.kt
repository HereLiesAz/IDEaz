package com.hereliesaz.ideaz.ui.project

import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.hereliesaz.aznavrail.AzButton
import com.hereliesaz.aznavrail.AzTextBox
import com.hereliesaz.aznavrail.model.AzButtonShape
import com.hereliesaz.ideaz.models.ProjectType
import com.hereliesaz.ideaz.ui.MainViewModel
import com.hereliesaz.ideaz.ui.SettingsViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProjectSetupTab(
    viewModel: MainViewModel,
    settingsViewModel: SettingsViewModel,
    context: Context,
    onBuildTriggered: () -> Unit,
    onCheckRequirements: () -> Boolean,
    isCreateMode: Boolean,
    onCreateModeChanged: (Boolean) -> Unit,
    onNavigateToTab: (String) -> Unit
) {
    val currentAppNameState by settingsViewModel.currentAppName.collectAsState()
    val sessions by viewModel.sessions.collectAsState()

    var appName by remember { mutableStateOf("") }
    var githubUser by remember { mutableStateOf("") }
    var branchName by remember { mutableStateOf("main") }
    var packageName by remember { mutableStateOf("") }
    var selectedType by remember { mutableStateOf(ProjectType.ANDROID) }

    // Create Mode specific fields
    var repoDescription by remember { mutableStateOf("Created with IDEaz") }
    var initialPrompt by remember { mutableStateOf("") }

    LaunchedEffect(currentAppNameState, isCreateMode) {
        if (!isCreateMode) {
            appName = settingsViewModel.getAppName() ?: "IDEazProject"
            githubUser = settingsViewModel.getGithubUser() ?: ""
            branchName = settingsViewModel.getBranchName()
            packageName = settingsViewModel.getTargetPackageName() ?: "com.example"
            selectedType = ProjectType.fromString(settingsViewModel.getProjectType())
            if (appName.isNotBlank()) viewModel.fetchSessionsForRepo(appName)
        } else {
            if (appName == "IDEazProject") appName = ""
        }
    }

    LazyColumn(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        item {
            Text("Project Actions", style = MaterialTheme.typography.headlineSmall, color = MaterialTheme.colorScheme.onBackground)
            Spacer(Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // If user clicks Create here, we enter Create Mode on this same tab
                AzButton(
                    modifier = Modifier.weight(1f),
                    onClick = { onCreateModeChanged(true) },
                    text = "Create",
                    shape = AzButtonShape.RECTANGLE
                )
                AzButton(
                    modifier = Modifier.weight(1f),
                    onClick = { onNavigateToTab("Clone") },
                    text = "Clone",
                    shape = AzButtonShape.RECTANGLE
                )
                AzButton(
                    modifier = Modifier.weight(1f),
                    onClick = { onNavigateToTab("Load") },
                    text = "Load",
                    shape = AzButtonShape.RECTANGLE
                )
            }
            Spacer(modifier = Modifier.height(24.dp))

            val headerText = if (isCreateMode) "Create New Project" else "Project Configuration"
            Text(headerText, style = MaterialTheme.typography.headlineSmall, color = MaterialTheme.colorScheme.onBackground)
            Spacer(Modifier.height(16.dp))

            // --- WORKAROUND: Logical Read-Only ---
            // Since AzTextBox lacks 'enabled' param, we guard the onValueChange.

            AzTextBox(
                value = appName,
                onValueChange = { if (isCreateMode) appName = it },
                hint = "App Name",
                onSubmit = {}
            )
            Spacer(Modifier.height(8.dp))

            AzTextBox(
                value = githubUser,
                onValueChange = { if (isCreateMode) githubUser = it },
                hint = "GitHub User",
                onSubmit = {}
            )
            Spacer(Modifier.height(8.dp))

            AzTextBox(
                value = branchName,
                onValueChange = { if (isCreateMode) branchName = it },
                hint = "Branch",
                onSubmit = {}
            )
            Spacer(Modifier.height(8.dp))

            AzTextBox(
                value = packageName,
                onValueChange = { if (isCreateMode) packageName = it },
                hint = "Package Name",
                onSubmit = {}
            )

            Spacer(Modifier.height(8.dp))

            // Project Type Dropdown
            var expanded by remember { mutableStateOf(false) }
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
                    ProjectType.values().forEach { type ->
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

            // --- CREATE MODE SPECIFIC FIELDS ---
            if (isCreateMode) {
                Spacer(Modifier.height(8.dp))
                AzTextBox(
                    value = repoDescription,
                    onValueChange = { repoDescription = it },
                    hint = "Description",
                    onSubmit = {}
                )

                Spacer(Modifier.height(24.dp))
                // MANDATORY INITIAL PROMPT
                AzTextBox(
                    value = initialPrompt,
                    onValueChange = { initialPrompt = it },
                    hint = "Initial Prompt (Mandatory)",
                    onSubmit = {},
                    modifier = Modifier.fillMaxWidth()
                )
                if (initialPrompt.isBlank()) {
                    Text(
                        text = "* Initial Prompt is required to create a project.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.padding(start = 4.dp, top = 4.dp)
                    )
                }
            }

            Spacer(Modifier.height(24.dp))

            // --- ACTION BUTTON ---
            if (isCreateMode) {
                // CREATE & SAVE
                AzButton(
                    onClick = {
                        // Workaround: Logical disable check since button doesn't support 'enabled' prop yet
                        if (initialPrompt.isNotBlank() && appName.isNotBlank()) {
                            if (onCheckRequirements()) {
                                viewModel.createGitHubRepository(
                                    appName, repoDescription, false, selectedType, packageName, context
                                ) {
                                    onCreateModeChanged(false)
                                    if (initialPrompt.isNotBlank()) {
                                        viewModel.sendPrompt(initialPrompt)
                                    }
                                    onBuildTriggered()
                                }
                                Toast.makeText(context, "Creating Repository...", Toast.LENGTH_SHORT).show()
                            }
                        } else {
                            Toast.makeText(context, "Please fill in App Name and Initial Prompt", Toast.LENGTH_SHORT).show()
                        }
                    },
                    text = "Create & Save",
                    shape = AzButtonShape.RECTANGLE,
                    modifier = Modifier.fillMaxWidth()
                )
            } else {
                // SAVE & INITIALIZE
                AzButton(
                    onClick = {
                        if (onCheckRequirements()) {
                            viewModel.saveAndInitialize(
                                appName, githubUser, branchName, packageName, selectedType, context, null
                            )
                            onBuildTriggered()
                            Toast.makeText(context, "Initializing...", Toast.LENGTH_SHORT).show()
                        }
                    },
                    text = "Save & Initialize",
                    shape = AzButtonShape.RECTANGLE,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }

        if (!isCreateMode && sessions.isNotEmpty()) {
            item {
                Spacer(Modifier.height(24.dp))
                Text("Available Sessions", style = MaterialTheme.typography.headlineSmall)
                Spacer(Modifier.height(8.dp))
            }
            items(sessions) { session ->
                Card(
                    modifier = Modifier
                        .padding(bottom = 8.dp)
                        .fillMaxWidth()
                        .clickable {
                            viewModel.resumeSession(session.id)
                            Toast.makeText(context, "Resuming session ${session.id}", Toast.LENGTH_SHORT).show()
                        },
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surface,
                        contentColor = MaterialTheme.colorScheme.onSurface
                    ),
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text("Session: ${session.id}", style = MaterialTheme.typography.bodyLarge)
                        Text("Prompt: ${session.prompt.take(50)}...", style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        }
    }
}