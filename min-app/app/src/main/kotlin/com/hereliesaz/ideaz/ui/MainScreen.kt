package com.hereliesaz.ideaz.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import com.hereliesaz.aznavrail.AzButton
import com.hereliesaz.aznavrail.AzTextBox
import com.hereliesaz.aznavrail.model.AzButtonShape
import com.hereliesaz.ideaz.utils.BubbleUtils
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.composables.core.rememberBottomSheetState
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(
    viewModel: MainViewModel,
    onRequestScreenCapture: () -> Unit,
    onThemeToggle: (Boolean) -> Unit,
    onLaunchOverlay: () -> Unit
) {
    val context = LocalContext.current
    var showPromptPopup by remember{ mutableStateOf(false) }
    val navController = rememberNavController()
    val scope = rememberCoroutineScope()

    val showCancelDialog by viewModel.showCancelDialog.collectAsState()
    val isTargetAppVisible by viewModel.isTargetAppVisible.collectAsState()

    // --- Bottom Sheet State (Dashboard Console) ---
    val configuration = LocalConfiguration.current
    val screenHeight = configuration.screenHeightDp.dp

    val sheetState = rememberBottomSheetState(
        initialDetent = Peek,
        detents = listOf(AlmostHidden, Peek, Halfway)
    )

    // --- Visibility Logic ---
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route

    // Dashboard is visible unless we are hidden by target app
    val isDashboardVisible = !isTargetAppVisible
    val isBottomSheetVisible = currentRoute == "main" || currentRoute == "build"

    // Auto-expand sheet when navigating to Build screen (Dashboard view)
    LaunchedEffect(currentRoute) {
        if (currentRoute == "build") {
            sheetState.animateTo(Halfway)
        }
    }

    // Trigger for permission request
    LaunchedEffect(viewModel.requestScreenCapture.collectAsState().value) {
        if (viewModel.requestScreenCapture.value) {
            onRequestScreenCapture()
            viewModel.screenCaptureRequestHandled()
        }
    }

    val handleActionClick = { action: () -> Unit ->
        // If we are deep in settings, navigate back to main first?
        // Ideally we just run the action.
        action()
    }

    if (showPromptPopup) {
        PromptPopup(
            onDismiss = { showPromptPopup = false },
            onSubmit = { prompt ->
                viewModel.sendPrompt(prompt)
                showPromptPopup = false
            }
        )
    }

    if (showCancelDialog) {
        AlertDialog(
            onDismissRequest = { viewModel.dismissCancelTask() },
            title = { Text("Cancel Task") },
            text = { Text("Are you sure you want to cancel this task? All AI progress will be lost.") },
            confirmButton = {
                AzButton(onClick = { viewModel.confirmCancelTask() }, text = "Confirm", shape = AzButtonShape.RECTANGLE)
            },
            dismissButton = {
                AzButton(onClick = { viewModel.dismissCancelTask() }, text = "Dismiss", shape = AzButtonShape.RECTANGLE)
            }
        )
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        containerColor = if (isTargetAppVisible) Color.Transparent else MaterialTheme.colorScheme.background,
    ) { innerPadding ->

        Box(modifier = Modifier.padding(innerPadding).fillMaxSize()) {
            Row(
                modifier = Modifier.fillMaxSize()
            ) {
                IdeNavRail(
                    navController = navController,
                    viewModel = viewModel,
                    context = context,
                    onShowPromptPopup = { showPromptPopup = true },
                    handleActionClick = handleActionClick,
                    isIdeVisible = isDashboardVisible,
                    onLaunchOverlay = {
                        if (!viewModel.hasScreenCapturePermission()) {
                            viewModel.requestScreenCapturePermission()
                        } else {
                            onLaunchOverlay()
                        }
                    },
                    sheetState = sheetState,
                    scope = scope,
                    onUndock = { BubbleUtils.createBubbleNotification(context) }
                )

                AnimatedVisibility(
                    visible = isDashboardVisible,
                    modifier = Modifier.weight(1f)
                ) {
                    IdeNavHost(
                        modifier = Modifier.fillMaxSize(),
                        navController = navController,
                        viewModel = viewModel,
                        settingsViewModel = viewModel.settingsViewModel,
                        onThemeToggle = onThemeToggle
                    )
                }
            }

            // Dashboard Console (Separate from Overlay Console)
            if (isBottomSheetVisible && isDashboardVisible) {
                IdeBottomSheet(
                    sheetState = sheetState,
                    viewModel = viewModel,
                    peekDetent = Peek,
                    halfwayDetent = Halfway,
                    screenHeight = screenHeight,
                    onSendPrompt = { viewModel.sendPrompt(it) }
                )
            }
        }
    }
}

@Composable
fun PromptPopup(
    onDismiss: () -> Unit,
    onSubmit: (String) -> Unit
) {
    var prompt by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("New Prompt") },
        text = {
            Column {
                AzTextBox(
                    value = prompt,
                    onValueChange = { prompt = it },
                    hint = "Describe your change...",
                    onSubmit = { onSubmit(prompt) },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            AzButton(
                onClick = { onSubmit(prompt) },
                text = "Send",
                shape = AzButtonShape.RECTANGLE
            )
        },
        dismissButton = {
            AzButton(
                onClick = onDismiss,
                text = "Cancel",
                shape = AzButtonShape.RECTANGLE
            )
        }
    )
}
