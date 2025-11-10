package com.hereliesaz.ideaz.ui

import android.content.Intent
import android.inputmethodservice.Keyboard
import android.net.Uri
import android.util.Log
import android.widget.Toast
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import com.hereliesaz.aznavrail.AzButton
import com.hereliesaz.aznavrail.model.AzButtonShape
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.MaterialTheme
import com.hereliesaz.aznavrail.AzTextBox
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
import androidx.compose.material3.Switch

private const val TAG = "SettingsScreen"

@Composable
fun SettingsScreen(
    settingsViewModel: SettingsViewModel,
    sessions: List<Session>, // Accept the sessions list
    onThemeToggle: (Boolean) -> Unit
) {
    Log.d(TAG, "SettingsScreen: Composing")
    Log.d(TAG, "SettingsScreen: Composing")
    Log.d(TAG, "SettingsScreen: SettingsViewModel hash: ${settingsViewModel.hashCode()}")

    val context = LocalContext.current
    var apiKey by remember { mutableStateOf(settingsViewModel.getApiKey() ?: "") }
    var googleApiKey by remember { mutableStateOf(settingsViewModel.getGoogleApiKey() ?: "") }
    var isDarkMode by remember { mutableStateOf(settingsViewModel.isDarkMode()) }


    // --- NEW: State for Cancel Warning ---
    var showCancelWarning by remember {
        mutableStateOf(settingsViewModel.getShowCancelWarning())
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
                Text("API Keys", color = MaterialTheme.colorScheme.onBackground, style = MaterialTheme.typography.titleLarge)
                Spacer(modifier = Modifier.height(16.dp))
            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {

                Text("Jules API Key", color = MaterialTheme.colorScheme.onBackground, style = MaterialTheme.typography.labelSmall)
            }
                // Jules API Key
                Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    AzTextBox(
                        modifier = Modifier.fillMaxWidth(),
                        value = apiKey,
                        onValueChange = { apiKey = it },
                        hint = "Jules API Key",
                        secret = true,
                        onSubmit = {
                            settingsViewModel.saveApiKey(apiKey)
                            Toast.makeText(context, "Jules Key Saved", Toast.LENGTH_SHORT).show()
                        },
                        submitButtonContent = { Text("Save") }
                    )
                }
                Row(Modifier.width(60.dp) , verticalAlignment = Alignment.CenterVertically) {
                    AzButton(onClick = {
                        val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://jules.google.com/settings"))
                        context.startActivity(intent)
                    }, text = "Get Key", shape = AzButtonShape.NONE)
                }

            Row(Modifier.fillMaxWidth() , verticalAlignment = Alignment.CenterVertically) {
                Spacer(modifier = Modifier.height(24.dp))

            }
            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {

                Text("AI Studio API Key", color = MaterialTheme.colorScheme.onBackground, style = MaterialTheme.typography.labelSmall)
            }
                // Google AI Studio API Key
                Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    AzTextBox(
                        modifier = Modifier.fillMaxWidth(),
                        value = googleApiKey,
                        onValueChange = { googleApiKey = it },
                        hint = "AI Studio API Key",
                        secret = true,
                        onSubmit = {
                            settingsViewModel.saveGoogleApiKey(googleApiKey)
                            Toast.makeText(context, "AI Studio Key Saved", Toast.LENGTH_SHORT).show()
                        },
                        submitButtonContent = { Text("Save") }
                    )}
                    Row(Modifier.width(60.dp) , verticalAlignment = Alignment.CenterVertically) {
                    AzButton(onClick = {
                        val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://aistudio.google.com/app/api-keys"))
                        context.startActivity(intent)
                    }, text = "Get Key", shape = AzButtonShape.NONE)
                }

            Row(Modifier.fillMaxWidth() , verticalAlignment = Alignment.CenterVertically) {
                Spacer(modifier = Modifier.height(24.dp))

            }
                Spacer(modifier = Modifier.height(24.dp))
                Text("AI Assignments", color = MaterialTheme.colorScheme.onBackground, style = MaterialTheme.typography.titleLarge)

                // Render dropdowns for each task
                SettingsViewModel.aiTasks.forEach { (taskKey, taskName) ->
                    var currentModelId by remember(taskKey) {
                        mutableStateOf(settingsViewModel.getAiAssignment(taskKey) ?: AiModels.JULES_DEFAULT)
                    }

                    AiAssignmentDropdown(
                        label = taskName,
                        selectedModelId = currentModelId,
                        onModelSelected = { model ->
                            currentModelId = model.id
                            settingsViewModel.saveAiAssignment(taskKey, model.id)
                        }
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                }


                Spacer(modifier = Modifier.height(24.dp))

                // --- NEW: Cancel Warning Checkbox ---
                Text("Preferences", color = MaterialTheme.colorScheme.onBackground, style = MaterialTheme.typography.titleLarge)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Checkbox(
                        checked = showCancelWarning,
                        onCheckedChange = {
                            showCancelWarning = it
                            settingsViewModel.setShowCancelWarning(it)
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
                            settingsViewModel.setDarkMode(it)
                            onThemeToggle(it)
                        }
                    )
                }
                // --- END NEW ---


                Spacer(modifier = Modifier.height(24.dp))

                Text("Log Level", color = MaterialTheme.colorScheme.onBackground)
                LogLevelDropdown(
                    settingsViewModel = settingsViewModel
                )


                Spacer(modifier = Modifier.height(24.dp))

                // Display the list of sessions
                Text("Active Sessions", color = MaterialTheme.colorScheme.onBackground)
                if (sessions.isEmpty()) {
                    Text("No active sessions found.", color = MaterialTheme.colorScheme.onBackground)
                } else {
                    sessions.forEach {
                        Text(it.name ?: "Unnamed Session", color = MaterialTheme.colorScheme.onBackground)
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LogLevelDropdown(
    settingsViewModel: SettingsViewModel
) {
    var isExpanded by remember { mutableStateOf(false) }
    var selectedLevel by remember { mutableStateOf(settingsViewModel.getLogLevel()) }

    val levelOptions = mapOf(
        SettingsViewModel.LOG_LEVEL_INFO to "Info",
        SettingsViewModel.LOG_LEVEL_DEBUG to "Debug",
        SettingsViewModel.LOG_LEVEL_VERBOSE to "Verbose"
    )

    ExposedDropdownMenuBox(
        expanded = isExpanded,
        onExpandedChange = { isExpanded = it }
    ) {
        TextField(
            value = levelOptions[selectedLevel] ?: "Info",
            onValueChange = { },
            readOnly = true,
            label = { Text("Log Level") },
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
            levelOptions.forEach { (key, value) ->
                DropdownMenuItem(
                    text = { Text(value) },
                    onClick = {
                        selectedLevel = key
                        settingsViewModel.setLogLevel(key)
                        isExpanded = false
                    }
                )
            }
        }
    }
}