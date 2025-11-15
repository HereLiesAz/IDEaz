package com.hereliesaz.ideaz.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import com.hereliesaz.ideaz.MainApplication

@Composable
fun IdeNavHost(
    modifier: Modifier = Modifier,
    navController: NavHostController,
    viewModel: MainViewModel,
    settingsViewModel: SettingsViewModel,
    onThemeToggle: (Boolean) -> Unit
) {
    NavHost(
        navController = navController,
        startDestination = "main",
        modifier = modifier
    ) {
        composable("main") {
            // --- FIX: Update call to match LiveOutputBottomCard's new signature ---
            LiveOutputBottomCard(
                logStream = viewModel.filteredLog
                // bottomPadding = 0.dp // This will use the default
            )
            // --- END FIX ---
        }
        composable("settings") {
            SettingsScreen(
                viewModel = viewModel,
                settingsViewModel = settingsViewModel,
                onThemeToggle = onThemeToggle
            )
        }
        composable("project_settings") {
            ProjectSettingsScreen(
                viewModel = viewModel,
                settingsViewModel = settingsViewModel
            )
        }
    }
}