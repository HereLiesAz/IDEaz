package com.hereliesaz.ideaz.ui

import android.util.Log
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

private const val TAG = "IdeNavHost"

@Composable
fun IdeNavHost(
    modifier: Modifier = Modifier,
    navController: NavHostController,
    viewModel: MainViewModel,
    settingsViewModel: SettingsViewModel, // Add this
    onThemeToggle: (Boolean) -> Unit
) {
    Log.d(TAG, "IdeNavHost: Composing")
    Log.d(TAG, "IdeNavHost: MainViewModel hash: ${viewModel.hashCode()}")
    Log.d(TAG, "IdeNavHost: SettingsViewModel hash: ${settingsViewModel.hashCode()}")

    NavHost(
        navController = navController,
        startDestination = "main",
        modifier = modifier
    ) {
        composable("main") {
            // Main content is now just the log view
        }
        composable("settings") {
            // Pass the settingsViewModel down
            SettingsScreen(
                onThemeToggle = onThemeToggle,
                settingsViewModel = settingsViewModel
            )
        }
        composable("project_settings") {
            // Pass the settingsViewModel down
            ProjectSettingsScreen(
                viewModel = viewModel,
                settingsViewModel = settingsViewModel
            )
        }
    }
}