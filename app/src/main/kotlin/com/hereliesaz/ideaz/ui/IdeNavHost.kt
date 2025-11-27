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
        startDestination = "project_settings",
        modifier = modifier
    ) {
        composable("main") {
            // Empty placeholder for "Home" state where bottom sheet takes focus
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
                onBuildTriggered = { navController.navigate("build") }
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
            // Empty placeholder for Build/Log state
            Box(modifier = Modifier.fillMaxSize().background(Color.Transparent))
        }
    }
}