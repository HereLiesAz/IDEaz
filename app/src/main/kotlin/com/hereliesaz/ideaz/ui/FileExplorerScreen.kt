package com.hereliesaz.ideaz.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import java.io.File

@Composable
fun FileExplorerScreen(
    settingsViewModel: SettingsViewModel
) {
    val context = LocalContext.current
    val appName = settingsViewModel.getAppName()

    val projectDir = remember(appName) {
        appName?.let { File(context.filesDir, it) }
    }

    val files = remember(projectDir) {
        projectDir?.listFiles()?.sortedBy { it.name } ?: emptyList()
    }

    if (appName == null || projectDir == null || !projectDir.exists()) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Text("No project selected or project directory not found.")
        }
        return
    }

    if (files.isEmpty()) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Text("Project directory is empty.")
        }
    } else {
        LazyColumn(
            modifier = Modifier.fillMaxSize()
        ) {
            items(files) { file ->
                Text(text = if (file.isDirectory) "${file.name}/" else file.name)
            }
        }
    }
}
