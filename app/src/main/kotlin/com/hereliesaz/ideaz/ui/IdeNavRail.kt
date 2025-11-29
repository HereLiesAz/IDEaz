package com.hereliesaz.ideaz.ui

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import com.composables.core.BottomSheetState
import com.hereliesaz.aznavrail.AzNavRail
import com.hereliesaz.aznavrail.model.AzButtonShape
import com.hereliesaz.ideaz.api.Activity
import com.hereliesaz.aznavrail.model.AzHeaderIconShape
import com.hereliesaz.ideaz.ui.Halfway
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
            // displayAppNameInHeader = true, // Set to true to display the app name instead of the icon
            packRailButtons = true,
            defaultShape = AzButtonShape.RECTANGLE,
            enableRailDragging = true,
            headerIconShape = AzHeaderIconShape.NONE,
            onUndock = onUndock
        )

        // 1. Project
        azRailItem(id = "project_settings", text = "Project", onClick = { navController.navigate("project_settings") })

        // 2. Git
        azMenuItem(id = "git",  text = "Git", onClick = { navController.navigate("git") })

        // 3. Libs
        azMenuItem(id = "libraries",  text = "Libs", onClick = { navController.navigate("libraries") })

        // 4. IDEaz (Host)
        azRailHostItem(id = "main", text = "IDEaz", onClick = { handleActionClick { navController.navigate("main") } })

        // 4a. Prompt (Sub)
        azRailSubItem(id = "prompt", hostId = "main", text = "Prompt", onClick = { handleActionClick { onShowPromptPopup() } })

        // 4b. Build (Sub)
        azRailSubItem(id = "build", hostId = "main", text = "Build", onClick = {
            handleActionClick {
                navController.navigate("build")
                scope.launch {
                    sheetState.animateTo(Halfway)
                }
            }
        })

        // 4c. Mode Toggle (Sub Toggle)
        // In the Dashboard, we are effectively in "Interact" mode.
        // Clicking this should take us to "Select" mode (The Overlay).
        azRailSubToggle(
            id = "mode_toggle",
            hostId = "main",
            isChecked = isIdeVisible,
            toggleOnText = "Interact",
            toggleOffText = "Select",
            shape = AzButtonShape.NONE,
            onClick = {
                handleActionClick {
                    // Launching overlay minimizes this dashboard
                    onLaunchOverlay()
                }
            }
        )

        // 5. Files
        azMenuItem(id = "file_explorer",  text = "Files", onClick = { navController.navigate("file_explorer") })

        // 6. Settings
        azRailItem(id = "settings", text = "Settings", onClick = { navController.navigate("settings") })
    }
}