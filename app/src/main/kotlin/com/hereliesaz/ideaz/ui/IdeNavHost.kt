package com.hereliesaz.ideaz.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.collectAsState
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState

@Composable
fun IdeNavHost(
    modifier: Modifier = Modifier,
    navController: NavHostController,
    viewModel: MainViewModel,
    settingsViewModel: SettingsViewModel,
    onThemeToggle: (Boolean) -> Unit
) {
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route

    LaunchedEffect(currentRoute) {
        viewModel.flushNonFatalErrors()
    }

    // Handle Pending Navigation from Intent or Rail here to ensure graph is set
    val pendingRoute by viewModel.pendingRoute.collectAsState()
    LaunchedEffect(pendingRoute) {
        pendingRoute?.let { route ->
            try {
                navController.navigate(route) {
                    launchSingleTop = true
                    restoreState = true
                }
                viewModel.setPendingRoute(null)
            } catch (e: Exception) {
                // Navigation might fail if graph isn't ready
                e.printStackTrace()
            }
        }
    }

    NavHost(
        navController = navController,
        startDestination = "project_settings",
        modifier = modifier
    ) {
        composable("main") {
            MainIdeScreen(viewModel = viewModel)
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
                navController = navController
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
                FileContentScreen(
                    filePath = filePath,
                    viewModel = viewModel.editorViewModel
                )
            }
        }
        composable("build") {
            Box(modifier = Modifier.fillMaxSize().background(Color.Transparent))
        }
    }
}