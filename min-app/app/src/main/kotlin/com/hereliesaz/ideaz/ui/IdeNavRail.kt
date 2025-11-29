package com.hereliesaz.ideaz.ui

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import com.composables.core.BottomSheetState
import com.hereliesaz.aznavrail.AzNavRail
import com.hereliesaz.aznavrail.model.AzButtonShape
import com.hereliesaz.aznavrail.model.AzHeaderIconShape
import com.hereliesaz.ideaz.utils.BubbleUtils
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
    enableRailDraggingOverride: Boolean? = null
) {
    AzNavRail(
        navController = navController,
        initiallyExpanded = initiallyExpanded
    ) {
        azSettings(
            packRailButtons = true,
            defaultShape = AzButtonShape.RECTANGLE,
            enableRailDragging = true,
            onUndock = onUndock ?: { BubbleUtils.createBubbleNotification(context) },
            headerIconShape = AzHeaderIconShape.NONE,
        )

        azRailItem(id = "project_settings", text = "Project", onClick = { navController.navigate("project_settings") })
        azMenuItem(id = "git",  text = "Git", onClick = { navController.navigate("git") })
        // azMenuItem(id = "libraries",  text = "Libs", onClick = { navController.navigate("libraries") })

        azRailHostItem(id = "main", text = "IDEaz", onClick = { handleActionClick { navController.navigate("main") } })

        azRailSubItem(id = "prompt", hostId = "main", text = "Prompt", onClick = { handleActionClick { onShowPromptPopup() } })

        azRailSubItem(id = "build", hostId = "main", text = "Build", onClick = {
            handleActionClick {
                navController.navigate("build")
                scope.launch {
                    sheetState.animateTo(Halfway)
                }
            }
        })

        azRailSubToggle(
            id = "mode_toggle",
            hostId = "main",
            isChecked = isIdeVisible,
            toggleOnText = "Interact",
            toggleOffText = "Select",
            shape = AzButtonShape.NONE,
            onClick = {
                handleActionClick {
                    onLaunchOverlay()
                }
            }
        )

        // azMenuItem(id = "file_explorer",  text = "Files", onClick = { navController.navigate("file_explorer") })
        azRailItem(id = "settings", text = "Settings", onClick = { navController.navigate("settings") })
    }
}
