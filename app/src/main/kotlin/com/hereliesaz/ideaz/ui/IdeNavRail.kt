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
    isInspecting: Boolean,
    buildStatus: String,
    activities: List<Activity>,
    onInspectToggle: (Boolean) -> Unit,
    onShowPromptPopup: () -> Unit,
    handleActionClick: (() -> Unit) -> Unit
) {
    AzNavRail(navController = navController) {
        azSettings(
            // displayAppNameInHeader = true, // Set to true to display the app name instead of the icon
            packRailButtons = true,
            defaultShape = AzButtonShape.RECTANGLE
        )
        azRailItem(id = "project_settings", text = "Project", onClick = { navController.navigate("project_settings") })

        azRailHostItem(id = "main", text = "Status", onClick = { handleActionClick { navController.navigate("main") } })
        azRailSubItem(id = "prompt", hostId = "main", text = "Prompt", onClick = { handleActionClick {onShowPromptPopup()} })
        azRailSubItem(id = "build", hostId = "main", text = "Build", onClick = { handleActionClick { viewModel.startBuild(context) } })

        azMenuToggle(
            id = "inspect",
            isChecked = isInspecting,
            toggleOnText = "Stop",
            toggleOffText = "Inspect",
            onClick = {
                handleActionClick {
                    val newInspectState = !isInspecting
                    onInspectToggle(newInspectState) // Update the state
                    if (newInspectState) {
                        viewModel.startInspection(context)
                    } else {
                        viewModel.stopInspection(context)
                    }
                }
            }
        )

        azRailItem(id = "settings", text = "Settings", onClick = { navController.navigate("settings") })
    }
}