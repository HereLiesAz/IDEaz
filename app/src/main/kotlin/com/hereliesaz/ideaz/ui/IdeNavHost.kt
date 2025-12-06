package com.hereliesaz.ideaz.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable

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
        startDestination = "initial_placeholder",
        modifier = modifier
    ) {
        composable("initial_placeholder") {
            Box(modifier = Modifier.fillMaxSize().background(Color.Transparent))
        }
        composable("main") {
            Box(modifier = Modifier.fillMaxSize().background(Color.Transparent))
        }
        composable("settings") {
            SettingsScreen(
                viewModel = viewModel,
                settingsViewModel = settingsViewModel,
                onThemeToggle = onThemeToggle
            )
        }
        composable("project_settings") {
            ProjectScreen(
                viewModel = viewModel,
                settingsViewModel = settingsViewModel,
                onBuildTriggered = { navController.navigate("build") },
                navController = navController // PASS NAV CONTROLLER HERE
            )
        }
        composable("file_explorer") {
            FileExplorerScreen(
                settingsViewModel = settingsViewModel,
                navController = navController
            )
        }
        composable("git") {
            GitScreen(
                viewModel = viewModel,
                settingsViewModel = settingsViewModel
            )
        }
        composable("libraries") {
            LibrariesScreen(
                viewModel = viewModel,
                settingsViewModel = settingsViewModel
            )
        }
        composable("file_content/{filePath}") { backStackEntry ->
            val filePath = backStackEntry.arguments?.getString("filePath")
            if (filePath != null) {
                FileContentScreen(filePath = filePath)
            }
        }
        composable("build") {
            Box(modifier = Modifier.fillMaxSize().background(Color.Transparent))
        }
    }
}