package com.hereliesaz.ideaz.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.hereliesaz.aznavrail.AzButton
import com.hereliesaz.aznavrail.model.AzButtonShape
import com.hereliesaz.ideaz.ui.project.ProjectCloneTab
import com.hereliesaz.ideaz.ui.project.ProjectLoadTab
import com.hereliesaz.ideaz.ui.project.ProjectSetupTab
import android.os.Build
import android.provider.Settings
import android.net.Uri
import android.content.Intent
import android.os.Environment

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

    // --- TABS LOGIC ---
    // Removed "Create" tab. It is now a state within "Setup".
    val tabs = remember(hasToken) {
        listOf("Setup", "Clone", "Load")
    }
    var tabIndex by remember { mutableStateOf(0) }
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
        if (!isAccessibilityServiceEnabled(context, ".services.UIInspectionService")) {
            requirementTitle = "Service Required"
            requirementMessage = "Enable 'IDEaz Inspection Service' (Accessibility)."
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
        if (!checkOverlayPermissions()) return false
        return true
    }

    LaunchedEffect(Unit) { checkKeys() }

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

    Column(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        Spacer(modifier = Modifier.height(32.dp))

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
                Tab(text = { Text(title) }, selected = tabIndex == index, onClick = { tabIndex = index })
            }
        }

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
                }
            )
            "Load" -> ProjectLoadTab(viewModel, settingsViewModel, context) {
                if(checkLoadRequirements()) {
                    viewModel.loadProject(it) {
                        isCreateMode = false
                        tabIndex = tabs.indexOf("Setup")
                    }
                }
            }
        }
    }
}