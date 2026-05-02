package com.hereliesaz.ideaz.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.changedToDown
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import androidx.navigation.compose.rememberNavController
import com.composables.core.SheetDetent
import com.composables.core.rememberBottomSheetState
import kotlinx.coroutines.launch
import com.hereliesaz.ideaz.ui.web.WebProjectHost
import androidx.compose.ui.platform.LocalConfiguration

const val Z_INDEX_WEB_VIEW = 0f
const val Z_INDEX_IDE_CONTENT = 1f
const val Z_INDEX_NAV_RAIL = 100f
const val Z_INDEX_OVERLAY = 200f

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(
    viewModel: MainViewModel,
    onRequestScreenCapture: () -> Unit,
    onThemeToggle: (Boolean) -> Unit
) {
    val navController = rememberNavController()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val config = LocalConfiguration.current
    val screenHeight = config.screenHeightDp.dp

    val supportedDetents = remember {
        listOf(SheetDetent.Hidden, AlmostHidden, Peek, Halfway, FullyExpanded)
    }
    val sheetState = rememberBottomSheetState(
        detents = supportedDetents,
        initialDetent = Halfway
    )

    val isIdeVisible by viewModel.isTargetAppVisible.collectAsState()
    val currentWebUrl by viewModel.currentWebUrl.collectAsState()
    val webReloadTrigger by viewModel.webReloadTrigger.collectAsState()
    val webHardReloadTrigger by viewModel.webHardReloadTrigger.collectAsState()

    val isContextualChatVisible by viewModel.isContextualChatVisible.collectAsState()
    val activeSelectionRect by viewModel.activeSelectionRect.collectAsState()
    val isSelectMode by viewModel.isSelectMode.collectAsState()

    var isPromptPopupVisible by remember { mutableStateOf(false) }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        containerColor = MaterialTheme.colorScheme.background,
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .padding(innerPadding)
                .fillMaxSize()
                .pointerInput(sheetState) {
                    awaitPointerEventScope {
                        while (true) {
                            val event = awaitPointerEvent(PointerEventPass.Initial)
                            val downChange = event.changes.firstOrNull { it.changedToDown() }

                            if (downChange != null) {
                                val sheetHeightFactor = when (sheetState.currentDetent) {
                                    FullyExpanded -> 0.8f
                                    Halfway -> 0.5f
                                    Peek -> 0.2f
                                    AlmostHidden -> 0.01f
                                    else -> 0f
                                }
                                val screenHeightPx = size.height
                                val sheetTopY = screenHeightPx * (1f - sheetHeightFactor)

                                if (downChange.position.y < sheetTopY) {
                                    val targetDetent = when (sheetState.currentDetent) {
                                        FullyExpanded -> Halfway
                                        Halfway -> Peek
                                        Peek -> AlmostHidden
                                        else -> null
                                    }
                                    if (targetDetent != null) {
                                        scope.launch { sheetState.animateTo(targetDetent) }
                                    }
                                }
                            }
                        }
                    }
                }
        ) {

            // LAYER 1: Content (Full Screen)
            Box(modifier = Modifier.fillMaxSize()) {
                if (isIdeVisible) {
                    if (currentWebUrl != null) {
                        // Web Mode: Show WebView
                        currentWebUrl?.let { webUrl ->
                            WebProjectHost(
                                url = webUrl,
                                reloadTrigger = webReloadTrigger,
                                hardReloadTrigger = webHardReloadTrigger,
                                modifier = Modifier.fillMaxSize()
                            )
                        }
                    } else {
                        // Android Mode: Placeholder until Phase 2 rebuilds the host
                        // on top of IdeazOverlayService (System Alert Window overlay).
                        // The previous VirtualDisplay-based AndroidProjectHost was removed
                        // because it required signature-level permissions unavailable to
                        // sideloaded apps. See docs/plans/2026-05-01-phase-0-triage.md.
                        AndroidProjectHostPlaceholder()
                    }
                } else {
                    // IDE Mode: Show Settings/Project screens
                    IdeNavHost(
                        modifier = Modifier.fillMaxSize(),
                        navController = navController,
                        viewModel = viewModel,
                        settingsViewModel = viewModel.settingsViewModel,
                        onThemeToggle = onThemeToggle
                    )
                }
            }

            // LAYER 2: Navigation Rail (Overlay, Aligned Start)
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .align(Alignment.CenterStart)
                    .zIndex(Z_INDEX_NAV_RAIL)
            ) {
                IdeNavRail(
                    navController = navController,
                    viewModel = viewModel,
                    context = context,
                    onShowPromptPopup = {
                        // Show the prompt input dialog
                        isPromptPopupVisible = true
                    },
                    handleActionClick = { it() },
                    isIdeVisible = isIdeVisible,
                    onToggleMode = {
                        if (currentWebUrl != null) {
                            viewModel.toggleSelectMode(!viewModel.isSelectMode.value)
                        }
                    },
                    sheetState = sheetState,
                    scope = scope,
                    onNavigateToMainApp = { route ->
                        viewModel.clearSelection()
                        // Exit App View (Web or Android)
                        viewModel.stateDelegate.setTargetAppVisible(false)
                        navController.navigate(route) {
                            launchSingleTop = true
                            restoreState = true
                        }
                    }
                )
            }

            // LAYER 2.5: Selection Overlay
            if (isSelectMode) {
                Box(modifier = Modifier.fillMaxSize().zIndex(Z_INDEX_OVERLAY)) {
                    SelectionOverlay(
                        onTap = { x, y -> viewModel.handleSelection(android.graphics.Rect(x.toInt(), y.toInt(), x.toInt()+1, y.toInt()+1)) },
                        onDragEnd = { rect -> viewModel.handleSelection(rect) }
                    )
                }
            }

            // LAYER 3: Contextual Chat Overlay
            if (isContextualChatVisible && activeSelectionRect != null) {
                Box(modifier = Modifier.fillMaxSize().zIndex(Z_INDEX_OVERLAY)) {
                    ContextualChatOverlay(
                        rect = activeSelectionRect!!,
                        viewModel = viewModel,
                        onClose = { viewModel.closeContextualChat() }
                    )
                }
            }

            // LAYER 4: Bottom Sheet (Console)
            IdeBottomSheet(
                sheetState = sheetState,
                viewModel = viewModel,
                peekDetent = Peek,
                halfwayDetent = Halfway,
                fullyExpandedDetent = FullyExpanded,
                screenHeight = screenHeight,
                onSendPrompt = { viewModel.sendPrompt(it) }
            )

            if (isPromptPopupVisible) {
                PromptPopup(
                    onDismiss = { isPromptPopupVisible = false },
                    onSubmit = { prompt ->
                        viewModel.sendPrompt(prompt)
                        isPromptPopupVisible = false
                    }
                )
            }
        }
    }
}

/**
 * Placeholder shown when the IDE is in "App View" for an Android-typed project.
 *
 * The previous [com.hereliesaz.ideaz.ui.project.AndroidProjectHost] used
 * `VirtualDisplay` + `ActivityOptions.setLaunchDisplayId` to render the target
 * APK inside the IDE. That approach requires signature-level permissions that
 * sideloaded apps cannot obtain on stock Android, so it was removed in the
 * Phase 0 triage. Phase 2 will rebuild the Android target host on top of
 * `IdeazOverlayService` (System Alert Window overlay).
 */
@Composable
private fun AndroidProjectHostPlaceholder() {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text("Android target host arrives in Phase 2.")
    }
}
