package com.hereliesaz.ideaz.ui

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.util.Log
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.Icon
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Switch
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.clickable
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.hereliesaz.aznavrail.AzButton
import com.hereliesaz.aznavrail.AzTextBox
import com.hereliesaz.aznavrail.model.AzButtonShape
import androidx.compose.foundation.background
import com.hereliesaz.ideaz.utils.ToolManager
import java.io.File

private const val TAG = "SettingsScreen"

@Composable
fun SettingsScreen(
    viewModel: MainViewModel,
    settingsViewModel: SettingsViewModel,
    onThemeToggle: (Boolean) -> Unit
) {
    Log.d(TAG, "SettingsScreen: Composing")

    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    var apiKey by remember { mutableStateOf(settingsViewModel.getApiKey() ?: "") }
    var googleApiKey by remember { mutableStateOf(settingsViewModel.getGoogleApiKey() ?: "") }
    var githubToken by remember { mutableStateOf(settingsViewModel.getGithubToken() ?: "") }
    var julesProjectId by remember { mutableStateOf(settingsViewModel.getJulesProjectId() ?: "") }

    var showCancelWarning by remember {
        mutableStateOf(settingsViewModel.getShowCancelWarning())
    }

    var autoReportBugs by remember {
        mutableStateOf(settingsViewModel.getAutoReportBugs())
    }

    // Local Build State
    var isLocalBuildEnabled by remember {
        mutableStateOf(settingsViewModel.isLocalBuildEnabled())
    }
    var showDownloadToolsDialog by remember { mutableStateOf(false) }
    var showDeleteToolsDialog by remember { mutableStateOf(false) }

    // --- NEW: Signing State ---
    var keystorePath by remember { mutableStateOf(settingsViewModel.getKeystorePath() ?: "Default (debug.keystore)") }
    var keystorePass by remember { mutableStateOf(settingsViewModel.getKeystorePass()) }
    var keyAlias by remember { mutableStateOf(settingsViewModel.getKeyAlias()) }
    var keyPass by remember { mutableStateOf(settingsViewModel.getKeyPass()) }

    val keystorePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            val newPath = settingsViewModel.importKeystore(context, uri)
            if (newPath != null) {
                keystorePath = newPath
                Toast.makeText(context, "Keystore imported", Toast.LENGTH_SHORT).show()
            }
        }
    }
    // --- END NEW ---

    val screenHeight = LocalConfiguration.current.screenHeightDp.dp

    var refreshTrigger by remember { mutableStateOf(0) } // Force recomposition

    // Refresh permissions on resume
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                refreshTrigger++
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
        onResult = { isGranted ->
            Log.d(TAG, "Notification permission granted: $isGranted")
            refreshTrigger++
        }
    )

    val overlayPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult(),
        onResult = {
            Log.d(TAG, "Returned from overlay settings")
            refreshTrigger++
        }
    )

    val installPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult(),
        onResult = {
            Log.d(TAG, "Returned from install settings")
            refreshTrigger++
        }
    )

    val accessibilitySettingsLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult(),
        onResult = {
            Log.d(TAG, "Returned from accessibility settings")
            refreshTrigger++
        }
    )

    // --- Dialogs ---
    if (showDownloadToolsDialog) {
        AlertDialog(
            onDismissRequest = {
                showDownloadToolsDialog = false
                // Revert toggle if cancelled
                isLocalBuildEnabled = false
            },
            title = { Text("Download Build Tools?") },
            text = { Text("Local compilation requires additional tools (~100MB). Download them now?") },
            confirmButton = {
                AzButton(onClick = {
                    showDownloadToolsDialog = false
                    viewModel.downloadBuildTools()
                    settingsViewModel.setLocalBuildEnabled(true)
                    isLocalBuildEnabled = true
                }, text = "Download")
            },
            dismissButton = {
                AzButton(onClick = {
                    showDownloadToolsDialog = false
                    isLocalBuildEnabled = false
                }, text = "Cancel", shape = AzButtonShape.NONE)
            }
        )
    }

    if (showDeleteToolsDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteToolsDialog = false },
            title = { Text("Delete Build Tools?") },
            text = { Text("You disabled local builds. Do you want to delete the build tools to free up space?") },
            confirmButton = {
                AzButton(onClick = {
                    ToolManager.deleteTools(context)
                    showDeleteToolsDialog = false
                    settingsViewModel.setLocalBuildEnabled(false)
                    isLocalBuildEnabled = false
                    Toast.makeText(context, "Tools deleted.", Toast.LENGTH_SHORT).show()
                }, text = "Delete")
            },
            dismissButton = {
                AzButton(onClick = {
                    showDeleteToolsDialog = false
                    settingsViewModel.setLocalBuildEnabled(false)
                    isLocalBuildEnabled = false
                }, text = "Keep", shape = AzButtonShape.NONE)
            }
        )
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(
                    top = 32.dp,
                    bottom = 32.dp
                )
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp)
                    .verticalScroll(rememberScrollState())
            ) {

                Text(
                    "API Keys",
                    color = MaterialTheme.colorScheme.onBackground,
                    style = MaterialTheme.typography.titleLarge
                )

                Spacer(modifier = Modifier.height(16.dp))
                Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {

                    Text("Jules API Key", color = MaterialTheme.colorScheme.onBackground, style = MaterialTheme.typography.labelSmall)
                }
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
                Row(Modifier.width(60.dp), verticalAlignment = Alignment.CenterVertically) {
                    AzButton(onClick = {
                        val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://jules.google.com/settings"))
                        context.startActivity(intent)
                    }, text = "Get Key", shape = AzButtonShape.NONE)
                }

                Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Spacer(modifier = Modifier.height(24.dp))
                }
                Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text("Jules Project ID", color = MaterialTheme.colorScheme.onBackground, style = MaterialTheme.typography.labelSmall)
                }
                Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    AzTextBox(
                        modifier = Modifier.fillMaxWidth(),
                        value = julesProjectId,
                        onValueChange = { julesProjectId = it },
                        hint = "Jules Project ID",
                        onSubmit = {
                            settingsViewModel.saveJulesProjectId(julesProjectId)
                            Toast.makeText(context, "Jules Project ID Saved", Toast.LENGTH_SHORT).show()
                        },
                        submitButtonContent = { Text("Save") }
                    )
                }

                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Spacer(modifier = Modifier.height(24.dp))

                }
                Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {

                    Text("GitHub Personal Access Token", color = MaterialTheme.colorScheme.onBackground, style = MaterialTheme.typography.labelSmall)
                }
                Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    AzTextBox(
                        modifier = Modifier.fillMaxWidth(),
                        value = githubToken,
                        onValueChange = { githubToken = it },
                        hint = "GitHub Token",
                        secret = true,
                        onSubmit = {
                            settingsViewModel.saveGithubToken(githubToken)
                            Toast.makeText(context, "GitHub Token Saved", Toast.LENGTH_SHORT).show()
                        },
                        submitButtonContent = { Text("Save") }
                    )
                }
                Row(Modifier.width(60.dp), verticalAlignment = Alignment.CenterVertically) {
                    AzButton(onClick = {
                        val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/settings/tokens"))
                        context.startActivity(intent)
                    }, text = "Get Key", shape = AzButtonShape.NONE)
                }

                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Spacer(modifier = Modifier.height(24.dp))

                }
                Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {

                    Text("AI Studio API Key", color = MaterialTheme.colorScheme.onBackground, style = MaterialTheme.typography.labelSmall)
                }
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
                    )
                }
                Row(Modifier.width(60.dp), verticalAlignment = Alignment.CenterVertically) {
                    AzButton(onClick = {
                        val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://aistudio.google.com/app/api-keys"))
                        context.startActivity(intent)
                    }, text = "Get Key", shape = AzButtonShape.NONE)
                }

                Spacer(modifier = Modifier.height(24.dp))

                // --- BUILD CONFIGURATION ---
                Text("Build Configuration", color = MaterialTheme.colorScheme.onBackground, style = MaterialTheme.typography.titleLarge)
                Spacer(modifier = Modifier.height(16.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Enable Local Builds (Experimental)",
                        modifier = Modifier.weight(1f),
                        color = MaterialTheme.colorScheme.onBackground
                    )
                    Switch(
                        checked = isLocalBuildEnabled,
                        onCheckedChange = { enabled ->
                            if (enabled) {
                                // Check if tools exist
                                if (!ToolManager.areToolsInstalled(context)) {
                                    showDownloadToolsDialog = true
                                    // Toggle waits for confirmation
                                } else {
                                    isLocalBuildEnabled = true
                                    settingsViewModel.setLocalBuildEnabled(true)
                                }
                            } else {
                                // Disable
                                showDeleteToolsDialog = true
                                // Toggle waits for confirmation/dismiss of dialog
                            }
                        }
                    )
                }
                Text(
                    text = "Requires downloading extension (~100MB). If disabled, the app relies solely on GitHub Actions for builds.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Spacer(modifier = Modifier.height(24.dp))

                // --- NEW: Signing Config Section ---
                Text("Signing Configuration", color = MaterialTheme.colorScheme.onBackground, style = MaterialTheme.typography.titleLarge)
                Spacer(modifier = Modifier.height(8.dp))

                Text("Current Keystore: ${File(keystorePath).name}", color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(modifier = Modifier.height(8.dp))

                AzButton(
                    onClick = { keystorePickerLauncher.launch("*/*") },
                    text = "Select Custom Keystore",
                    shape = AzButtonShape.RECTANGLE,
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(8.dp))
                AzTextBox(
                    value = keystorePass,
                    onValueChange = { keystorePass = it },
                    hint = "Keystore Password",
                    secret = true,
                    onSubmit = { settingsViewModel.saveSigningCredentials(keystorePass, keyAlias, keyPass) }
                )
                Spacer(modifier = Modifier.height(8.dp))
                AzTextBox(
                    value = keyAlias,
                    onValueChange = { keyAlias = it },
                    hint = "Key Alias",
                    onSubmit = { settingsViewModel.saveSigningCredentials(keystorePass, keyAlias, keyPass) }
                )
                Spacer(modifier = Modifier.height(8.dp))
                AzTextBox(
                    value = keyPass,
                    onValueChange = { keyPass = it },
                    hint = "Key Password",
                    secret = true,
                    onSubmit = {
                        settingsViewModel.saveSigningCredentials(keystorePass, keyAlias, keyPass)
                        Toast.makeText(context, "Signing config saved", Toast.LENGTH_SHORT).show()
                    },
                    submitButtonContent = { Text("Save") }
                )
                Spacer(modifier = Modifier.height(8.dp))
                AzButton(
                    onClick = {
                        settingsViewModel.clearSigningConfig()
                        keystorePath = "Default (debug.keystore)"
                        keystorePass = "android"
                        keyAlias = "androiddebugkey"
                        keyPass = "android"
                        Toast.makeText(context, "Reset to default debug keystore", Toast.LENGTH_SHORT).show()
                    },
                    text = "Reset to Default",
                    shape = AzButtonShape.NONE,
                    modifier = Modifier.align(Alignment.End)
                )
                // --- END NEW ---

                Spacer(modifier = Modifier.height(24.dp))
                Text("AI Assignments", color = MaterialTheme.colorScheme.onBackground, style = MaterialTheme.typography.titleLarge)

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

                Text("Permissions", color = MaterialTheme.colorScheme.onBackground, style = MaterialTheme.typography.titleLarge)
                Spacer(modifier = Modifier.height(16.dp))

                val hasOverlay by remember(refreshTrigger) {
                    mutableStateOf(if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) Settings.canDrawOverlays(context) else true)
                }
                val hasNotify by remember(refreshTrigger) {
                    mutableStateOf(if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED else true)
                }
                val hasInstall by remember(refreshTrigger) {
                    mutableStateOf(if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) context.packageManager.canRequestPackageInstalls() else true)
                }
                val hasScreenshot by remember(viewModel.hasScreenCapturePermission()) { mutableStateOf(viewModel.hasScreenCapturePermission()) }
                val hasAccessibility by remember(refreshTrigger) {
                    mutableStateOf(isAccessibilityServiceEnabled(context, ".services.UIInspectionService"))
                }

                PermissionCheckRow(
                    name = "Draw Over Other Apps",
                    granted = hasOverlay,
                    onClick = {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                            val intent = Intent(
                                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                                Uri.parse("package:${context.packageName}")
                            )
                            overlayPermissionLauncher.launch(intent)
                        }
                    }
                )

                PermissionCheckRow(
                    name = "Accessibility Service",
                    granted = hasAccessibility,
                    onClick = {
                        val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
                        accessibilitySettingsLauncher.launch(intent)
                    }
                )

                PermissionCheckRow(
                    name = "Post Notifications",
                    granted = hasNotify,
                    onClick = {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                        }
                    }
                )

                PermissionCheckRow(
                    name = "Install Unknown Apps",
                    granted = hasInstall,
                    onClick = {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                            val intent = Intent(
                                Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                                Uri.parse("package:${context.packageName}")
                            )
                            installPermissionLauncher.launch(intent)
                        }
                    }
                )

                PermissionCheckRow(
                    name = "Screen Capture",
                    granted = hasScreenshot,
                    onClick = {
                        if (!hasScreenshot) {
                            viewModel.requestScreenCapturePermission()
                        } else {
                            Toast.makeText(context, "Permission already granted", Toast.LENGTH_SHORT).show()
                        }
                    }
                )

                Spacer(modifier = Modifier.height(24.dp))

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

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Checkbox(
                        checked = autoReportBugs,
                        onCheckedChange = {
                            autoReportBugs = it
                            settingsViewModel.setAutoReportBugs(it)
                        }
                    )
                    Text("Auto-report IDE internal errors to GitHub", color = MaterialTheme.colorScheme.onBackground)
                }

                Spacer(modifier = Modifier.height(16.dp))

                ThemeDropdown(
                    settingsViewModel = settingsViewModel,
                    onThemeToggle = onThemeToggle
                )


                Spacer(modifier = Modifier.height(24.dp))

                Text("Log Level", color = MaterialTheme.colorScheme.onBackground)
                LogLevelDropdown(
                    settingsViewModel = settingsViewModel
                )

                Spacer(modifier = Modifier.height(24.dp))

                Text("Updates", color = MaterialTheme.colorScheme.onBackground, style = MaterialTheme.typography.titleLarge)
                Spacer(modifier = Modifier.height(16.dp))

                val updateStatus by viewModel.updateStatus.collectAsState()
                val showUpdateWarning by viewModel.showUpdateWarning.collectAsState()

                if (updateStatus != null) {
                    AlertDialog(
                        onDismissRequest = { },
                        title = { Text("Updating") },
                        text = {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                CircularProgressIndicator()
                                Spacer(modifier = Modifier.height(16.dp))
                                Text(updateStatus!!)
                            }
                        },
                        confirmButton = {}
                    )
                }

                if (showUpdateWarning) {
                    AlertDialog(
                        onDismissRequest = { viewModel.dismissUpdateWarning() },
                        title = { Text("Update Ready") },
                        text = { Text("An update has been downloaded. The version might be the same as the installed one. Proceed with installation?") },
                        confirmButton = {
                            AzButton(onClick = { viewModel.confirmUpdate() }, text = "Install")
                        },
                        dismissButton = {
                            AzButton(onClick = { viewModel.dismissUpdateWarning() }, text = "Cancel", shape = AzButtonShape.NONE)
                        }
                    )
                }

                AzButton(
                    onClick = { viewModel.checkForExperimentalUpdates() },
                    text = "Check for Experimental Updates",
                    shape = AzButtonShape.RECTANGLE,
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(24.dp))
                Text("Debug", color = MaterialTheme.colorScheme.onBackground, style = MaterialTheme.typography.titleLarge)
                Spacer(modifier = Modifier.height(16.dp))
                AzButton(
                    onClick = {
                        viewModel.clearBuildCaches(context)
                        Toast.makeText(context, "Build caches cleared", Toast.LENGTH_SHORT).show()
                    },
                    text = "Clear Build Caches",
                    shape = AzButtonShape.RECTANGLE,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }
}

fun isAccessibilityServiceEnabled(context: Context, serviceName: String): Boolean {
    val prefString = Settings.Secure.getString(context.contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES)
    return prefString?.contains(context.packageName + serviceName) ?: false
}

@Composable
fun PermissionCheckRow(
    name: String,
    granted: Boolean,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = name,
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onBackground
        )
        if (granted) {
            Icon(Icons.Default.Check, contentDescription = "Granted", tint = Color.Green)
        } else {
            Icon(Icons.Default.Close, contentDescription = "Not Granted", tint = MaterialTheme.colorScheme.error)
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ThemeDropdown(
    settingsViewModel: SettingsViewModel,
    onThemeToggle: (Boolean) -> Unit
) {
    var isExpanded by remember { mutableStateOf(false) }
    var selectedMode by remember { mutableStateOf(settingsViewModel.getThemeMode()) }

    val themeOptions = mapOf(
        SettingsViewModel.THEME_AUTO to "Automatic",
        SettingsViewModel.THEME_DARK to "Dark Mode",
        SettingsViewModel.THEME_LIGHT to "Light Mode",
        SettingsViewModel.THEME_SYSTEM to "Match System"
    )

    ExposedDropdownMenuBox(
        expanded = isExpanded,
        onExpandedChange = { isExpanded = it }
    ) {
        TextField(
            value = themeOptions[selectedMode] ?: "Automatic",
            onValueChange = { },
            readOnly = true,
            label = { Text("Theme") },
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
            themeOptions.forEach { (key, value) ->
                DropdownMenuItem(
                    text = { Text(value) },
                    onClick = {
                        selectedMode = key
                        settingsViewModel.setThemeMode(key)
                        onThemeToggle(true)
                        isExpanded = false
                    }
                )
            }
        }
    }
}