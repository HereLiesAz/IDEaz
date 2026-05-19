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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import androidx.navigation.compose.rememberNavController
import androidx.navigation.compose.currentBackStackEntryAsState
import com.hereliesaz.ideaz.ui.web.WebProjectHost
import androidx.compose.ui.platform.LocalConfiguration
import com.hereliesaz.aznavrail.AzHostActivityLayout
import com.hereliesaz.aznavrail.bottomsheet.rememberAzSheetController
import com.hereliesaz.aznavrail.model.AzSheetDetent

const val Z_INDEX_WEB_VIEW = 0f
const val Z_INDEX_OVERLAY = 200f

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(
    viewModel: MainViewModel,
    onRequestScreenCapture: () -> Unit,
    onThemeToggle: (Boolean) -> Unit
) {
    val navController = rememberNavController()
    val config = LocalConfiguration.current
    val screenHeight = config.screenHeightDp.dp

    val currentDestination by navController.currentBackStackEntryAsState()

    val sheetController = rememberAzSheetController(initial = AzSheetDetent.PEEK)

    val isIdeVisible by viewModel.isTargetAppVisible.collectAsState()
    val projectType by viewModel.settingsViewModel.projectType.collectAsState()
    val currentWebUrl by viewModel.currentWebUrl.collectAsState()
    val webReloadTrigger by viewModel.webReloadTrigger.collectAsState()
    val webHardReloadTrigger by viewModel.webHardReloadTrigger.collectAsState()

    val isContextualChatVisible by viewModel.isContextualChatVisible.collectAsState()
    val activeSelectionRect by viewModel.activeSelectionRect.collectAsState()
    val isSelectMode by viewModel.isSelectMode.collectAsState()

    var isPromptPopupVisible by remember { mutableStateOf(false) }
    var showHelp by remember { mutableStateOf(false) }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        containerColor = MaterialTheme.colorScheme.background,
    ) { innerPadding ->
        AzHostActivityLayout(
            navController = navController,
            modifier = Modifier
                .padding(innerPadding)
                .fillMaxSize(),
            currentDestination = currentDestination?.destination?.route,
            isLandscape = config.orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE
        ) {
            ideNavRail(
                viewModel = viewModel,
                projectType = projectType,
                onShowPromptPopup = {
                    isPromptPopupVisible = true
                },
                handleActionClick = { it() },
                isIdeVisible = isIdeVisible,
                onToggleMode = {
                    if (currentWebUrl != null) {
                        viewModel.toggleSelectMode(!viewModel.isSelectMode.value)
                    }
                },
                sheetController = sheetController,
                showHelp = showHelp,
                onDismissHelp = { showHelp = false },
                onNavigateToMainApp = { route ->
                    viewModel.clearSelection()
                    viewModel.stateDelegate.setTargetAppVisible(false)
                    navController.navigate(route) {
                        launchSingleTop = true
                        restoreState = true
                    }
                }
            )

            // The Box stacks three siblings (content layer, contextual-chat
            // overlay, prompt popup) — it draws nothing itself but is the only
            // way to layer composables inside an onscreen { } slot.
            onscreen {
                Box(modifier = Modifier.fillMaxSize()) {
                    // LAYER 1: Content
                    if (isIdeVisible) {
                        if (currentWebUrl != null) {
                            currentWebUrl?.let { webUrl ->
                                WebProjectHost(
                                    url = webUrl,
                                    reloadTrigger = webReloadTrigger,
                                    hardReloadTrigger = webHardReloadTrigger,
                                    selectMode = isSelectMode,
                                    onElementContext = { viewModel.handleWebElementContext(it) },
                                    modifier = Modifier.fillMaxSize()
                                )
                            }
                        } else {
                            // Placeholder until Phase 2 rebuilds the Android target host
                            // on top of IdeazOverlayService (System Alert Window overlay).
                            // The previous VirtualDisplay-based AndroidProjectHost was
                            // removed because it required signature-level permissions
                            // unavailable to sideloaded apps. See
                            // docs/plans/2026-05-01-phase-0-triage.md.
                            AndroidProjectHostPlaceholder()
                        }
                    } else {
                        IdeNavHost(
                            modifier = Modifier.fillMaxSize(),
                            navController = navController,
                            viewModel = viewModel,
                            settingsViewModel = viewModel.settingsViewModel,
                            onThemeToggle = onThemeToggle
                        )
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

            // Selection overlay lives in its own onscreen layer so AzNavRail's
            // safe-zone padding applies natively — the rail strip and system
            // bars stay reachable while drag-to-select is active.
            onscreen {
                if (isSelectMode) {
                    SelectionOverlay(
                        onTap = { x, y -> viewModel.handleSelection(android.graphics.Rect(x.toInt(), y.toInt(), x.toInt()+1, y.toInt()+1)) },
                        onDragEnd = { rect -> viewModel.handleSelection(rect) }
                    )
                }
            }

            // Console bottom sheet uses the AzNavHostScope DSL form. Per
            // AZNAVRAIL_COMPLETE_GUIDE.md §10.2 it draws at zIndex(2f) above
            // the rail's onscreen layers, spans full screen width edge-to-edge,
            // and extends to the bottom of the screen so the HIDDEN-detent
            // strip remains touch-targetable from the system-nav-bar area.
            // AzNavRail's expanded menu still composes above the sheet,
            // preserving: system nav > AzNavRail menu > bottom sheet >
            // onscreen layers.
            azBottomSheet(controller = sheetController) {
                IdeBottomSheet(
                    controller = sheetController,
                    viewModel = viewModel,
                    screenHeight = screenHeight,
                    onSendPrompt = { viewModel.sendPrompt(it) }
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
