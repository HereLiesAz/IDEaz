package com.hereliesaz.ideaz.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import com.hereliesaz.aznavrail.AzButton
import com.hereliesaz.aznavrail.model.AzButtonShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
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
    onThemeToggle: (Boolean) -> Unit
) {
    val context = LocalContext.current
    var showPromptPopup by remember{ mutableStateOf(false) }
    val navController = rememberNavController()
    val scope = rememberCoroutineScope()

    val showCancelDialog by viewModel.showCancelDialog.collectAsState()

    // --- Bottom Sheet State ---
    val configuration = LocalConfiguration.current
    val screenHeight = configuration.screenHeightDp.dp

    val sheetState = rememberBottomSheetState(
        initialDetent = Peek,
        detents = listOf(AlmostHidden, Peek, Halfway)
    )

    // --- Visibility Logic ---
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route
    val isOnSettings = currentRoute == "settings"
    val isOnProjectSettings = currentRoute == "project_settings"

    val isIdeVisible = sheetState.currentDetent != AlmostHidden || isOnSettings || isOnProjectSettings
    val isBottomSheetVisible = currentRoute == "main" || currentRoute == "build"

    // Auto-expand sheet when navigating to Build screen
    LaunchedEffect(currentRoute) {
        if (currentRoute == "build") {
            sheetState.animateTo(Halfway)
        }
    }

    // --- Tie Inspection State to Sheet State ---
    LaunchedEffect(sheetState.currentDetent) {
        if (sheetState.currentDetent == AlmostHidden) {
            viewModel.stopInspection(context)
        } else {
            viewModel.startInspection(context)
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
        if (isOnSettings || isOnProjectSettings) {
            navController.navigate("main")
        }
        action()
    }

    val onModeToggleClick: () -> Unit = {
        scope.launch {
            if (isIdeVisible) {
                sheetState.animateTo(AlmostHidden)
            } else {
                if (!viewModel.hasScreenCapturePermission()) {
                    viewModel.requestScreenCapturePermission()
                } else {
                    sheetState.animateTo(Peek)
                }
            }
        }
    }

    // Auto-slide up when permission granted
    LaunchedEffect(viewModel.hasScreenCapturePermission()) {
        if (viewModel.hasScreenCapturePermission() && sheetState.currentDetent == AlmostHidden) {
            scope.launch {
                sheetState.animateTo(Peek)
            }
        }
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
        containerColor = MaterialTheme.colorScheme.background,
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
                    isIdeVisible = isIdeVisible,
                    onModeToggleClick = onModeToggleClick,
                    sheetState = sheetState,
                    scope = scope
                )

                // --- FIX: Apply weight to AnimatedVisibility, not IdeNavHost ---
                AnimatedVisibility(
                    visible = isIdeVisible,
                    modifier = Modifier.weight(1f)
                ) {
                    IdeNavHost(
                        modifier = Modifier.fillMaxSize(), // Fill the weighted container
                        navController = navController,
                        viewModel = viewModel,
                        settingsViewModel = viewModel.settingsViewModel,
                        onThemeToggle = onThemeToggle
                    )
                }
                // --- END FIX ---
            }

            if (isBottomSheetVisible) {
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