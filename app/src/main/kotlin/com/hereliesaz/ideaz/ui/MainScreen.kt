package com.hereliesaz.ideaz.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import androidx.navigation.compose.rememberNavController
import com.composables.core.SheetDetent
import com.composables.core.rememberBottomSheetState
import com.hereliesaz.ideaz.ui.web.WebProjectHost
import com.hereliesaz.ideaz.ui.project.AndroidProjectHost
import androidx.compose.ui.platform.LocalConfiguration

const val Z_INDEX_WEB_VIEW = 0f
const val Z_INDEX_IDE_CONTENT = 1f
const val Z_INDEX_NAV_RAIL = 100f
const val Z_INDEX_OVERLAY = 200f

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(
    viewModel: MainViewModel,
    onRequestScreenCapture: () -> Unit,
    onThemeToggle: (Boolean) -> Unit
) {
    val navController = rememberNavController()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val config = LocalConfiguration.current
    val screenHeight = config.screenHeightDp.dp

    val supportedDetents = remember {
        listOf(SheetDetent.Hidden, AlmostHidden, Peek, Halfway)
    }
    val sheetState = rememberBottomSheetState(
        detents = supportedDetents,
        initialDetent = Halfway
    )

    val isIdeVisible by viewModel.isTargetAppVisible.collectAsState()
    val currentWebUrl by viewModel.currentWebUrl.collectAsState()
    val isLocalBuildEnabled = viewModel.settingsViewModel.isLocalBuildEnabled()

    val isContextualChatVisible by viewModel.isContextualChatVisible.collectAsState()
    val activeSelectionRect by viewModel.activeSelectionRect.collectAsState()

    var isPromptPopupVisible by remember { mutableStateOf(false) }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        containerColor = MaterialTheme.colorScheme.background,
    ) { innerPadding ->
        Box(modifier = Modifier.padding(innerPadding).fillMaxSize()) {

            Row(modifier = Modifier.fillMaxSize()) {
                // Navigation Rail
                Box(
                    modifier = Modifier.zIndex(Z_INDEX_NAV_RAIL)
                ) {
                    IdeNavRail(
                        navController = navController,
                        viewModel = viewModel,
                        context = context,
                        onShowPromptPopup = {
                            // Show the prompt input dialog
                            isPromptPopupVisible = true
                        },
                        handleActionClick = { it() },
                        isIdeVisible = isIdeVisible,
                        onToggleMode = {
                            if (currentWebUrl != null) {
                                viewModel.toggleSelectMode(!viewModel.isSelectMode.value)
                            }
                        },
                        sheetState = sheetState,
                        scope = scope,
                        isLocalBuildEnabled = isLocalBuildEnabled,
                        onNavigateToMainApp = { route ->
                            viewModel.clearSelection()
                            // Exit App View (Web or Android)
                            viewModel.stateDelegate.setTargetAppVisible(false)
                            navController.navigate(route) {
                                launchSingleTop = true
                                restoreState = true
                            }
                        }
                    )
                }

                // Content
                Box(modifier = Modifier.weight(1f).fillMaxSize()) {
                    if (isIdeVisible) {
                        if (currentWebUrl != null) {
                            // Web Mode: Show WebView
                            currentWebUrl?.let { webUrl ->
                                WebProjectHost(
                                    url = webUrl,
                                    modifier = Modifier.fillMaxSize()
                                )
                            }
                        } else {
                            // Android Mode: Show Virtual Environment
                            val targetPackage by viewModel.settingsViewModel.targetPackageName.collectAsState()
                            if (targetPackage != null) {
                                AndroidProjectHost(
                                    packageName = targetPackage!!,
                                    modifier = Modifier.fillMaxSize()
                                )
                            }
                        }
                    } else {
                        // IDE Mode: Show Settings/Project screens
                        IdeNavHost(
                            modifier = Modifier.fillMaxSize(),
                            navController = navController,
                            viewModel = viewModel,
                            settingsViewModel = viewModel.settingsViewModel,
                            onThemeToggle = onThemeToggle
                        )
                    }
                }
            }

            // LAYER 3: Contextual Chat Overlay
            if (isContextualChatVisible && activeSelectionRect != null) {
                Box(modifier = Modifier.fillMaxSize().zIndex(Z_INDEX_OVERLAY)) {
                    ContextualChatOverlay(
                        rect = activeSelectionRect!!,
                        viewModel = viewModel,
                        onClose = { viewModel.closeContextualChat() }
                    )
                }
            }

            // LAYER 4: Bottom Sheet (Console)
            if (isIdeVisible) {
                IdeBottomSheet(
                    sheetState = sheetState,
                    viewModel = viewModel,
                    peekDetent = Peek,
                    halfwayDetent = Halfway,
                    screenHeight = screenHeight,
                    onSendPrompt = { viewModel.sendPrompt(it) }
                )
            }

            if (isPromptPopupVisible) {
                PromptPopup(
                    onDismiss = { isPromptPopupVisible = false },
                    onSubmit = { prompt ->
                        viewModel.sendPrompt(prompt)
                        isPromptPopupVisible = false
                    }
                )
            }
        }
    }
}
