package com.hereliesaz.ideaz.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import com.hereliesaz.aznavrail.AzButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.composables.core.SheetDetent
import com.composables.core.rememberBottomSheetState
import com.hereliesaz.ideaz.ui.theme.IDEazTheme
import kotlinx.coroutines.launch

// 1. Define custom detents
private val AlmostHidden by lazy { SheetDetent("almost_hidden") { _, _ -> 6.dp } }
private val Peek = SheetDetent("peek") { containerHeight, _ -> containerHeight * 0.2f }
private val Halfway = SheetDetent("halfway") { containerHeight, _ -> containerHeight * 0.5f }


@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(
    viewModel: MainViewModel,
    onRequestScreenCapture: () -> Unit, // NEW: Lambda to trigger permission
    settingsViewModel: SettingsViewModel,
    onThemeToggle: (Boolean) -> Unit
) {
    val session by viewModel.session.collectAsState()
    val sessions by viewModel.sessions.collectAsState()
    val activities by viewModel.activities.collectAsState()
    val sources by viewModel.sources.collectAsState()
    val context = LocalContext.current
    var showPromptPopup by remember{ mutableStateOf(false) } // This is for the OLD popup
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
    // --- End Visibility Logic ---

    // --- Tie Inspection State to Sheet State ---
    LaunchedEffect(sheetState.currentDetent) {
        if (sheetState.currentDetent == AlmostHidden) {
            // "Interaction Mode"
            viewModel.stopInspection(context)
        } else {
            // "Selection Mode"
            // NEW: Only start inspection if we have permission.
            // If not, permission will be requested by the button.
            if (viewModel.hasScreenCapturePermission()) {
                viewModel.startInspection(context)
            }
        }
    }

    // NEW: Trigger for permission request
    LaunchedEffect(viewModel.requestScreenCapture.collectAsState().value) {
        if (viewModel.requestScreenCapture.value) {
            onRequestScreenCapture()
            viewModel.screenCaptureRequestHandled() // Reset the trigger
        }
    }

    val containerColor = Color.Transparent

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
                // NEW: Request permission *before* sliding the sheet up
                if (!viewModel.hasScreenCapturePermission()) {
                    viewModel.requestScreenCapturePermission()
                } else {
                    sheetState.animateTo(Peek)
                }
            }
        }
    }

    // NEW: When permission is granted, auto-slide up
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
                AzButton(onClick = { viewModel.confirmCancelTask() }, text = "Confirm")
            },
            dismissButton = {
                AzButton(onClick = { viewModel.dismissCancelTask() }, text = "Dismiss")
            }
        )
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        containerColor = containerColor,
    ) { innerPadding ->

        // Use a Box to layer the IDE content and the BottomSheet
        Box(modifier = Modifier.padding(innerPadding).fillMaxSize()) {
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .then(
                        if (isIdeVisible) Modifier.background(MaterialTheme.colorScheme.background)
                        else Modifier
                    )
            ) {
                // Call the extracted NavRail
                IdeNavRail(
                    navController = navController,
                    viewModel = viewModel,
                    context = context,
                    onShowPromptPopup = { showPromptPopup = true },
                    handleActionClick = handleActionClick,
                    isIdeVisible = isIdeVisible,
                    onModeToggleClick = onModeToggleClick // Pass the click handler
                )

                // This is the main screen content, which we make visible/invisible
                AnimatedVisibility(visible = isIdeVisible) {
                    // Call the extracted NavHost
                    IdeNavHost(
                        modifier = Modifier.weight(1f),
                        navController = navController,
                        viewModel = viewModel,
                        session = session,
                        sessions = sessions,
                        activities = activities,
                        sources = sources,
                        onThemeToggle = onThemeToggle
                    )
                }
            }

            val chatHeight = screenHeight * 0.05f
            val isChatVisible = sheetState.currentDetent == Peek || sheetState.currentDetent == Halfway

            // Call the extracted BottomSheet
            IdeBottomSheet(
                sheetState = sheetState,
                viewModel = viewModel,
                peekDetent = Peek,
                halfwayDetent = Halfway,
                chatHeight = chatHeight,
                buildStatus = "", // Not needed
                aiStatus = "", // Not needed
                sessions = emptyList(), // Not needed
                activities = emptyList() // Not needed
            )

            // --- External Chat Input ---
            // This floats ON TOP of the BottomSheet, but is aligned to the screen bottom.
            AnimatedVisibility(
                visible = isChatVisible,
                modifier = Modifier.align(Alignment.BottomCenter)
            ) {
                ContextlessChatInput(
                    modifier = Modifier.height(chatHeight),
                    onSend = { viewModel.sendPrompt(it) }
                )
            }
        }
    }
}
