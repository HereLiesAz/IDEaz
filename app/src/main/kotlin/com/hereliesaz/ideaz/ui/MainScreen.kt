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
import com.hereliesaz.ideaz.utils.BubbleUtils
import com.hereliesaz.ideaz.models.ProjectType
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

    val pendingRoute by viewModel.pendingRoute.collectAsState()

    LaunchedEffect(pendingRoute) {
        pendingRoute?.let {
            navController.navigate(it)
            viewModel.setPendingRoute(null)
        }
    }

    val showCancelDialog by viewModel.showCancelDialog.collectAsState()
    val isTargetAppVisible by viewModel.isTargetAppVisible.collectAsState()

    // --- OBSERVE LOCAL BUILD SETTING ---
    // This allows the UI to react instantly when the user toggles the switch in Settings.
    var isLocalBuildEnabled by remember {
        mutableStateOf(viewModel.settingsViewModel.isLocalBuildEnabled())
    }

    // We can't easily observe SharedPreferences directly in Compose without a wrapper,
    // but since we are toggling it in the same app session, we can rely on screen recomposition
    // or a more robust flow. For now, let's assume Settings updates trigger a recomposition
    // or we can add a flow in SettingsViewModel if needed.
    // Ideally, SettingsViewModel exposes this as a Flow.
    // Let's assume for this MVP we re-read it on recomposition or navigation.
    // To be safe, let's check it whenever the route changes.
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route

    val projectTypeStr by viewModel.settingsViewModel.projectType.collectAsState()
    val projectType = ProjectType.fromString(projectTypeStr)

    // Bubble Mode Logic
    val isBubbleMode = when {
        currentRoute == "project_settings" || currentRoute == "settings" -> false
        projectType == ProjectType.WEB && (currentRoute == "main" || currentRoute == null) -> false
        else -> true
    }

    LaunchedEffect(currentRoute) {
        isLocalBuildEnabled = viewModel.settingsViewModel.isLocalBuildEnabled()
    }

    // --- Bottom Sheet State (Dashboard Console) ---
    val configuration = LocalConfiguration.current
    val screenHeight = configuration.screenHeightDp.dp

    val sheetState = rememberBottomSheetState(
        initialDetent = Peek,
        detents = listOf(AlmostHidden, Peek, Halfway)
    )

    // --- Visibility Logic ---
    val isSelectMode by viewModel.isSelectMode.collectAsState()
    // Dashboard is visible if NOT in select mode (and not hidden by target app signal which is deprecated/handled by select mode)
    val isDashboardVisible = !isSelectMode

    // The "main" and "build" routes are considered "Overlay Modes" where we want to see the target app.
    val isOverlayRoute = currentRoute == "main" || currentRoute == "build"
    val isBottomSheetVisible = isOverlayRoute

    // Auto-launch target app if entering overlay mode
    LaunchedEffect(isOverlayRoute) {
        if (isOverlayRoute && !isTargetAppVisible) {
            viewModel.launchTargetApp(context)
        }
    }

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
        containerColor = if (isSelectMode || isOverlayRoute) Color.Transparent else MaterialTheme.colorScheme.background,
    ) { innerPadding ->

        Box(modifier = Modifier.fillMaxSize()) {

            // Selection Overlay (Behind Rail, Above Transparent Background)
            // Placed outside padding to ensure global coordinates match screen coordinates
            if (isSelectMode) {
                SelectionOverlay(
                    modifier = Modifier.fillMaxSize(),
                    onTap = { x, y -> viewModel.handleOverlayTap(x, y) },
                    onDragEnd = { rect -> viewModel.handleOverlayDragEnd(rect) }
                )
            }

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
                            viewModel.toggleSelectMode(!isSelectMode)
                        },
                        sheetState = sheetState,
                        scope = scope,
                        onUndock = { BubbleUtils.createBubbleNotification(context) },
                        isLocalBuildEnabled = isLocalBuildEnabled, // PASSING THE FLAG
                        isBubbleMode = isBubbleMode
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
}