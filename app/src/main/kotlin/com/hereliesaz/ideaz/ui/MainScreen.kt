package com.hereliesaz.ideaz.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.navigation.compose.rememberNavController

@Composable
fun MainScreen(
    viewModel: MainViewModel,
    settingsViewModel: SettingsViewModel,
    onRequestScreenCapture: () -> Unit,
    onThemeToggle: (Boolean) -> Unit,
    onLaunchOverlay: () -> Unit,
    onLaunchPreview: (String) -> Unit
) {
    val navController = rememberNavController()
    val isSelectMode by viewModel.isSelectMode.collectAsState()

    Scaffold { innerPadding ->
        Box(modifier = Modifier.padding(innerPadding)) {
            IdeNavHost(
                navController = navController,
                viewModel = viewModel,
                settingsViewModel = settingsViewModel,
                onThemeToggle = onThemeToggle
            )
        }
    }

    if (isSelectMode) {
        Box(modifier = Modifier.fillMaxSize()) {
            // Selection Overlay
        }
    }
}