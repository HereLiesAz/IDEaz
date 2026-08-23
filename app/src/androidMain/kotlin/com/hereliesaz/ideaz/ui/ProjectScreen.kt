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
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts

@Composable
fun ProjectScreen(
    viewModel: MainViewModel,
    settingsViewModel: SettingsViewModel,
    navController: NavController
) {
    val context = LocalContext.current
    val hasToken = !settingsViewModel.getGithubToken().isNullOrBlank()
    val loadingProgress by viewModel.loadingProgress.collectAsState()

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

    fun checkLoadRequirements(): Boolean {
        if (!checkKeys()) return false
        // Overlay and Accessibility permissions are requested lazily when needed
        // (e.g. OverlayDelegate.toggleSelectMode); project storage lives in
        // context.filesDir, which needs no storage permission at all.
        return true
    }

    // Permission probes (keys check) used to fire on first composition via
    // LaunchedEffect(Unit). That meant first-launch users got a system dialog
    // before they'd done anything. checkLoadRequirements/checkKeys are still
    // invoked at action time — Load
    // tab project-pick, SetupTab Save & Initialize / Create & Save — so the
    // user is prompted only when a path actually needs the permission/keys.

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
            onDismissRequest = { viewModel.dismissLoadingDialog() },
            title = { Text("Working...") },
            text = { Column { LinearProgressIndicator(progress = { (loadingProgress ?: 0) / 100f }); Text("$loadingProgress%") } },
            confirmButton = {},
            // Previously this dialog had no dismiss path at all (empty
            // onDismissRequest, no buttons) - a slow or offline network call
            // (e.g. the Clone tab's initial repo fetch) locked the entire
            // screen behind it until the request timed out on its own.
            // Dismissing only hides the dialog; the operation keeps running
            // and still updates its own state when it finishes.
            dismissButton = { TextButton(onClick = { viewModel.dismissLoadingDialog() }) { Text("Dismiss") } }
        )
    }


    Column(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
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
                    onCheckRequirements = { checkLoadRequirements() },
                    isCreateMode = isCreateMode,
                    onCreateModeChanged = { isCreateMode = it },
                    onNavigateToTab = { tabName ->
                        val idx = tabs.indexOf(tabName)
                        if (idx != -1) tabIndex = idx
                    },
                    onNavigateToSettings = { navController.navigate("settings") },
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
                    onForkRepo = { url ->
                        val parts = url.removePrefix("https://github.com/")
                            .removeSuffix(".git")
                            .split("/")
                            .filter { it.isNotBlank() }
                        if (parts.size < 2) {
                            android.widget.Toast.makeText(context, "Invalid repository format. Use 'owner/repo'.", android.widget.Toast.LENGTH_SHORT).show()
                        } else if (checkKeys()) {
                            android.widget.Toast.makeText(context, "Forking...", android.widget.Toast.LENGTH_SHORT).show()
                            viewModel.forkRepository(url) {
                                isCreateMode = false
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
                        // loadProject re-derives the type, re-mounts the preview, and
                        // shows the project (web → live preview, Android → host).
                        viewModel.loadProject(it, context) {
                            isCreateMode = false
                        }
                    }
                }
            }
        }
    }
}
