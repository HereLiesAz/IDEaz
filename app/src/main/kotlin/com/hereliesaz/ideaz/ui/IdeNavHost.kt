package com.hereliesaz.ideaz.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable

@Composable
fun IdeNavHost(
    modifier: Modifier = Modifier,
    navController: NavHostController,
    viewModel: MainViewModel,
    settingsViewModel: SettingsViewModel,
    onThemeToggle: (Boolean) -> Unit,
    bottomPadding: Dp = 0.dp
) {
    NavHost(
        navController = navController,
        startDestination = "project_settings",
        modifier = modifier
    ) {
        composable("main") {
            LiveOutputBottomCard(
                logStream = viewModel.filteredLog,
                bottomPadding = bottomPadding
            )
        }
        composable("settings") {
            SettingsScreen(
                viewModel = viewModel,
                settingsViewModel = settingsViewModel,
                onThemeToggle = onThemeToggle
            )
        }
        composable("project_settings") {
            val sources by viewModel.ownedSources.collectAsState()
            ProjectScreen(
                viewModel = viewModel,
                settingsViewModel = settingsViewModel,
                sources = sources
            )
        }
        composable("build") {
            Box(modifier = Modifier.fillMaxSize().background(Color.Transparent))
        }
    }
}