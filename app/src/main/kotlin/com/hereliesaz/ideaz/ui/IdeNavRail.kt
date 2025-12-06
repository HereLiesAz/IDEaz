package com.hereliesaz.ideaz.ui

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import com.composables.core.BottomSheetState
import com.hereliesaz.aznavrail.AzNavRail
import com.hereliesaz.aznavrail.model.AzButtonShape
import com.hereliesaz.aznavrail.model.AzHeaderIconShape
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

@Composable
fun IdeNavRail(
    navController: NavHostController,
    viewModel: MainViewModel,
    context: Context,
    onShowPromptPopup: () -> Unit,
    handleActionClick: (() -> Unit) -> Unit,
    isIdeVisible: Boolean,
    onLaunchOverlay: () -> Unit,
    sheetState: BottomSheetState,
    scope: CoroutineScope,
    initiallyExpanded: Boolean = false,
    onUndock: (() -> Unit)? = null,
    enableRailDraggingOverride: Boolean? = null, // NEW
    isLocalBuildEnabled: Boolean = false,
    isBubbleMode: Boolean = false,
    onNavigateToMainApp: (String) -> Unit = { navController.navigate(it) }
) {
    AzNavRail(
        navController = navController,
        initiallyExpanded = initiallyExpanded
    ) {
        azSettings(
            packRailButtons = true,
            defaultShape = AzButtonShape.RECTANGLE,
            enableRailDragging = enableRailDraggingOverride ?: true, // Default true unless override
            onUndock = onUndock,
            headerIconShape = AzHeaderIconShape.NONE,
            bubbleMode = isBubbleMode
        )

        // ... (rest of items unchanged)
        azRailItem(id = "project_settings", text = "Project", onClick = { onNavigateToMainApp("project_settings") })
        azMenuItem(id = "git",  text = "Git", onClick = { onNavigateToMainApp("git") })

        if (isLocalBuildEnabled) {
            azMenuItem(id = "libraries", text = "Libs", onClick = { onNavigateToMainApp("libraries") })
        }

        azRailHostItem(
            id = "main",
            text = "IDEaz",
            onClick = {
                handleActionClick {
                    if (!isBubbleMode) {
                        onLaunchOverlay()
                    }
                    // In overlay, clicking "IDEaz" (header) just keeps us there / does nothing
                }
            }
        )
        azRailSubItem(
            id = "prompt",
            hostId = "main",
            text = "Prompt",
            onClick = {
                handleActionClick {
                    if (!isBubbleMode) {
                        onLaunchOverlay()
                    } else {
                        onShowPromptPopup()
                    }
                }
            }
        )

        azRailSubItem(
            id = "build",
            hostId = "main",
            text = "Build",
            onClick = {
                handleActionClick {
                    if (!isBubbleMode) {
                        onLaunchOverlay()
                    } else {
                        // In overlay, Build opens the bottom sheet
                        scope.launch {
                            sheetState.animateTo(Halfway)
                        }
                    }
                }
            }
        )

        azRailSubToggle(
            id = "mode_toggle",
            hostId = "main",
            isChecked = isIdeVisible,
            toggleOnText = "Interact",
            toggleOffText = "Select",
            shape = AzButtonShape.NONE,
            onClick = {
                handleActionClick {
                    if (!isBubbleMode) {
                        onLaunchOverlay()
                    } else {
                        // In overlay, this toggles mode. We assume the toggle logic
                        // is handled by the component's state or a separate callback if needed,
                        // but `onLaunchOverlay` here seems to be used as a toggle trigger in MainScreen.
                        // If `onLaunchOverlay` is safe to call in bubble (e.g. toggles visibility), keep it.
                        // However, assuming standard behavior:
                        onLaunchOverlay()
                    }
                }
            }
        )

        azMenuItem(id = "file_explorer",  text = "Files", onClick = { onNavigateToMainApp("file_explorer") })
        azRailItem(id = "settings", text = "Settings", onClick = { onNavigateToMainApp("settings") })
    }
}