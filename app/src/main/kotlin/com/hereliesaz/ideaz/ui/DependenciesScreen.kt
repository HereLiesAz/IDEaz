package com.hereliesaz.ideaz.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch

@Composable
fun LibrariesScreen(
    viewModel: MainViewModel,
    settingsViewModel: SettingsViewModel
) {
    // getDependencies now returns List<Dependency> which is compatible with mutableStateListOf
    val dependencies = remember { mutableStateListOf(*viewModel.getDependencies().toTypedArray()) }
    var newDependency by remember { mutableStateOf("") }
    val coroutineScope = rememberCoroutineScope()

    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Text("Libraries", style = MaterialTheme.typography.headlineMedium)

        Spacer(modifier = Modifier.height(16.dp))

        Row {
            OutlinedTextField(
                value = newDependency,
                onValueChange = { newDependency = it },
                label = { Text("New Dependency (e.g., group:artifact:version)") },
                modifier = Modifier.weight(1f)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Button(onClick = {
                if (newDependency.isNotBlank()) {
                    val dep = Dependency.fromString(newDependency)
                    if (dep.group.isBlank() || dep.artifact.isBlank() || dep.version.isBlank()) {
                        dependencies.add(dep.copy(error = "Invalid dependency format"))
                    } else {
                        dependencies.add(dep)
                    }
                    newDependency = ""
                }
            }) {
                Text("Add")
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        LazyColumn(modifier = Modifier.weight(1f)) {
            itemsIndexed(dependencies) { index, dependency ->
                Column {
                    Text(dependency.toString(), color = if (dependency.error != null) Color.Red else Color.Unspecified)
                    if (dependency.error != null) {
                        Text(dependency.error, color = Color.Red)
                    }
                    if (dependency.isUpdating) {
                        CircularProgressIndicator()
                    } else if (dependency.availableUpdate != null) {
                        Button(onClick = {
                            val updatedDep = dependency.copy(version = dependency.availableUpdate, availableUpdate = null)
                            dependencies[index] = updatedDep
                        }) {
                            Text("Update to ${dependency.availableUpdate}")
                        }
                    } else {
                        Button(onClick = {
                            coroutineScope.launch {
                                dependencies[index] = dependency.copy(isUpdating = true)
                                val updatedDep = viewModel.checkForUpdates(dependency)
                                dependencies[index] = updatedDep
                            }
                        }) {
                            Text("Check for updates")
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        Row {
            Button(
                onClick = { viewModel.downloadDependencies() },
                modifier = Modifier.weight(1f)
            ) {
                Text("Download Dependencies")
            }
            Spacer(modifier = Modifier.width(8.dp))
            Button(
                onClick = { viewModel.saveDependencies(dependencies.map { it.toString() }) },
                modifier = Modifier.weight(1f)
            ) {
                Text("Save Dependencies")
            }
        }
    }
}
