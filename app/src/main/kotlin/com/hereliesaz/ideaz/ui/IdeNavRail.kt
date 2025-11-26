package com.hereliesaz.ideaz.ui

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import com.hereliesaz.aznavrail.AzNavRail
import com.hereliesaz.aznavrail.model.AzButtonShape
import com.hereliesaz.ideaz.api.Activity

import com.composables.core.BottomSheetState
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
    scope: CoroutineScope
) {
    AzNavRail(navController = navController) {
        azSettings(
            // displayAppNameInHeader = true, // Set to true to display the app name instead of the icon
            packRailButtons = true,
            defaultShape = AzButtonShape.RECTANGLE,
            enableRailDragging = !isIdeVisible
        )
        azRailItem(id = "project_settings", text = "Project", onClick = { navController.navigate("project_settings") })

        azRailHostItem(id = "tools", text = "Tools", onClick = {})
        azRailSubItem(id = "file_explorer", hostId = "tools", text = "File Explorer", onClick = { navController.navigate("file_explorer") })
        azRailSubItem(id = "git", hostId = "tools", text = "Git", onClick = { navController.navigate("git") })
        azRailSubItem(id = "dependencies", hostId = "tools", text = "Dependencies", onClick = { navController.navigate("dependencies") })

        // MODIFIED: Renamed "Status" to "IDE"
        azRailHostItem(id = "main", text = "IDE", onClick = { handleActionClick { navController.navigate("main") } })
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

        azRailItem(id = "settings", text = "Settings", onClick = { navController.navigate("settings") })
    }
}