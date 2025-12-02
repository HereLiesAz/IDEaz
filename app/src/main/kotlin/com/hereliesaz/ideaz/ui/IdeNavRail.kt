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
import com.hereliesaz.ideaz.utils.BubbleUtils
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
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
    enableRailDraggingOverride: Boolean? = null,
    isLocalBuildEnabled: Boolean = false, // NEW PARAMETER
    isBubbleMode: Boolean = false,
    onNavigateToMainApp: (String) -> Unit = { navController.navigate(it) }
) {
    AzNavRail(
        navController = navController,
        initiallyExpanded = initiallyExpanded
    ) {
        azSettings(
            packRailButtons = true,
            defaultShape = AzButtonShape.RECTANGLE,
            enableRailDragging = true,
            onUndock = onUndock,
            headerIconShape = AzHeaderIconShape.NONE,
            bubbleMode = isBubbleMode
        )

        // 1. Project
        azRailItem(id = "project_settings", text = "Project", onClick = { onNavigateToMainApp("project_settings") })

        // 2. Git
        azMenuItem(id = "git",  text = "Git", onClick = { navController.navigate("git") })



        // 3. Libs - GATED
        // Local dependency management only makes sense if we are building locally.
        if (isLocalBuildEnabled) {
            azMenuItem(id = "libraries", text = "Libs", onClick = { navController.navigate("libraries") })
        }

        // 4. IDEaz (Host)
        azRailHostItem(id = "main", text = "IDEaz", onClick = { handleActionClick { navController.navigate("main") } })

        // 4a. Prompt (Sub)
        azRailSubItem(id = "prompt", hostId = "main", text = "Prompt", onClick = { handleActionClick { onShowPromptPopup() } })

        // 4b. Build (Sub)
        // We keep this visible because it serves as the "Log Console" view.
        // However, if local build is disabled, clicking this to "Start Build" via a UI trigger
        // inside the screen (if we added one) would fail.
        // The nav rail item just opens the log/bottom sheet.
        azRailSubItem(id = "build", hostId = "main", text = "Logs", onClick = {
            handleActionClick {
                navController.navigate("build")
                scope.launch {
                    sheetState.animateTo(Halfway)
                }
            }
        })

        // 4c. Mode Toggle (Sub Toggle)
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

        // 5. Files
        azMenuItem(id = "file_explorer",  text = "Files", onClick = { navController.navigate("file_explorer") })

        // 6. Settings
        azRailItem(id = "settings", text = "Settings", onClick = { onNavigateToMainApp("settings") })
    }
}