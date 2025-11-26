package com.hereliesaz.ideaz.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import java.io.File

@Composable
fun FileExplorerScreen(
    settingsViewModel: SettingsViewModel,
    navController: NavController
) {
    val context = LocalContext.current
    val appName = settingsViewModel.getAppName()
    val projectDir = remember(appName) {
        appName?.let { File(context.filesDir, it) }
    }

    var currentPath by remember(projectDir) { mutableStateOf(projectDir) }

    val files = remember(currentPath) {
        currentPath?.listFiles()?.sortedBy { it.isDirectory }?.sortedBy { it.name } ?: emptyList()
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

    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Text("File Explorer", style = androidx.compose.material3.MaterialTheme.typography.headlineMedium)
        Text(currentPath?.absolutePath?.removePrefix(projectDir.absolutePath) ?: "/", style = androidx.compose.material3.MaterialTheme.typography.bodySmall)

        Spacer(modifier = Modifier.height(16.dp))

        LazyColumn {
            if (currentPath != projectDir) {
                item {
                    Text(
                        text = "..",
                        modifier = Modifier.clickable {
                            currentPath = currentPath?.parentFile
                        }
                    )
                }
            }
            items(files) { file ->
                Text(
                    text = if (file.isDirectory) "${file.name}/" else file.name,
                    modifier = Modifier.clickable {
                        if (file.isDirectory) {
                            currentPath = file
                        } else {
                            navController.navigate("file_content/${file.absolutePath}")
                        }
                    }
                )
            }
        }
    }
}
