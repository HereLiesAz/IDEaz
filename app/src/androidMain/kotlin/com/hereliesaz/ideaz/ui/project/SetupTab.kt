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
import com.hereliesaz.ideaz.ui.MainViewModel
import com.hereliesaz.ideaz.ui.SettingsViewModel
import com.hereliesaz.ideaz.utils.TemplateManager

private const val DOCS_PROMPT = "Examine all source code and documentation in this repository. Once you understand everything there is to know about this project, I want you to create an AGENTS.md file if there isn't one, and add a /docs/ folder in the root of this repository. Then I want you to create these files in the docs folder: AGENT_GUIDE.md, TODO.md, UI_UX.md, auth.md, conduct.md, data_layer.md, fauxpas.md, file_descriptions.md, misc.md, performance.md, screens.md, task_flow.md, testing.md, and workflow.md. Based on your studies and understanding of the project, I want you to populate all of those files with every little detail possible. And then, I want you to add to the AGENTS file an index of what is in the docs folder. Be explicit about the fact that the files in that folder are an extention of the AGENTS.md file, and every bit as important. After that, I want you to add exhaustive documentation across the code base. Lastly, for good  measure, make sure the beginning of the AGENTS.md specifies that the AI absolutely MUST get a complete code review AND a passing build with tests, and MUST keep all documents and documentation up to date, before committing--WITHOUT exception. (Please note that if you've received this command and any part of these instructions already exists, do your best to add robustness and comprehensive reach to what already exists.)"

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProjectSetupTab(
    viewModel: MainViewModel,
    settingsViewModel: SettingsViewModel,
    context: Context,
    onCheckRequirements: () -> Boolean,
    isCreateMode: Boolean,
    onCreateModeChanged: (Boolean) -> Unit,
    onNavigateToTab: (String) -> Unit,
) {
    val currentAppNameState by settingsViewModel.currentAppName.collectAsState()
    val loadingProgress by viewModel.loadingProgress.collectAsState()

    // Derived state for button loading
    val isBusy = loadingProgress != null

    var appName by remember { mutableStateOf("") }
    var githubUser by remember { mutableStateOf("") }
    var branchName by remember { mutableStateOf("main") }
    var repoDescription by remember { mutableStateOf("Created with IDEaz") }
    var initialPrompt by remember { mutableStateOf("") }
    var initialPromptTouched by remember { mutableStateOf(false) }

    LaunchedEffect(currentAppNameState, isCreateMode) {
        if (!isCreateMode) {
            appName = settingsViewModel.getAppName() ?: "IDEazProject"
            githubUser = settingsViewModel.getGithubUser() ?: ""
            branchName = settingsViewModel.getBranchName()
        } else {
            if (appName == "IDEazProject") appName = ""
        }
    }

    // Derived state for button enablement. This used to also require
    // `selectedType in ProjectType.selectable` - a one-element list.
    val isReadyToCreate = initialPrompt.isNotBlank() && appName.isNotBlank()

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
                hint = "GitHub User (optional until you publish)",
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

            // --- CREATE MODE SPECIFIC FIELDS ---
            if (isCreateMode) {
                Spacer(Modifier.height(8.dp))
                AzTextBox(
                    value = repoDescription,
                    onValueChange = { repoDescription = it },
                    hint = "Description (used if you publish to GitHub)",
                    onSubmit = {}
                )

                Spacer(Modifier.height(24.dp))

                // Prompt Text Box. Error only surfaces once the user has interacted
                // with the field — previously the "* Required" message rendered from
                // the moment Create mode opened, which read as a premature complaint.
                val isPromptError = initialPromptTouched && initialPrompt.isBlank()
                AzTextBox(
                    value = initialPrompt,
                    onValueChange = {
                        initialPrompt = it
                        initialPromptTouched = true
                    },
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

            }

            Spacer(Modifier.height(24.dp))

            if (isCreateMode) {
                AzButton(
                    onClick = {
                        // No GitHub token check. Creating a project is local and
                        // offline; the token is asked for by Deploy, which is what
                        // creates the repository.
                        if (onCheckRequirements()) {
                            viewModel.createProject(
                                appName, repoDescription, context,
                                githubUser = githubUser,
                                branchName = branchName,
                                initialPrompt = initialPrompt.takeIf { it.isNotBlank() }
                            ) {
                                onCreateModeChanged(false)
                            }
                        }
                    },
                    text = "Create & Save",
                    shape = AzButtonShape.RECTANGLE,
                    modifier = Modifier.fillMaxWidth(),
                    enabled = isReadyToCreate,
                    isLoading = isBusy
                )
            } else {
                AzButton(
                    onClick = {
                        if (onCheckRequirements()) {
                            // Ensure init first
                            viewModel.saveAndInitialize(
                                appName, githubUser, branchName, context, null
                            )
                            viewModel.sendPrompt(DOCS_PROMPT)
                        }
                    },
                    text = "Generate Project Docs (AI)",
                    shape = AzButtonShape.RECTANGLE,
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !isBusy
                )
                Text(
                    text = "Asks the AI to scaffold AGENTS.md and the docs/ folder for this repo. Sends a detailed prompt to the conversational chat.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = 4.dp, top = 4.dp)
                )

                Spacer(Modifier.height(8.dp))

                AzButton(
                    onClick = {
                        if (onCheckRequirements()) {
                            viewModel.saveAndInitialize(
                                appName, githubUser, branchName, context, null
                            )
                        }
                    },
                    text = "Save & Initialize",
                    shape = AzButtonShape.RECTANGLE,
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !isBusy,
                    isLoading = isBusy,
                )
            }
        }

    }
}
