package com.hereliesaz.ideaz.ui

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.navigation.compose.rememberNavController
import com.composables.core.rememberBottomSheetState
import com.composables.core.SheetDetent

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(
    viewModel: MainViewModel,
    onRequestScreenCapture: () -> Unit,
    onThemeToggle: (Boolean) -> Unit,
    onLaunchOverlay: () -> Unit
) {
    val navController = rememberNavController()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val supportedDetents = remember {
        listOf(SheetDetent.Hidden, AlmostHidden, Peek, Halfway)
    }
    val sheetState = rememberBottomSheetState(
        detents = supportedDetents,
        initialDetent = Halfway
    )

    val isIdeVisible by viewModel.isTargetAppVisible.collectAsState()
    val isLocalBuildEnabled = viewModel.settingsViewModel.isLocalBuildEnabled()

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        containerColor = MaterialTheme.colorScheme.background,
    ) { innerPadding ->
        Row(modifier = Modifier.padding(innerPadding).fillMaxSize()) {
            IdeNavRail(
                navController = navController,
                viewModel = viewModel,
                context = context,
                onShowPromptPopup = { /*TODO*/ },
                handleActionClick = { it() },
                isIdeVisible = isIdeVisible,
                onLaunchOverlay = onLaunchOverlay,
                sheetState = sheetState,
                scope = scope,
                isLocalBuildEnabled = isLocalBuildEnabled,
                onNavigateToMainApp = { route ->
                    navController.navigate(route) {
                        launchSingleTop = true
                        restoreState = true
                    }
                }
            )
            IdeNavHost(
                modifier = Modifier.weight(1f),
                navController = navController,
                viewModel = viewModel,
                settingsViewModel = viewModel.settingsViewModel,
                onThemeToggle = onThemeToggle
            )
        }
    }
}