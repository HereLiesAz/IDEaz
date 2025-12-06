package com.hereliesaz.ideaz

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
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
import kotlinx.coroutines.launch

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
            val themeMode by viewModel.settingsViewModel.themeMode.collectAsState()

            val useDarkTheme = when (themeMode) {
                SettingsViewModel.THEME_LIGHT -> false
                SettingsViewModel.THEME_DARK -> true
                SettingsViewModel.THEME_SYSTEM -> isSystemInDarkTheme()
                SettingsViewModel.THEME_AUTO -> !isSystemInDarkTheme() // Opposite for Overlay (High Contrast)
                else -> !isSystemInDarkTheme()
            }

            IDEazTheme(darkTheme = useDarkTheme) {
                // CRITICAL: Surface must be transparent to see the app behind
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
    var showPromptPopup by remember{ mutableStateOf(false) }

    // Read capability & state
    val isLocalBuildEnabled = remember { viewModel.settingsViewModel.isLocalBuildEnabled() }

    val isSelectMode by viewModel.isSelectMode.collectAsState()
    val isContextualChatVisible by viewModel.isContextualChatVisible.collectAsState()
    val activeSelectionRect by viewModel.activeSelectionRect.collectAsState()

    val handleActionClick = { action: () -> Unit -> action() }

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val screenHeight = maxHeight

        Row(modifier = Modifier.fillMaxSize()) {
            IdeNavRail(
                navController = navController,
                viewModel = viewModel,
                context = context,
                onShowPromptPopup = { showPromptPopup = true },
                handleActionClick = handleActionClick,
                isIdeVisible = isSelectMode,
                onLaunchOverlay = {
                    viewModel.toggleSelectMode(!isSelectMode)
                },
                sheetState = sheetState,
                scope = scope,
                initiallyExpanded = false, // Forced undock state
                onUndock = onUndock,
                isLocalBuildEnabled = isLocalBuildEnabled,
                isBubbleMode = true,
                onNavigateToMainApp = { route ->
                    // Clear selection context when leaving bubble logic
                    viewModel.clearSelection()
                    val intent = android.content.Intent(context, MainActivity::class.java).apply {
                        flags = android.content.Intent.FLAG_ACTIVITY_NEW_TASK or android.content.Intent.FLAG_ACTIVITY_REORDER_TO_FRONT
                        putExtra("NAV_ROUTE", route)
                    }
                    context.startActivity(intent)
                    onUndock()
                }
            )

            // In Bubble/Overlay mode, we do NOT show the full IdeNavHost content (Settings/Project)
            // unless explicitly requested. Usually, the overlay is just the Rail + Chat.
            // If you want Settings in the overlay, keep this. But usually, settings are opaque.
            // We keep it here, but relies on the Surface transparency above.
            IdeNavHost(
                modifier = Modifier.fillMaxSize(),
                navController = navController,
                viewModel = viewModel,
                settingsViewModel = viewModel.settingsViewModel,
                onThemeToggle = { }
            )
        }

        IdeBottomSheet(
            sheetState = sheetState,
            viewModel = viewModel,
            peekDetent = Peek,
            halfwayDetent = Halfway,
            screenHeight = screenHeight,
            onSendPrompt = {
                viewModel.sendPrompt(it)
            }
        )

        // Draw the chat OVER everything if active
        if (isContextualChatVisible && activeSelectionRect != null) {
            ContextualChatOverlay(
                rect = activeSelectionRect!!,
                viewModel = viewModel,
                onClose = { viewModel.closeContextualChat() }
            )
        }
    }
}