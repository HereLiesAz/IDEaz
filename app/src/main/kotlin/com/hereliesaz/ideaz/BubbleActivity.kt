package com.hereliesaz.ideaz

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import androidx.navigation.compose.rememberNavController
import com.composables.core.SheetDetent
import com.composables.core.rememberBottomSheetState
import com.hereliesaz.ideaz.ui.AlmostHidden
import com.hereliesaz.ideaz.ui.ContextualChatOverlay
import com.hereliesaz.ideaz.ui.Halfway
import com.hereliesaz.ideaz.ui.IdeBottomSheet
import com.hereliesaz.ideaz.ui.IdeNavHost
import com.hereliesaz.ideaz.ui.IdeNavRail
import com.hereliesaz.ideaz.ui.MainViewModel
import com.hereliesaz.ideaz.ui.MainViewModelFactory
import com.hereliesaz.ideaz.ui.Peek
import com.hereliesaz.ideaz.ui.SettingsViewModel
import com.hereliesaz.ideaz.ui.theme.IDEazTheme

class BubbleActivity : ComponentActivity() {

    private val viewModel: MainViewModel by viewModels {
        MainViewModelFactory(
            application,
            SettingsViewModel(application)
        )
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            // HUD is always Dark Mode for contrast
            IDEazTheme(darkTheme = true) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = Color.Transparent
                ) {
                    BubbleScreen(viewModel, onUndock = {
                        finish()
                    })
                }
            }
        }
    }
}

@Composable
fun BubbleScreen(
    viewModel: MainViewModel,
    onUndock: () -> Unit
) {
    val navController = rememberNavController()
    val scope = rememberCoroutineScope()

    val supportedDetents = remember {
        listOf(SheetDetent.Hidden, AlmostHidden, Peek, Halfway)
    }
    val sheetState = rememberBottomSheetState(
        detents = supportedDetents,
        initialDetent = SheetDetent.Hidden
    )

    val context = LocalContext.current
    var showPromptPopup by remember { mutableStateOf(false) }

    val isLocalBuildEnabled = remember { viewModel.settingsViewModel.isLocalBuildEnabled() }

    val isSelectMode by viewModel.isSelectMode.collectAsState()
    val isContextualChatVisible by viewModel.isContextualChatVisible.collectAsState()
    val activeSelectionRect by viewModel.activeSelectionRect.collectAsState()

    val handleActionClick = { action: () -> Unit -> action() }

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val screenHeight = maxHeight
        val railWidth = 80.dp

        // LAYER 1: Bottom Sheet & Content (Z=1)
        // Padded so it sits to the RIGHT of the rail
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(start = railWidth)
                .zIndex(1f)
        ) {
            IdeNavHost(
                modifier = Modifier.fillMaxSize(),
                navController = navController,
                viewModel = viewModel,
                settingsViewModel = viewModel.settingsViewModel,
                onThemeToggle = { }
            )

            IdeBottomSheet(
                sheetState = sheetState,
                viewModel = viewModel,
                peekDetent = Peek,
                halfwayDetent = Halfway,
                screenHeight = LocalConfiguration.current.screenHeightDp.dp,
                onSendPrompt = { viewModel.sendPrompt(it) }
            )
        }

        // LAYER 2: Navigation Rail (Z=100)
        // ABSOLUTELY UNCONSTRAINED. Floating freely.
        Box(
            modifier = Modifier.zIndex(100f)
        ) {
            IdeNavRail(
                navController = navController,
                viewModel = viewModel,
                context = context,
                onShowPromptPopup = { showPromptPopup = true },
                handleActionClick = handleActionClick,
                isIdeVisible = isSelectMode,
                onLaunchOverlay = { viewModel.toggleSelectMode(!isSelectMode) },
                sheetState = sheetState,
                scope = scope,
                initiallyExpanded = false,
                onUndock = onUndock,
                isLocalBuildEnabled = isLocalBuildEnabled,
                isBubbleMode = false, // Standard Mode
                onNavigateToMainApp = { route ->
                    viewModel.clearSelection()
                    val intent = android.content.Intent(context, MainActivity::class.java).apply {
                        flags = android.content.Intent.FLAG_ACTIVITY_NEW_TASK or android.content.Intent.FLAG_ACTIVITY_REORDER_TO_FRONT
                        putExtra("NAV_ROUTE", route)
                    }
                    context.startActivity(intent)
                    onUndock()
                }
            )
        }

        // LAYER 3: Contextual Chat (Z=200)
        if (isContextualChatVisible && activeSelectionRect != null) {
            Box(modifier = Modifier.fillMaxSize().zIndex(200f)) {
                ContextualChatOverlay(
                    rect = activeSelectionRect!!,
                    viewModel = viewModel,
                    onClose = { viewModel.closeContextualChat() }
                )
            }
        }
    }
}