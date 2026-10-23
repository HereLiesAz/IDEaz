package com.hereliesaz.ideaz.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.navigation.compose.currentBackStackEntryAsState
import com.hereliesaz.ideaz.ui.theme.railActiveColor
import com.hereliesaz.ideaz.ui.theme.railItemColor
import com.hereliesaz.ideaz.ui.theme.railScrimColor
import com.hereliesaz.ideaz.ui.web.WebProjectHost
import androidx.compose.ui.platform.LocalConfiguration
import com.hereliesaz.aznavrail.AzHostActivityLayout
import com.hereliesaz.aznavrail.bottomsheet.rememberAzSheetController
import com.hereliesaz.aznavrail.model.AzSheetDetent
import com.hereliesaz.aznavrail.AzButton
import com.hereliesaz.aznavrail.model.AzButtonShape
import kotlinx.coroutines.launch

const val Z_INDEX_WEB_VIEW = 0f
const val Z_INDEX_OVERLAY = 200f

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(viewModel: MainViewModel) {
    val navController = rememberNavController()
    val config = LocalConfiguration.current
    val screenHeight = config.screenHeightDp.dp

    val currentDestination by navController.currentBackStackEntryAsState()

    val sheetController = rememberAzSheetController(initial = AzSheetDetent.PEEK)

    val isIdeVisible by viewModel.isTargetAppVisible.collectAsState()
    val currentWebUrl by viewModel.currentWebUrl.collectAsState()
    val currentWebProjectDir by viewModel.currentWebProjectDir.collectAsState()
    val webReloadTrigger by viewModel.webReloadTrigger.collectAsState()
    val webHardReloadTrigger by viewModel.webHardReloadTrigger.collectAsState()

    val isContextualChatVisible by viewModel.isContextualChatVisible.collectAsState()
    val isSelectMode by viewModel.isSelectMode.collectAsState()

    var isPromptPopupVisible by remember { mutableStateOf(false) }
    var showHelp by remember { mutableStateOf(false) }

    val settingsViewModel = viewModel.settingsViewModel
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    val deployConfirmationVisible by viewModel.deployConfirmationVisible.collectAsState()
    val deployResult by viewModel.deployResult.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(Unit) {
        // "Open Settings" from a chat error message.
        viewModel.openSettingsRequests.collect {
            viewModel.clearSelection()
            viewModel.stateDelegate.setTargetAppVisible(false)
            navController.navigate("settings") {
                launchSingleTop = true
                restoreState = true
            }
        }
    }

    LaunchedEffect(deployResult?.id) {
        val result = deployResult ?: return@LaunchedEffect
        val outcome = snackbarHostState.showSnackbar(
            message = result.message,
            actionLabel = if (result.url != null) "Open" else null,
            withDismissAction = true,
            duration = if (result.url != null) SnackbarDuration.Long else SnackbarDuration.Indefinite,
        )
        if (outcome == SnackbarResult.ActionPerformed && result.url != null) {
            runCatching {
                context.startActivity(
                    android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse(result.url))
                        .addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                )
            }
        }
        viewModel.consumeDeployResult(result.id)
    }

    if (deployConfirmationVisible) {
        AlertDialog(
            onDismissRequest = { viewModel.dismissDeployConfirmation() },
            title = { Text("Deploy this project?") },
            text = {
                Text(
                    "Deploy will create a private GitHub repository for this project if it has none, " +
                        "push your changes to it, and publish the built site to a public web address. " +
                        "It sends your GitHub token, the repository name and the commit to the IDEaz " +
                        "build service. Only repositories owned by HereLiesAz can be published today; " +
                        "for any other account the push still happens and publishing is refused."
                )
            },
            confirmButton = {
                AzButton(onClick = { viewModel.confirmDeploy() }, text = "Deploy", shape = AzButtonShape.RECTANGLE)
            },
            dismissButton = {
                AzButton(onClick = { viewModel.dismissDeployConfirmation() }, text = "Cancel", shape = AzButtonShape.RECTANGLE)
            },
        )
    }

    // Resolved here: the rail DSL scope below is not composable.
    val railItem = railItemColor()
    val railActive = railActiveColor()
    val railScrim = railScrimColor()

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        snackbarHost = { SnackbarHost(snackbarHostState) },
        containerColor = MaterialTheme.colorScheme.background,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
    ) { innerPadding ->
        AzHostActivityLayout(
            navController = navController,
            modifier = Modifier
                .padding(innerPadding)
                .fillMaxSize(),
            currentDestination = currentDestination?.destination?.route,
            isLandscape = config.orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE
        ) {
            ideNavRail(
                viewModel = viewModel,
                onShowPromptPopup = {
                    isPromptPopupVisible = true
                },
                handleActionClick = { it() },
                isIdeVisible = isIdeVisible,
                onToggleMode = {
                    if (currentWebUrl != null) {
                        viewModel.toggleSelectMode(!viewModel.isSelectMode.value)
                    }
                },
                sheetController = sheetController,
                activeRoute = currentDestination?.destination?.route,
                itemColor = railItem,
                activeColor = railActive,
                scrimColor = railScrim,
                showHelp = showHelp,
                onDismissHelp = { showHelp = false },
                onNavigateToMainApp = { route ->
                    viewModel.clearSelection()
                    viewModel.stateDelegate.setTargetAppVisible(false)
                    navController.navigate(route) {
                        launchSingleTop = true
                        restoreState = true
                    }
                }
            )

            // The Box stacks three siblings (content layer, contextual-chat
            // overlay, prompt popup) — it draws nothing itself but is the only
            // way to layer composables inside an onscreen { } slot.
            onscreen {
                Box(modifier = Modifier.fillMaxSize()) {
                    // LAYER 1: Content
                    if (isIdeVisible) {
                        if (currentWebUrl != null) {
                            currentWebUrl?.let { webUrl ->
                                WebProjectHost(
                                    url = webUrl,
                                    projectDir = currentWebProjectDir,
                                    reloadTrigger = webReloadTrigger,
                                    hardReloadTrigger = webHardReloadTrigger,
                                    selectMode = isSelectMode,
                                    onElementContext = { viewModel.handleWebElementContext(it) },
                                    modifier = Modifier.fillMaxSize()
                                )
                            }
                        } else {
                            // App View with no URL means the project has no entry
                            // point. launchTargetApp() refuses to get here and says
                            // so; this branch is the belt to that suspenders.
                            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                Text("Nothing to preview: this project has no index.html.")
                            }
                        }
                    } else {
                        IdeNavHost(
                            modifier = Modifier.fillMaxSize(),
                            navController = navController,
                            viewModel = viewModel,
                            settingsViewModel = viewModel.settingsViewModel,
                            // The live theme comes from settingsViewModel.themeMode,
                            // which MainActivity collects at the root; this callback
                            // has always been a no-op and stays one.
                            onThemeToggle = {},
                        )
                    }

                    // LAYER 3: Contextual Chat Overlay
                    if (isContextualChatVisible) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .zIndex(Z_INDEX_OVERLAY),
                            contentAlignment = androidx.compose.ui.Alignment.BottomCenter,
                        ) {
                            ContextualChatOverlay(
                                viewModel = viewModel,
                                onClose = { viewModel.closeContextualChat() }
                            )
                        }
                    }

                    if (isPromptPopupVisible) {
                        PromptPopup(
                            onDismiss = { isPromptPopupVisible = false },
                            viewModel = viewModel,
                        )
                    }
                }
            }

            // Selection overlay lives in its own onscreen layer so AzNavRail's
            // safe-zone padding applies natively — the rail strip and system
            // bars stay reachable while drag-to-select is active.
            onscreen {
                if (isSelectMode) {
                    // A tap is the whole gesture. The old drag-to-select produced a
                    // rect that was immediately collapsed to its centre point anyway,
                    // and a horizontal drag was silently discarded because the
                    // threshold required movement on both axes.
                    SelectionOverlay(onTap = { x, y -> viewModel.handleSelection(x, y) })
                }
            }

            // Console bottom sheet uses the AzNavHostScope DSL form. Per
            // AZNAVRAIL_COMPLETE_GUIDE.md §10.2 it draws at zIndex(2f) above
            // the rail's onscreen layers, spans full screen width edge-to-edge,
            // and extends to the bottom of the screen so the HIDDEN-detent
            // strip remains touch-targetable from the system-nav-bar area.
            // AzNavRail's expanded menu still composes above the sheet,
            // preserving: system nav > AzNavRail menu > bottom sheet >
            // onscreen layers.
            azBottomSheet(controller = sheetController) {
                IdeBottomSheet(
                    controller = sheetController,
                    viewModel = viewModel,
                    screenHeight = screenHeight,
                )
            }
        }
    }
}

