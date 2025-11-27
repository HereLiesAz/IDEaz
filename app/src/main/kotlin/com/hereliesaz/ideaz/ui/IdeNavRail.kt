package com.hereliesaz.ideaz.ui

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import com.hereliesaz.aznavrail.AzNavRail
import com.hereliesaz.aznavrail.model.AzButtonShape
import com.hereliesaz.ideaz.api.Activity

import com.composables.core.BottomSheetState
import com.hereliesaz.aznavrail.model.AzHeaderIconShape
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

@Composable
fun IdeNavRail(
    navController: NavHostController,
    viewModel: MainViewModel,
    context: Context,
    // MODIFIED: Removed status variables
    onShowPromptPopup: () -> Unit,
    handleActionClick: (() -> Unit) -> Unit,
    isIdeVisible: Boolean,
    onModeToggleClick: () -> Unit, // New click handler for the toggle
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
            // displayAppNameInHeader = true, // Set to true to display the app name instead of the icon
            packRailButtons = true,
            defaultShape = AzButtonShape.RECTANGLE,
            enableRailDragging = enableRailDraggingOverride ?: !isIdeVisible,
            headerIconShape = AzHeaderIconShape.NONE,
            onUndock = onUndock
        )
        azRailItem(id = "project_settings", text = "Project", onClick = { navController.navigate("project_settings") })

        azMenuItem(id = "git",  text = "Git", onClick = { navController.navigate("git") })
        azMenuItem(id = "libraries",  text = "Libs", onClick = { navController.navigate("libraries") })

        // MODIFIED: Renamed "Status" to "IDE"
        azRailHostItem(id = "main", text = "IDEaz", onClick = { handleActionClick { navController.navigate("main") } })
        azRailSubItem(id = "prompt", hostId = "main", text = "Prompt", onClick = { handleActionClick {onShowPromptPopup()} })
        azRailSubItem(id = "build", hostId = "main", text = "Build", onClick = {
            handleActionClick {
                navController.navigate("build")
                scope.launch {
                    sheetState.animateTo(Halfway)
                }
            }
        })

        // MODIFIED: Changed from azMenuToggle to azRailSubToggle and nested it under "main"
        azRailSubToggle(
            id = "mode_toggle",
            hostId = "main",
            isChecked = isIdeVisible, // isIdeVisible is true when sheet is up (Selection Mode)
            toggleOnText = "Interact", // Button text for Selection Mode
            toggleOffText = "Select",   // Button text for Interaction Mode
            shape = AzButtonShape.NONE,
            onClick = {
                handleActionClick {
                    onModeToggleClick() // Call the lambda from MainScreen
                }
            }
        )
        azMenuItem(id = "file_explorer",  text = "Files", onClick = { navController.navigate("file_explorer") })

        azRailItem(id = "settings", text = "Settings", onClick = { navController.navigate("settings") })
    }
}