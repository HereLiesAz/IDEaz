package com.hereliesaz.ideaz.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.navigation.compose.rememberNavController
import com.composables.core.rememberBottomSheetState
import com.composables.core.SheetDetent
import com.hereliesaz.aznavrail.AzButton
import com.hereliesaz.aznavrail.model.AzButtonShape

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(
    viewModel: MainViewModel,
    onRequestScreenCapture: () -> Unit,
    onThemeToggle: (Boolean) -> Unit,
    onLaunchOverlay: () -> Unit
) {
    val navController = rememberNavController()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val supportedDetents = remember {
        listOf(SheetDetent.Hidden, AlmostHidden, Peek, Halfway)
    }
    val sheetState = rememberBottomSheetState(
        detents = supportedDetents,
        initialDetent = Halfway
    )

    val isIdeVisible by viewModel.isTargetAppVisible.collectAsState() // Used for rail toggle visual state
    val isLocalBuildEnabled = viewModel.settingsViewModel.isLocalBuildEnabled()

    val isContextualChatVisible by viewModel.isContextualChatVisible.collectAsState()
    val activeSelectionRect by viewModel.activeSelectionRect.collectAsState()

    var showApiKeyAlert by remember { mutableStateOf(false) }
    var missingKeys by remember { mutableStateOf<List<String>>(emptyList()) }

    // Startup Check
    LaunchedEffect(Unit) {
        missingKeys = viewModel.checkRequiredKeys()
        if (missingKeys.isNotEmpty()) {
            showApiKeyAlert = true
        } else {
            // Navigate to Project Settings by default on startup if keys are good
            navController.navigate("project_settings")
        }
    }

    if (showApiKeyAlert) {
        AlertDialog(
            onDismissRequest = { /* Force user to address it */ },
            title = { Text("Missing Configuration") },
            text = { Text("The following keys are required for IDEaz to function:\n\n${missingKeys.joinToString("\n")}") },
            confirmButton = {
                AzButton(
                    onClick = {
                        showApiKeyAlert = false
                        navController.navigate("settings")
                    },
                    text = "Go to Settings",
                    shape = AzButtonShape.RECTANGLE
                )
            }
        )
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        containerColor = MaterialTheme.colorScheme.background,
    ) { innerPadding ->
        Box(modifier = Modifier.padding(innerPadding).fillMaxSize()) {
            Row(modifier = Modifier.fillMaxSize()) {
                IdeNavRail(
                    navController = navController,
                    viewModel = viewModel,
                    context = context,
                    onShowPromptPopup = { /*TODO*/ },
                    handleActionClick = { it() },
                    isIdeVisible = isIdeVisible,
                    onLaunchOverlay = onLaunchOverlay,
                    sheetState = sheetState,
                    scope = scope,
                    isLocalBuildEnabled = isLocalBuildEnabled,
                    onNavigateToMainApp = { route ->
                        viewModel.clearSelection()
                        navController.navigate(route) {
                            launchSingleTop = true
                            restoreState = true
                        }
                    }
                )
                IdeNavHost(
                    modifier = Modifier.weight(1f),
                    navController = navController,
                    viewModel = viewModel,
                    settingsViewModel = viewModel.settingsViewModel,
                    onThemeToggle = onThemeToggle
                )
            }

            // Overlay Contextual Chat on MainScreen (if not in bubble mode)
            if (isContextualChatVisible && activeSelectionRect != null) {
                ContextualChatOverlay(
                    rect = activeSelectionRect!!,
                    viewModel = viewModel,
                    onClose = { viewModel.closeContextualChat() }
                )
            }
        }
    }
}