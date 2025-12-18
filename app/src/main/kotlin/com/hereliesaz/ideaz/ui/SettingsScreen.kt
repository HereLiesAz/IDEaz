package com.hereliesaz.ideaz.ui

import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.hereliesaz.ideaz.BuildConfig

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val scrollState = rememberScrollState()

    // State initialization
    var apiKey by remember { mutableStateOf(viewModel.getApiKey() ?: "") }
    var githubToken by remember { mutableStateOf(viewModel.getGithubToken() ?: "") }
    var githubUser by remember { mutableStateOf(viewModel.getGithubUser() ?: "") }
    var appName by remember { mutableStateOf(viewModel.getAppName() ?: "") }
    var googleApiKey by remember { mutableStateOf(viewModel.getGoogleApiKey() ?: "") }
    var julesProjectId by remember { mutableStateOf(viewModel.getJulesProjectId() ?: "") }

    var showCancelWarning by remember { mutableStateOf(viewModel.getShowCancelWarning()) }
    var autoReportBugs by remember { mutableStateOf(viewModel.getAutoReportBugs()) }
    var autoDebugBuilds by remember { mutableStateOf(viewModel.isAutoDebugBuildsEnabled()) }
    var reportIdeErrors by remember { mutableStateOf(viewModel.isReportIdeErrorsEnabled()) }
    var localBuildEnabled by remember { mutableStateOf(viewModel.isLocalBuildEnabled()) }

    var keystorePath by remember { mutableStateOf(viewModel.getKeystorePath() ?: "") }
    var keystorePass by remember { mutableStateOf(viewModel.getKeystorePass() ?: "") }
    var keyAlias by remember { mutableStateOf(viewModel.getKeyAlias() ?: "") }
    var keyPass by remember { mutableStateOf(viewModel.getKeyPass() ?: "") }

    val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        uri?.let {
            try {
                // Fixed: Pass required params. For password we use default or ask (simplifying here)
                viewModel.exportSettings(context, it, "default")
                Toast.makeText(context, "Settings Exported", Toast.LENGTH_SHORT).show()
            } catch (e: Exception) {
                Toast.makeText(context, "Export Failed: ${e.message}", Toast.LENGTH_LONG).show()
            }
        }
    }

    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let {
            try {
                // Fixed: Pass required params
                viewModel.importSettings(context, it, "default")
                Toast.makeText(context, "Settings Imported", Toast.LENGTH_SHORT).show()
                // Refresh local state logic would go here
            } catch (e: Exception) {
                Toast.makeText(context, "Import Failed: ${e.message}", Toast.LENGTH_LONG).show()
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .padding(16.dp)
                .verticalScroll(scrollState),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {

            Text("Project Config", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)

            OutlinedTextField(
                value = githubUser,
                onValueChange = {
                    githubUser = it
                    viewModel.setGithubUser(it)
                },
                label = { Text("GitHub Username") },
                modifier = Modifier.fillMaxWidth()
            )

            OutlinedTextField(
                value = appName,
                onValueChange = {
                    appName = it
                    viewModel.setAppName(it)
                },
                label = { Text("App Name / Repo") },
                modifier = Modifier.fillMaxWidth()
            )

            OutlinedTextField(
                value = githubToken,
                onValueChange = {
                    githubToken = it
                    viewModel.saveGithubToken(it)
                },
                label = { Text("GitHub Token") },
                modifier = Modifier.fillMaxWidth()
            )

            Text("AI Configuration", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)

            OutlinedTextField(
                value = apiKey,
                onValueChange = {
                    apiKey = it
                    viewModel.saveApiKey(it)
                },
                label = { Text("Jules API Key") },
                modifier = Modifier.fillMaxWidth()
            )

            OutlinedTextField(
                value = googleApiKey,
                onValueChange = {
                    googleApiKey = it
                    viewModel.saveGoogleApiKey(it)
                },
                label = { Text("Google API Key") },
                modifier = Modifier.fillMaxWidth()
            )

            Text("Behavior", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)

            Row(verticalAlignment = Alignment.CenterVertically) {
                Checkbox(checked = showCancelWarning, onCheckedChange = {
                    showCancelWarning = it
                    viewModel.setShowCancelWarning(it)
                })
                Text("Show Warning on Cancel")
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                Checkbox(checked = autoReportBugs, onCheckedChange = {
                    autoReportBugs = it
                    viewModel.setAutoReportBugs(it)
                })
                Text("Auto Report Bugs")
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                Checkbox(checked = localBuildEnabled, onCheckedChange = {
                    localBuildEnabled = it
                    viewModel.setLocalBuildEnabled(it)
                })
                Text("Enable Local Build (Experimental)")
            }

            Text("Signing Configuration", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)

            OutlinedTextField(
                value = keystorePath,
                onValueChange = { keystorePath = it },
                label = { Text("Keystore Path") },
                modifier = Modifier.fillMaxWidth()
            )

            OutlinedTextField(
                value = keystorePass,
                onValueChange = { keystorePass = it },
                label = { Text("Keystore Password") },
                modifier = Modifier.fillMaxWidth()
            )

            OutlinedTextField(
                value = keyAlias,
                onValueChange = { keyAlias = it },
                label = { Text("Key Alias") },
                modifier = Modifier.fillMaxWidth()
            )

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = {
                    // Fixed: removed extra path arg, only saving credentials here
                    viewModel.saveSigningCredentials(keystorePass, keyAlias, keyPass)
                    Toast.makeText(context, "Signing Config Saved", Toast.LENGTH_SHORT).show()
                }) {
                    Text("Save Config")
                }

                OutlinedButton(onClick = {
                    viewModel.clearSigningConfig()
                    keystorePath = ""
                    keystorePass = ""
                    keyAlias = ""
                    keyPass = ""
                }) {
                    Text("Clear")
                }
            }

            Text("Data Management", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = { exportLauncher.launch("ideaz_settings.json") }) {
                    Text("Export Settings")
                }
                Button(onClick = { importLauncher.launch(arrayOf("application/json")) }) {
                    Text("Import Settings")
                }
            }

            Divider()
            Text("IDEaz v${BuildConfig.VERSION_NAME}", style = MaterialTheme.typography.bodySmall)
        }
    }
}