package com.hereliesaz.ideaz.ui

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import com.hereliesaz.aznavrail.AzNavRail
import com.hereliesaz.aznavrail.model.AzButtonShape
import com.hereliesaz.ideaz.api.Activity

@Composable
fun IdeNavRail(
    navController: NavHostController,
    viewModel: MainViewModel,
    context: Context,
    // MODIFIED: Removed status variables
    onShowPromptPopup: () -> Unit,
    handleActionClick: (() -> Unit) -> Unit,
    isIdeVisible: Boolean,
    onModeToggleClick: () -> Unit // New click handler for the toggle
) {
    AzNavRail(navController = navController) {
        azSettings(
            // displayAppNameInHeader = true, // Set to true to display the app name instead of the icon
            packRailButtons = true,
            defaultShape = AzButtonShape.RECTANGLE,
            enableRailDragging = !isIdeVisible
        )
        azRailItem(id = "project_settings", text = "Project", onClick = { navController.navigate("project_settings") })

        // MODIFIED: Renamed "Status" to "IDE"
        azRailHostItem(id = "main", text = "IDE", onClick = { handleActionClick { navController.navigate("main") } })
        azRailSubItem(id = "prompt", hostId = "main", text = "Prompt", onClick = { handleActionClick {onShowPromptPopup()} })
        azRailSubItem(id = "build", hostId = "main", text = "Build", onClick = { handleActionClick { viewModel.startBuild(context) } })

        // MODIFIED: Changed from azMenuToggle to azRailSubToggle and nested it under "main"
        azRailSubToggle(
            id = "mode_toggle",
            hostId = "main",
            isChecked = isIdeVisible, // isIdeVisible is true when sheet is up (Selection Mode)
            toggleOnText = "Interact", // Button text for Selection Mode
            toggleOffText = "Select",   // Button text for Interaction Mode
            shape = AzButtonShape.NONE, // Explicitly adding null for the optional shape parameter
            onClick = {
                handleActionClick {
                    onModeToggleClick() // Call the lambda from MainScreen
                }
            }
        )

        azRailItem(id = "settings", text = "Settings", onClick = { navController.navigate("settings") })
    }
}