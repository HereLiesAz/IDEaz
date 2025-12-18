package com.hereliesaz.ideaz.ui.project

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.hereliesaz.ideaz.models.ProjectType
import com.hereliesaz.ideaz.ui.MainViewModel

@Composable
fun SetupTab(viewModel: MainViewModel) {
    // Collecting the compatibility state
    val uiState by viewModel.uiState.collectAsState()

    var appName by remember { mutableStateOf("") }
    var packageName by remember { mutableStateOf(uiState.targetPackageName ?: "com.example.app") }
    var projectType by remember { mutableStateOf(uiState.projectType) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text("Project Setup", style = MaterialTheme.typography.headlineMedium)

        OutlinedTextField(
            value = appName,
            onValueChange = { appName = it },
            label = { Text("App Name") },
            modifier = Modifier.fillMaxWidth()
        )

        OutlinedTextField(
            value = packageName,
            onValueChange = { packageName = it },
            label = { Text("Package Name") },
            modifier = Modifier.fillMaxWidth()
        )

        Button(
            onClick = {
                val path = "/sdcard/IDEaz/Projects/$appName"
                viewModel.createProject(appName, path, projectType, packageName)
            },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Initialize Project")
        }

        if (uiState.currentProjectPath != null) {
            Text("Current Path: ${uiState.currentProjectPath}")
        }
    }
}