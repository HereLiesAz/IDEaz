package com.hereliesaz.ideaz.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import com.hereliesaz.ideaz.api.Activity
import com.hereliesaz.ideaz.api.Session
import com.hereliesaz.ideaz.api.Source

@Composable
fun IdeNavHost(
    modifier: Modifier = Modifier,
    navController: NavHostController,
    viewModel: MainViewModel,
    // MODIFIED: Removed status variables, they are moving to the bottom sheet
    session: Session?,
    sessions: List<Session>,
    activities: List<Activity>,
    sources: List<Source>,
    onThemeToggle: (Boolean) -> Unit
) {
    NavHost(
        navController = navController,
        startDestination = "main",
        modifier = modifier
    ) {
        composable("main") {
            // MODIFIED: This is now an empty screen.
            // The status text has been moved to the IdeBottomSheet.
        }
        composable("settings") {
            SettingsScreen(sessions = sessions, onThemeToggle = onThemeToggle)
        }
        composable("project_settings") {
            ProjectSettingsScreen(viewModel = viewModel, sources = sources)
        }
    }
}