package com.hereliesaz.ideaz.ui

import com.composables.core.BottomSheetState
import com.hereliesaz.aznavrail.AzNavHostScope
import com.hereliesaz.aznavrail.model.AzButtonShape
import com.hereliesaz.aznavrail.model.AzHeaderIconShape
import com.hereliesaz.ideaz.models.ProjectType
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

fun AzNavHostScope.ideNavRail(
    viewModel: MainViewModel,
    projectType: String?,
    onShowPromptPopup: () -> Unit,
    handleActionClick: (() -> Unit) -> Unit,
    isIdeVisible: Boolean,
    onToggleMode: () -> Unit,
    sheetState: BottomSheetState,
    scope: CoroutineScope,
    onUndock: (() -> Unit)? = null,
    enableRailDraggingOverride: Boolean? = null,
    onOverlayDrag: ((Float, Float) -> Unit)? = null,
    onNavigateToMainApp: (String) -> Unit = { navController.navigate(it) }
) {
    azSettings(
        packRailButtons = true,
        defaultShape = AzButtonShape.RECTANGLE,
        enableRailDragging = enableRailDraggingOverride ?: true,
        onUndock = onUndock,
        onOverlayDrag = onOverlayDrag,
        headerIconShape = AzHeaderIconShape.NONE,
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
                scope.launch {
                    sheetState.animateTo(Halfway)
                }
            }
        }
    )

    if ((projectType == ProjectType.WEB.name) || (projectType == ProjectType.PWA.name)) {
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

    if ((projectType == ProjectType.WEB.name) || (projectType == ProjectType.PWA.name)) {
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
}
