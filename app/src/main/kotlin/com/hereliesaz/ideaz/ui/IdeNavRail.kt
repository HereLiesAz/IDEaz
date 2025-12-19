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
    onToggleMode: () -> Unit,
    sheetState: BottomSheetState,
    scope: CoroutineScope,
    initiallyExpanded: Boolean = false,
    onUndock: (() -> Unit)? = null,
    enableRailDraggingOverride: Boolean? = null, // NEW
    onOverlayDrag: ((Float, Float) -> Unit)? = null, // NEW: Manual drag handler
    isLocalBuildEnabled: Boolean = false,
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
            onOverlayDrag = onOverlayDrag,
            headerIconShape = AzHeaderIconShape.NONE,
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
                // Previously handled launching overlay, now default behavior
            }
        )
        // Only show Prompt option if in IDE Mode (isIdeVisible = true) or if we want it everywhere.
        // The user complained about "contextless prompt popup over the project screen".
        // This implies they triggered it accidentally or it shouldn't be there.
        // However, user might want to prompt from Project Screen too (to control AI).
        // But if it's "contextless", maybe they mean the overlay version.
        // Let's keep it but ensure the popup handles dismissal/focus correctly.
        azRailSubItem(
            id = "prompt",
            hostId = "main",
            text = "Prompt",
            onClick = {
                handleActionClick {
                    onShowPromptPopup()
                }
            }
        )

        azRailSubItem(
            id = "build",
            hostId = "main",
            text = "Build",
            onClick = {
                handleActionClick {
                    scope.launch {
                        sheetState.animateTo(Halfway)
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
                    onToggleMode()
                }
            }
        )

        azMenuItem(id = "file_explorer",  text = "Files", onClick = { onNavigateToMainApp("file_explorer") })
        azRailItem(id = "settings", text = "Settings", onClick = { onNavigateToMainApp("settings") })
    }
}
