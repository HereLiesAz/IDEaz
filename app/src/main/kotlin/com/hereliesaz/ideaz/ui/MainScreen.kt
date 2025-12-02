package com.hereliesaz.ideaz.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.navigation.compose.rememberNavController
import com.hereliesaz.ideaz.models.ProjectType

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(
    viewModel: MainViewModel,
    onRequestScreenCapture: () -> Unit,
    onThemeToggle: (Boolean) -> Unit,
    onLaunchOverlay: () -> Unit
) {
    val navController = rememberNavController()
    // This MainScreen is now primarily the "Dashboard" / "Home" activity.
    // The actual IDE overlay tools are running in the Service.

    val currentPackageName by viewModel.settingsViewModel.targetPackageName.collectAsState()

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        containerColor = MaterialTheme.colorScheme.background,
    ) { innerPadding ->
        Box(modifier = Modifier.padding(innerPadding).fillMaxSize()) {
            // We reuse IdeNavHost here but only for the non-overlay screens (Settings, Project)
            // The "IDE" logic (Rail, Console) is now in the Service.
            IdeNavHost(
                modifier = Modifier.fillMaxSize(),
                navController = navController,
                viewModel = viewModel,
                settingsViewModel = viewModel.settingsViewModel,
                onThemeToggle = onThemeToggle
            )
        }
    }
}