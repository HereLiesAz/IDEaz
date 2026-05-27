package com.hereliesaz.ideaz.ui

import androidx.compose.ui.graphics.Color
import com.hereliesaz.aznavrail.AzNavHostScope
import com.hereliesaz.aznavrail.bottomsheet.AzSheetController
import com.hereliesaz.aznavrail.model.AzButtonShape
import com.hereliesaz.aznavrail.model.AzDockingSide
import com.hereliesaz.aznavrail.model.AzHeaderIconShape
import com.hereliesaz.aznavrail.model.AzSheetDetent
import com.hereliesaz.ideaz.models.ProjectType

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
    projectType: String?,
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
    onNavigateToMainApp: (String) -> Unit = { navController.navigate(it) }
) {
    // v9 split configuration (azConfig + azTheme + azAdvanced) per
    // docs/AZNAVRAIL_COMPLETE_GUIDE.md §2. Replaces the legacy single
    // azSettings(...) call.
    azConfig(
        packButtons = true,
        dockingSide = AzDockingSide.LEFT,
    )

    azTheme(
        defaultShape = AzButtonShape.RECTANGLE,
        headerIconShape = AzHeaderIconShape.NONE,
        translucentBackground = Color.Black.copy(alpha = 0.5f),
    )

    azAdvanced(
        // Default to docked. Per the AzNavRail guide, enableRailDragging = true
        // puts the rail in "FAB Mode (detach rail)" — keeping that on by default
        // forced the rail into overlay/floating mode in layouts where docking
        // would have been fine. Call sites that want a draggable FAB can opt in
        // via enableRailDraggingOverride.
        enableRailDragging = enableRailDraggingOverride ?: false,
        onUndock = onUndock,
        onOverlayDrag = onOverlayDrag,
        // Help overlay: azHelpRailItem (below) is the dedicated tap trigger.
        // showHelp is the external override callers can flip if they want to
        // force the overlay open from outside the rail (e.g. a tutorial step).
        helpEnabled = showHelp,
        helpList = ideHelpList,
        onDismissHelp = onDismissHelp,
    )

    azRailItem(id = "project_settings", text = "Project", route = "project_settings", onClick = { onNavigateToMainApp("project_settings") })
    azMenuItem(id = "git",  text = "Git", route = "git", onClick = { onNavigateToMainApp("git") })

    azRailHostItem(
        id = "main",
        text = "IDEaz",
        onClick = { }
    )

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
                sheetController.snapTo(AzSheetDetent.HALF)
            }
        }
    )

    if ((projectType == ProjectType.WEB.name) || (projectType == ProjectType.PWA.name) || (projectType == ProjectType.REACT.name)) {
        azRailSubItem(
            id = "reload",
            hostId = "main",
            text = "Reload",
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
            onClick = {
                handleActionClick {
                    viewModel.triggerWebHardReload()
                }
            }
        )
    }

    if ((projectType == ProjectType.WEB.name) || (projectType == ProjectType.PWA.name) || (projectType == ProjectType.REACT.name)) {
        azRailSubItem(
            id = "deploy",
            hostId = "main",
            text = "Deploy",
            onClick = {
                handleActionClick {
                    viewModel.deployWebProject()
                }
            }
        )
    }

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

    azMenuItem(id = "file_explorer",  text = "Files", route = "file_explorer", onClick = { onNavigateToMainApp("file_explorer") })
    azRailItem(id = "settings", text = "Settings", route = "settings", onClick = { onNavigateToMainApp("settings") })

    // Help overlay trigger. Tapping shows ideHelpList entries for each rail
    // item (and the defaults that AzNavRail computes for items without an
    // explicit entry).
    azHelpRailItem(id = "help", text = "Help")
}
