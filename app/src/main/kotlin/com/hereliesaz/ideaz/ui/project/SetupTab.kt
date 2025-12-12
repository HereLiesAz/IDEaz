package com.hereliesaz.ideaz.ui.project

import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import com.hereliesaz.aznavrail.AzButton
import com.hereliesaz.aznavrail.AzTextBox
import com.hereliesaz.aznavrail.model.AzButtonShape
import com.hereliesaz.ideaz.models.ProjectType
import com.hereliesaz.ideaz.ui.MainViewModel
import com.hereliesaz.ideaz.ui.SettingsViewModel

private const val DOCS_PROMPT = "Examine all source code and documentation in this repository. Once you understand everything there is to know about this project, I want you to create an AGENTS.md file if there isn't one, and add a /docs/ folder in the root of this repository. Then I want you to create these files in the docs folder: AGENT_GUIDE.md, TODO.md, UI_UX.md, auth.md, conduct.md, data_layer.md, fauxpas.md, file_descriptions.md, misc.md, performance.md, screens.md, task_flow.md, testing.md, and workflow.md. Based on your studies and understanding of the project, I want you to populate all of those files with every little detail possible. And then, I want you to add to the AGENTS file an index of what is in the docs folder. Be explicit about the fact that the files in that folder are an extention of the AGENTS.md file, and every bit as important. After that, I want you to add exhaustive documentation across the code base. Lastly, for good  measure, make sure the beginning of the AGENTS.md specifies that the AI absolutely MUST get a complete code review AND a passing build with tests, and MUST keep all documents and documentation up to date, before committing--WITHOUT exception. (Please note that if you've received this command and any part of these instructions already exists, do your best to add robustness and comprehensive reach to what already exists.)"

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
    val loadingProgress by viewModel.loadingProgress.collectAsState()

    // NEW STATE: Tracks if the user has dismissed the mandatory warning about manual secrets.
    var showManualSecretWarning by remember { mutableStateOf(true) }

    // Derived state for button loading
    val isBusy = loadingProgress != null

    var appName by remember { mutableStateOf("") }
    var githubUser by remember { mutableStateOf("") }
    var branchName by remember { mutableStateOf("main") }
    var packageName by remember { mutableStateOf("") }
    var selectedType by remember { mutableStateOf(ProjectType.ANDROID) }

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
            // If viewing a project, we assume secrets were handled.
            showManualSecretWarning = false
        } else {
            if (appName == "IDEazProject") appName = ""
            // Show warning only in creation mode
            showManualSecretWarning = true
        }
    }

    // Derived state for button enablement
    val isReadyToCreate = initialPrompt.isNotBlank() && appName.isNotBlank() && !showManualSecretWarning

    LazyColumn(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        item {
            Text("Project Actions", style = MaterialTheme.typography.headlineSmall, color = MaterialTheme.colorScheme.onBackground)
            Spacer(Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                AzButton(
                    modifier = Modifier.weight(1f),
                    onClick = { onCreateModeChanged(true) },
                    text = "Create",
                    shape = AzButtonShape.RECTANGLE,
                    enabled = !isBusy && !isCreateMode
                )
                AzButton(
                    modifier = Modifier.weight(1f),
                    onClick = { onNavigateToTab("Clone") },
                    text = "Clone",
                    shape = AzButtonShape.RECTANGLE,
                    enabled = !isBusy
                )
                AzButton(
                    modifier = Modifier.weight(1f),
                    onClick = { onNavigateToTab("Load") },
                    text = "Load",
                    shape = AzButtonShape.RECTANGLE,
                    enabled = !isBusy
                )
            }
            Spacer(modifier = Modifier.height(24.dp))

            val headerText = if (isCreateMode) "Create New Project" else "Project Configuration"
            Text(headerText, style = MaterialTheme.typography.headlineSmall, color = MaterialTheme.colorScheme.onBackground)
            Spacer(Modifier.height(16.dp))

            AzTextBox(
                value = appName,
                onValueChange = { appName = it },
                hint = "App Name",
                onSubmit = {},
                enabled = isCreateMode,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next)
            )
            Spacer(Modifier.height(8.dp))

            AzTextBox(
                value = githubUser,
                onValueChange = { githubUser = it },
                hint = "GitHub User",
                onSubmit = {},
                enabled = isCreateMode,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next)
            )
            Spacer(Modifier.height(8.dp))

            AzTextBox(
                value = branchName,
                onValueChange = { branchName = it },
                hint = "Branch",
                onSubmit = {},
                enabled = isCreateMode,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next)
            )
            Spacer(Modifier.height(8.dp))

            AzTextBox(
                value = packageName,
                onValueChange = { packageName = it },
                hint = "Package Name",
                onSubmit = {},
                enabled = isCreateMode,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next)
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

                // Prompt Text Box
                val isPromptError = initialPrompt.isBlank()
                AzTextBox(
                    value = initialPrompt,
                    onValueChange = { initialPrompt = it },
                hint = "Initial Prompt (Mandatory)",
                    onSubmit = {},
                    modifier = Modifier.fillMaxWidth(),
                    isError = isPromptError,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done)
                )
                if (isPromptError) {
                    Text(
                        text = "* Required to initialize the AI agent.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.padding(start = 4.dp, top = 4.dp)
                    )
                }

                Spacer(Modifier.height(24.dp))

                // NEW MANUAL SECRET WARNING
                if (showManualSecretWarning) {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
                        modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp)
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.Warning, contentDescription = "Warning", tint = MaterialTheme.colorScheme.onErrorContainer)
                                Spacer(Modifier.width(8.dp))
                                Text("CRITICAL MANUAL STEP REQUIRED", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onErrorContainer)
                            }
                            Spacer(Modifier.height(8.dp))
                            Text(
                                "Automated secret encryption is disabled for stability. The remote build will fail without the following secrets added manually to your GitHub repo settings:",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onErrorContainer
                            )
                            Text(
                                "GEMINI_API_KEY, GOOGLE_API_KEY, JULES_PROJECT_ID",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onErrorContainer,
                                modifier = Modifier.padding(top = 4.dp, bottom = 8.dp)
                            )
                            AzButton(
                                onClick = { showManualSecretWarning = false },
                                text = "I understand (Disable Warning)",
                                shape = AzButtonShape.RECTANGLE,
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }
                }
            }

            Spacer(Modifier.height(24.dp))

            if (isCreateMode) {
                AzButton(
                    onClick = {
                        if (onCheckRequirements()) {
                            viewModel.createGitHubRepository(
                                appName, repoDescription, false, selectedType, packageName, context
                            ) {
                                viewModel.uploadProjectSecrets(githubUser, appName) // This now just logs instructions
                                onCreateModeChanged(false)
                                if (initialPrompt.isNotBlank()) {
                                    viewModel.sendPrompt(initialPrompt)
                                }
                                onBuildTriggered()
                            }
                        }
                    },
                    text = if (showManualSecretWarning) "ACTION BLOCKED" else "Create & Save",
                    shape = AzButtonShape.RECTANGLE,
                    modifier = Modifier.fillMaxWidth(),
                    enabled = isReadyToCreate, // Disabled if warning is visible
                    isLoading = isBusy
                )
            } else {
                AzButton(
                    onClick = {
                        if (onCheckRequirements()) {
                            viewModel.saveAndInitialize(
                                appName, githubUser, branchName, packageName, selectedType, context, null
                            )
                            onBuildTriggered()
                        }
                    },
                    text = "Save & Initialize",
                    shape = AzButtonShape.RECTANGLE,
                    modifier = Modifier.fillMaxWidth(),
                    isLoading = isBusy
                )

                Spacer(Modifier.height(8.dp))

                AzButton(
                    onClick = {
                        if (onCheckRequirements()) {
                            // Ensure init first
                            viewModel.saveAndInitialize(
                                appName, githubUser, branchName, packageName, selectedType, context, null
                            )
                            viewModel.sendPrompt(DOCS_PROMPT)
                        }
                    },
                    text = "Add Docs",
                    shape = AzButtonShape.RECTANGLE,
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !isBusy
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
                            Toast.makeText(context, "Resumed: ${session.id}", Toast.LENGTH_SHORT).show()
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
