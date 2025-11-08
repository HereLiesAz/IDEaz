package com.hereliesaz.ideaz.ui

import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.hereliesaz.ideaz.api.Session
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Checkbox
import androidx.compose.ui.Alignment
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.material3.Switch

@Composable
fun SettingsScreen(
    settingsViewModel: SettingsViewModel = viewModel(),
    sessions: List<Session>, // Accept the sessions list
    onThemeToggle: (Boolean) -> Unit
) {
    val context = LocalContext.current
    var apiKey by remember { mutableStateOf(settingsViewModel.getApiKey(context) ?: "") }
    var googleApiKey by remember { mutableStateOf(settingsViewModel.getGoogleApiKey(context) ?: "") }
    var isDarkMode by remember { mutableStateOf(settingsViewModel.isDarkMode(context)) }


    // --- NEW: State for Cancel Warning ---
    var showCancelWarning by remember {
        mutableStateOf(settingsViewModel.getShowCancelWarning(context))
    }
    // --- END NEW ---
    val screenHeight = LocalConfiguration.current.screenHeightDp.dp

    Column {
        Spacer(modifier = Modifier.height(screenHeight * 0.1f))
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(all = 8.dp)
                .verticalScroll(rememberScrollState())
        ) {
                Text("API Keys", color = MaterialTheme.colorScheme.onBackground)
                Spacer(modifier = Modifier.height(16.dp))

                // Jules API Key
                TextField(
                    value = apiKey,
                    onValueChange = { apiKey = it },
                    label = { Text("Jules API Key") },
                    visualTransformation = PasswordVisualTransformation(),
                    modifier = Modifier.fillMaxWidth()
                )
                Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Button(onClick = {
                        settingsViewModel.saveApiKey(context, apiKey)
                        Toast.makeText(context, "Jules Key Saved", Toast.LENGTH_SHORT).show()
                    }) {
                        Text("Save")
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(onClick = {
                        val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://jules.google.com/settings"))
                        context.startActivity(intent)
                    }) {
                        Text("Get Key")
                    }
                }


                Spacer(modifier = Modifier.height(16.dp))

                // Google AI Studio API Key
                TextField(
                    value = googleApiKey,
                    onValueChange = { googleApiKey = it },
                    label = { Text("Google AI Studio API Key") },
                    visualTransformation = PasswordVisualTransformation(),
                    modifier = Modifier.fillMaxWidth()
                )
                Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Button(onClick = {
                        settingsViewModel.saveGoogleApiKey(context, googleApiKey)
                        Toast.makeText(context, "AI Studio Key Saved", Toast.LENGTH_SHORT).show()
                    }) {
                        Text("Save")
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(onClick = {
                        val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://aistudio.google.com/app/api-keys"))
                        context.startActivity(intent)
                    }) {
                        Text("Get Key")
                    }
                }


                Spacer(modifier = Modifier.height(24.dp))
                Text("AI Assignments", color = MaterialTheme.colorScheme.onBackground)

                // Render dropdowns for each task
                SettingsViewModel.aiTasks.forEach { (taskKey, taskName) ->
                    var currentModelId by remember(taskKey) {
                        mutableStateOf(settingsViewModel.getAiAssignment(context, taskKey) ?: AiModels.JULES_DEFAULT)
                    }

                    AiAssignmentDropdown(
                        label = taskName,
                        selectedModelId = currentModelId,
                        onModelSelected = { model ->
                            currentModelId = model.id
                            settingsViewModel.saveAiAssignment(context, taskKey, model.id)
                        }
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                }


                Spacer(modifier = Modifier.height(24.dp))

                // --- NEW: Cancel Warning Checkbox ---
                Text("Preferences", color = MaterialTheme.colorScheme.onBackground)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Checkbox(
                        checked = showCancelWarning,
                        onCheckedChange = {
                            showCancelWarning = it
                            settingsViewModel.setShowCancelWarning(context, it)
                        }
                    )
                    Text("Show warning when cancelling AI task", color = MaterialTheme.colorScheme.onBackground)
                }

                Spacer(modifier = Modifier.height(16.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Dark Mode", color = MaterialTheme.colorScheme.onBackground)
                    Spacer(modifier = Modifier.width(16.dp))
                    Switch(
                        checked = isDarkMode,
                        onCheckedChange = {
                            isDarkMode = it
                            settingsViewModel.setDarkMode(context, it)
                            onThemeToggle(it)
                        }
                    )
                }
                // --- END NEW ---


                Spacer(modifier = Modifier.height(24.dp))

                // Display the list of sessions
                Text("Active Sessions", color = MaterialTheme.colorScheme.onBackground)
                if (sessions.isEmpty()) {
                    Text("No active sessions found.", color = MaterialTheme.colorScheme.onBackground)
                } else {
                    sessions.forEach {
                        Text(it.name, color = MaterialTheme.colorScheme.onBackground)
                    }
                }
            }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AiAssignmentDropdown(
    label: String,
    selectedModelId: String,
    onModelSelected: (AiModel) -> Unit
) {
    var isExpanded by remember { mutableStateOf(false) }
    val selectedModel = AiModels.findById(selectedModelId) ?: AiModels.JULES

    ExposedDropdownMenuBox(
        expanded = isExpanded,
        onExpandedChange = { isExpanded = it }
    ) {
        TextField(
            value = selectedModel.displayName,
            onValueChange = { },
            readOnly = true,
            label = { Text(label) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = isExpanded) },
            colors = ExposedDropdownMenuDefaults.textFieldColors(),
            modifier = Modifier
                .fillMaxWidth()
                .menuAnchor()
        )

        ExposedDropdownMenu(
            expanded = isExpanded,
            onDismissRequest = { isExpanded = false }
        ) {
            AiModels.availableModels.forEach { model ->
                DropdownMenuItem(
                    text = { Text(model.displayName) },
                    onClick = {
                        onModelSelected(model)
                        isExpanded = false
                    }
                )
            }
        }
    }
}
