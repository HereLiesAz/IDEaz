package com.hereliesaz.ideaz.minapp

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.navigation.compose.rememberNavController
import com.hereliesaz.ideaz.ui.composables.SheetDetent
import com.hereliesaz.ideaz.ui.composables.rememberBottomSheetState
import com.hereliesaz.ideaz.ui.IdeNavHost
import com.hereliesaz.ideaz.ui.IdeNavRail
import com.hereliesaz.ideaz.ui.MainViewModel
import com.hereliesaz.ideaz.ui.MainViewModelFactory
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
            IDEazTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
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

    // Dummy sheet state for IdeNavRail compatibility
    val sheetState = rememberBottomSheetState(
        initialDetent = SheetDetent.FullyExpanded
    )
    val context = LocalContext.current
    var showPromptPopup by remember{ mutableStateOf(false) }

    // Read capability
    val isLocalBuildEnabled = remember { viewModel.settingsViewModel.isLocalBuildEnabled() }

    val handleActionClick = { action: () -> Unit -> action() }

    Row(modifier = Modifier.fillMaxSize()) {
        IdeNavRail(
            navController = navController,
            viewModel = viewModel,
            context = context,
            onShowPromptPopup = { showPromptPopup = true },
            handleActionClick = handleActionClick,
            isIdeVisible = true,
            onLaunchOverlay = {},
            sheetState = sheetState,
            scope = scope,
            initiallyExpanded = true,
            onUndock = onUndock,
            isLocalBuildEnabled = isLocalBuildEnabled
        )

        IdeNavHost(
            modifier = Modifier.fillMaxSize(),
            navController = navController,
            viewModel = viewModel,
            settingsViewModel = viewModel.settingsViewModel,
            onThemeToggle = { }
        )
    }
}