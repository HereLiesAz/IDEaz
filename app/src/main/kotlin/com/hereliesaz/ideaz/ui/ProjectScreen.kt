package com.hereliesaz.ideaz.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.width
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.hereliesaz.aznavrail.AzButton
import com.hereliesaz.aznavrail.model.AzButtonShape
import kotlinx.coroutines.launch
import com.hereliesaz.ideaz.ui.project.ProjectCloneTab
import com.hereliesaz.ideaz.ui.project.ProjectLoadTab
import com.hereliesaz.ideaz.ui.project.ProjectSetupTab
import com.hereliesaz.ideaz.utils.isAccessibilityServiceEnabled
import android.os.Build
import android.provider.Settings
import android.net.Uri
import android.content.Intent
import android.os.Environment
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import com.hereliesaz.ideaz.utils.ApkInstaller

@Composable
fun ProjectScreen(
    viewModel: MainViewModel,
    settingsViewModel: SettingsViewModel,
    onBuildTriggered: () -> Unit,
    navController: NavController
) {
    val context = LocalContext.current
    val hasToken = !settingsViewModel.getGithubToken().isNullOrBlank()
    val loadingProgress by viewModel.loadingProgress.collectAsState()
    val artifactCheckResult by viewModel.artifactCheckResult.collectAsState()

    var showDowngradeWarning by remember { mutableStateOf<Uri?>(null) }

    val scope = rememberCoroutineScope()
    val apkPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            // Check version before installing
            val remoteVersion = artifactCheckResult?.remoteVersion
            if (remoteVersion != null) {
                scope.launch {
                    val check = viewModel.checkLocalApkVersion(uri, remoteVersion)
                    if (check == "downgrade") {
                        showDowngradeWarning = uri
                    } else if (check == "mismatch") {
                         // Toast or error dialog handled by logging, but let's clear dialog
                         // viewModel.dismissArtifactDialog() // Maybe keep it open to try again?
                    } else {
                        ApkInstaller.installApk(context, uri)
                        viewModel.dismissArtifactDialog()
                        viewModel.launchTargetApp(context)
                    }
                }
            } else {
                // No remote version comparison needed
                ApkInstaller.installApk(context, uri)
                viewModel.dismissArtifactDialog()
                viewModel.launchTargetApp(context)
            }
        }
    }

    if (showDowngradeWarning != null) {
        AlertDialog(
            onDismissRequest = { showDowngradeWarning = null },
            title = { Text("Downgrade Warning") },
            text = { Text("The selected APK version is lower than the version available on GitHub. Install anyway?") },
            confirmButton = {
                AzButton(onClick = {
                    ApkInstaller.installApk(context, showDowngradeWarning!!)
                    showDowngradeWarning = null
                    viewModel.dismissArtifactDialog()
                    viewModel.launchTargetApp(context)
                }, text = "Install Anyway")
            },
            dismissButton = {
                TextButton(onClick = { showDowngradeWarning = null }) { Text("Cancel") }
            }
        )
    }

    // --- TABS LOGIC ---
    // Removed "Create" tab. It is now a state within "Setup".
    val tabs = remember {
        listOf("Setup", "Load", "Clone")
    }
    var tabIndex by remember { mutableIntStateOf(0) }
    val currentTabName = tabs.getOrElse(tabIndex) { "Setup" }

    // --- SCREEN STATE ---
    var isCreateMode by remember { mutableStateOf(false) }

    // --- REPO HEADER STATE ---
    val currentAppName by settingsViewModel.currentAppName.collectAsState()
    var displayAppName by remember { mutableStateOf("") }
    var displayUser by remember { mutableStateOf("") }
    var displayBranch by remember { mutableStateOf("") }

    LaunchedEffect(currentAppName, tabIndex) {
        displayAppName = settingsViewModel.getAppName() ?: ""
        displayUser = settingsViewModel.getGithubUser() ?: ""
        displayBranch = settingsViewModel.getBranchName()
    }

    // --- REQUIREMENTS LOGIC ---
    var showRequirementDialog by remember { mutableStateOf(false) }
    var requirementTitle by remember { mutableStateOf("") }
    var requirementMessage by remember { mutableStateOf("") }
    var requirementBtnText by remember { mutableStateOf("") }
    var requirementAction by remember { mutableStateOf<() -> Unit>({}) }

    fun checkKeys(): Boolean {
        val missing = viewModel.checkRequiredKeys()
        if (missing.isNotEmpty()) {
            requirementTitle = "Configuration Missing"
            requirementMessage = "The following keys are required:\n\n${missing.joinToString("\n")}"
            requirementBtnText = "Go to Settings"
            requirementAction = {
                showRequirementDialog = false
                navController.navigate("settings")
            }
            showRequirementDialog = true
            return false
        }
        return true
    }

    fun checkOverlayPermissions(): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !Settings.canDrawOverlays(context)) {
            requirementTitle = "Permission Required"
            requirementMessage = "IDEaz requires 'Display over other apps' permission."
            requirementBtnText = "Grant"
            requirementAction = {
                showRequirementDialog = false
                val intent = Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:${context.packageName}"))
                context.startActivity(intent)
            }
            showRequirementDialog = true
            return false
        }
        if (!isAccessibilityServiceEnabled(context, ".services.IdeazAccessibilityService")) {
            requirementTitle = "Service Required"
            requirementMessage = "Enable 'IDEaz Accessibility' service."
            requirementBtnText = "Enable"
            requirementAction = {
                showRequirementDialog = false
                context.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
            }
            showRequirementDialog = true
            return false
        }
        return true
    }

    fun checkLoadRequirements(): Boolean {
        if (!checkKeys()) return false
        val hasStorage = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) Environment.isExternalStorageManager() else true
        if (!hasStorage) {
            requirementTitle = "Permission Required"
            requirementMessage = "IDEaz requires full storage access."
            requirementBtnText = "Grant"
            requirementAction = {
                showRequirementDialog = false
                try {
                    context.startActivity(Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION, Uri.parse("package:${context.packageName}")))
                } catch (e: Exception) {
                    context.startActivity(Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION))
                }
            }
            showRequirementDialog = true
            return false
        }
        // Overlay and Accessibility permissions are now requested lazily when needed.
        return true
    }

    LaunchedEffect(Unit) { checkLoadRequirements() }

    // --- UI ---
    if (showRequirementDialog) {
        AlertDialog(
            onDismissRequest = { showRequirementDialog = false },
            title = { Text(requirementTitle) },
            text = { Text(requirementMessage) },
            confirmButton = { AzButton(onClick = requirementAction, text = requirementBtnText, shape = AzButtonShape.RECTANGLE) },
            dismissButton = { TextButton(onClick = { showRequirementDialog = false }) { Text("Cancel") } }
        )
    }

    if (loadingProgress != null) {
        AlertDialog(
            onDismissRequest = {},
            title = { Text("Working...") },
            text = { Column { LinearProgressIndicator(progress = { (loadingProgress ?: 0) / 100f }); Text("$loadingProgress%") } },
            confirmButton = {}
        )
    }

    if (artifactCheckResult != null) {
        val res = artifactCheckResult!!
        AlertDialog(
            onDismissRequest = { viewModel.dismissArtifactDialog() },
            title = { Text("Project Artifact Found") },
            text = {
                Column {
                    Text("A build is available on GitHub (v${res.remoteVersion}).")
                    if (res.localVersion != null) {
                        Text("Local version: v${res.localVersion}")
                    } else {
                        Text("No local version installed.")
                    }
                    Spacer(Modifier.height(8.dp))
                    Text("Do you want to download the latest version or use a local APK?")
                }
            },
            confirmButton = {
                AzButton(onClick = {
                    viewModel.downloadLatestArtifact(res.downloadUrl) { file ->
                        ApkInstaller.installApk(context, file.absolutePath)
                        viewModel.dismissArtifactDialog()
                        viewModel.launchTargetApp(context)
                    }
                }, text = "Download v${res.remoteVersion}")
            },
            dismissButton = {
                Row {
                    AzButton(onClick = {
                        apkPickerLauncher.launch("application/vnd.android.package-archive")
                    }, text = "Select Local", shape = AzButtonShape.NONE)
                    Spacer(Modifier.width(8.dp))
                    TextButton(onClick = { viewModel.dismissArtifactDialog() }) { Text("Cancel") }
                }
            }
        )
    }

    Column(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        // Use padding for the title area instead of a Spacer
        Column(modifier = Modifier.padding(top = 64.dp)) {
             Text(
                text = "Project",
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.onBackground,
                modifier = Modifier.padding(start = 16.dp, bottom = 8.dp)
            )
        }

        if (displayAppName.isNotBlank()) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 8.dp),
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
                        text = if (displayUser.isNotBlank()) "$displayUser/$displayAppName" else displayAppName,
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = "Branch: $displayBranch",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        PrimaryTabRow(selectedTabIndex = tabIndex) {
            tabs.forEachIndexed { index, title ->
                Tab(
                    text = { Text(title) },
                    icon = {
                        val icon = when (title) {
                            "Setup" -> Icons.Default.Build
                            "Load" -> Icons.Default.FolderOpen
                            "Clone" -> Icons.Default.CloudDownload
                            else -> Icons.Default.Build
                        }
                        Icon(icon, contentDescription = null)
                    },
                    selected = tabIndex == index,
                    onClick = { tabIndex = index }
                )
            }
        }

        Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
            when (currentTabName) {
                "Setup" -> ProjectSetupTab(
                    viewModel,
                    settingsViewModel,
                    context,
                    onBuildTriggered,
                    onCheckRequirements = { checkLoadRequirements() },
                    isCreateMode = isCreateMode,
                    onCreateModeChanged = { isCreateMode = it },
                    onNavigateToTab = { tabName ->
                        val idx = tabs.indexOf(tabName)
                        if (idx != -1) tabIndex = idx
                    }
                )
                "Clone" -> ProjectCloneTab(
                    viewModel,
                    settingsViewModel,
                    context,
                    onRepoSelected = { repo ->
                        if(checkKeys()) {
                            viewModel.selectRepositoryForSetup(repo) {
                                isCreateMode = false // Default to View/Init mode
                                tabIndex = tabs.indexOf("Setup")
                            }
                        }
                    },
                    onCreateNewSelected = {
                        if (checkKeys()) {
                            isCreateMode = true // Enable Create mode
                            tabIndex = tabs.indexOf("Setup")
                        }
                    },
                    onNavigateToSettings = { navController.navigate("settings") }
                )
                "Load" -> ProjectLoadTab(viewModel, settingsViewModel, context) {
                    if(checkLoadRequirements()) {
                        // Reset web state when loading a project (it might be Android)
                        viewModel.stateDelegate.setCurrentWebUrl(null)
                        viewModel.stateDelegate.setTargetAppVisible(false)
                        viewModel.loadProject(it) {
                            isCreateMode = false
                            tabIndex = tabs.indexOf("Setup")
                        }
                    }
                }
            }
        }
    }
}