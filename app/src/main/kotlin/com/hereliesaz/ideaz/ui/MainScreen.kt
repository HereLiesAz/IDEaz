package com.hereliesaz.ideaz.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
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
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.composables.core.SheetDetent
import com.composables.core.rememberBottomSheetState
import com.hereliesaz.aznavrail.AzNavRail
import com.hereliesaz.ideaz.ui.ProjectSettingsScreen
import com.hereliesaz.ideaz.ui.SettingsScreen
import kotlinx.coroutines.launch

// 1. Define custom detents
private val AlmostHidden by lazy { SheetDetent("almost_hidden") { _, _ -> 6.dp } }
private val Peek = SheetDetent("peek") { containerHeight, _ -> containerHeight * 0.2f }
private val Halfway = SheetDetent("halfway") { containerHeight, _ -> containerHeight * 0.5f }


@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(viewModel: MainViewModel) {
    // val buildLog by viewModel.buildLog.collectAsState() // This is now handled by the bottom sheet
    // val buildStatus by viewModel.buildStatus.collectAsState() // This is now IN the buildLog
    // val aiStatus by viewModel.aiStatus.collectAsState() // This is now IN the buildLog
    val session by viewModel.session.collectAsState()
    val sessions by viewModel.sessions.collectAsState()
    val activities by viewModel.activities.collectAsState()
    val sources by viewModel.sources.collectAsState()
    val context = LocalContext.current
    var showPromptPopup by remember{ mutableStateOf(false) } // This is for the OLD popup
    val navController = rememberNavController()
    val scope = rememberCoroutineScope()

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

    // This is now the single source of truth for the mode.
    // isIdeVisible == true -> "Selection Mode"
    // isIdeVisible == false -> "Interaction Mode"
    val isIdeVisible = sheetState.currentDetent != AlmostHidden || isOnSettings || isOnProjectSettings
    // --- End Visibility Logic ---

    // --- Tie Inspection State to Sheet State ---
    LaunchedEffect(sheetState.currentDetent) {
        if (sheetState.currentDetent == AlmostHidden) {
            // "Interaction Mode"
            viewModel.stopInspection(context)
        } else {
            // "Selection Mode"
            viewModel.startInspection(context)
        }
    }

    // Set background color based on sheet state
    val containerColor = Color.Transparent

    // Helper function to navigate away from settings when an action is taken
    val handleActionClick = { action: () -> Unit ->
        if (isOnSettings || isOnProjectSettings) {
            navController.navigate("main")
        }
        action()
    }

    // --- New: Click handler for the mode toggle button ---
    // MODIFIED: Explicitly typed as () -> Unit to fix the build error.
    val onModeToggleClick: () -> Unit = {
        scope.launch {
            if (isIdeVisible) {
                // We are in Selection Mode, switch to Interaction Mode
                sheetState.animateTo(AlmostHidden)
            } else {
                // We are in Interaction Mode, switch to Selection Mode
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

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        containerColor = containerColor,
    ) { innerPadding ->

        // Use a Box to layer the IDE content and the BottomSheet
        Box(modifier = Modifier.padding(innerPadding).fillMaxSize()) {
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    // MODIFIED: The content background is now applied here,
                    // so the Scaffold can remain transparent.
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
                    // buildStatus and activities no longer needed here
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
                        sources = sources
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
                // MODIFIED: No longer passing status vars
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