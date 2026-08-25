package com.hereliesaz.ideaz.ui

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.hereliesaz.aznavrail.AzNavHostScope
import com.hereliesaz.aznavrail.bottomsheet.AzSheetController
import com.hereliesaz.aznavrail.model.AzButtonShape
import com.hereliesaz.aznavrail.model.AzDockingSide
import com.hereliesaz.aznavrail.model.AzHeaderIconShape
import com.hereliesaz.aznavrail.model.AzSheetDetent

// Every item's own base/unselected colour. AzNavRail falls back to the rail's
// resolved accent (activeColor, below) for any item that doesn't set its own
// `color` — without this, every item renders in the active-item's yellow
// highlight instead of just the one that's actually active.
private val railItemColor = Color.White

private val ideHelpList: Map<String, String> = mapOf(
    "project_settings" to "Open the project's settings and switches.",
    "git" to "Git status, history, and remote actions.",
    "main" to "IDEaz actions: prompt, build, deploy, mode.",
    "prompt" to "Open the prompt input to instruct the AI.",
    "build" to "Trigger a build and open the console.",
    "reload" to "Soft-reload the PWA preview.",
    "hard_reload" to "Cache-bypassing reload of the PWA preview.",
    "deploy" to "Push the current PWA to its remote host.",
    "mode_toggle" to "Switch between Interact and Select on the live preview.",
    "file_explorer" to "Browse and open files in the project.",
    "settings" to "App-wide settings: theme, API keys, providers.",
    "help" to "This overlay. Tap any card to expand its full description.",
)

fun AzNavHostScope.ideNavRail(
    viewModel: MainViewModel,
    onShowPromptPopup: () -> Unit,
    handleActionClick: (() -> Unit) -> Unit,
    isIdeVisible: Boolean,
    onToggleMode: () -> Unit,
    sheetController: AzSheetController,
    showHelp: Boolean = false,
    onDismissHelp: () -> Unit = {},
    onUndock: (() -> Unit)? = null,
    enableRailDraggingOverride: Boolean? = null,
    onOverlayDrag: ((Float, Float) -> Unit)? = null,
    onNavigateToMainApp: (String) -> Unit = { navController?.navigate(it) }
) {
    // v9 split configuration (azConfig + azTheme + azAdvanced) per
    // docs/AZNAVRAIL_COMPLETE_GUIDE.md §2. Replaces the legacy single
    // azSettings(...) call.
    azConfig(
        packButtons = true,
        dockingSide = AzDockingSide.LEFT,
        railItemWidth = 56.dp,
    )

    azTheme(
        // Borderless: no item carries an outline. AzNavRail never draws the
        // border ring for a borderless shape, active or not, so an item that
        // *should* show an outline while active (the mode_toggle "Select"
        // item below) has to switch its own `shape` per-item instead.
        defaultShape = AzButtonShape.NONE,
        headerIconShape = AzHeaderIconShape.NONE,
        // The app palette is monochrome, so the active rail item is otherwise
        // indistinguishable; give it a distinct accent.
        activeColor = Color(0xFFFFC107),
        translucentBackground = Color.Black.copy(alpha = 0.5f),
    )

    azAdvanced(
        // In-app FAB mode is the intended default: with IdeazOverlayService gone,
        // the rail itself is the floating in-app control that sits over the live
        // preview and can be dragged out of the way — not a fixed docked strip.
        // Callers can still force docking via enableRailDraggingOverride = false.
        enableRailDragging = enableRailDraggingOverride ?: true,
        onUndock = onUndock,
        onOverlayDrag = onOverlayDrag,
        // Help overlay: azHelpRailItem (below) is the dedicated tap trigger.
        // showHelp is the external override callers can flip if they want to
        // force the overlay open from outside the rail (e.g. a tutorial step).
        helpEnabled = showHelp,
        helpList = ideHelpList,
        onDismissHelp = onDismissHelp,
    )

    azRailItem(id = "project_settings", text = "Project", route = "project_settings", color = railItemColor, onClick = { onNavigateToMainApp("project_settings") })
    azMenuItem(id = "git",  text = "Git", route = "git", color = railItemColor, onClick = { onNavigateToMainApp("git") })

    azRailHostItem(
        id = "main",
        text = "IDEaz",
        color = railItemColor,
        onClick = { }
    )

    azRailSubItem(
        id = "prompt",
        hostId = "main",
        text = "Prompt",
        color = railItemColor,
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
        color = railItemColor,
        onClick = {
            handleActionClick {
                // Verify the project has an entry point and show the preview.
                // Previously this only opened the sheet and did nothing else.
                viewModel.openPreview()
                sheetController.snapTo(AzSheetDetent.HALF)
            }
        }
    )

    azRailSubItem(
        id = "reload",
        hostId = "main",
        text = "Reload",
        color = railItemColor,
        onClick = {
            handleActionClick {
                viewModel.triggerWebReload()
            }
        }
    )
    azRailSubItem(
        id = "hard_reload",
        hostId = "main",
        text = "Hard Reload",
        color = railItemColor,
        onClick = {
            handleActionClick {
                viewModel.triggerWebHardReload()
            }
        }
    )

    azRailSubItem(
        id = "deploy",
        hostId = "main",
        text = "Deploy",
        color = railItemColor,
        onClick = {
            handleActionClick {
                viewModel.deployWebProject()
            }
        }
    )

    azRailSubToggle(
        id = "mode_toggle",
        hostId = "main",
        isChecked = isIdeVisible,
        toggleOnText = "Interact",
        toggleOffText = "Select",
        color = railItemColor,
        // Borderless while "Interact" (the default mode); switches to a
        // bordered shape only while toggled to "Select" (isIdeVisible ==
        // false), so the outline appears exactly when Select mode is active.
        shape = if (isIdeVisible) AzButtonShape.NONE else AzButtonShape.RECTANGLE,
        onClick = {
            handleActionClick {
                onToggleMode()
            }
        }
    )

    azMenuItem(id = "file_explorer",  text = "Files", route = "file_explorer", color = railItemColor, onClick = { onNavigateToMainApp("file_explorer") })
    azRailItem(id = "settings", text = "Settings", route = "settings", color = railItemColor, onClick = { onNavigateToMainApp("settings") })

    // Help overlay trigger. Tapping shows ideHelpList entries for each rail
    // item (and the defaults that AzNavRail computes for items without an
    // explicit entry).
    azHelpRailItem(id = "help", text = "Help", color = railItemColor)
}
