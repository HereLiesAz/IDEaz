package com.hereliesaz.ideaz.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.hereliesaz.aznavrail.AzButton
import com.hereliesaz.aznavrail.AzForm
import com.hereliesaz.aznavrail.model.AzButtonShape
import kotlinx.coroutines.launch

@Composable
fun LibrariesScreen(
    viewModel: MainViewModel,
    settingsViewModel: SettingsViewModel
) {
    // GATE CHECK
    if (!settingsViewModel.isLocalBuildEnabled()) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(32.dp)) {
                Text(
                    text = "Local Builds Disabled",
                    style = MaterialTheme.typography.headlineMedium,
                    color = MaterialTheme.colorScheme.onBackground
                )
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = "Dependency management requires the local build tools extension.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(24.dp))
                AzButton(
                    onClick = { /* Navigate to settings? Or just inform */ },
                    text = "Go to Settings to Enable",
                    shape = AzButtonShape.RECTANGLE
                )
            }
        }
        return
    }

    // getDependencies now returns List<Dependency> which is compatible with mutableStateListOf
    val dependencies = remember { mutableStateListOf(*viewModel.getDependencies().toTypedArray()) }
    val coroutineScope = rememberCoroutineScope()

    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Spacer(modifier = Modifier.height(64.dp))

        Row {
            AzForm(
                formName = "New Dependency",
                submitButtonContent = { Text("Add") },
                onSubmit = { formData ->
                    val group = formData["group"].orEmpty()
                    val artifact = formData["artifact"].orEmpty()
                    val version = formData["version"].orEmpty()

                    if (group.isNotBlank() && artifact.isNotBlank() && version.isNotBlank()) {
                        val newDependency = "$group:$artifact:$version"
                        dependencies.add(Dependency.fromString(newDependency))
                    }
                }

            )  {
                entry(entryName = "group", hint = "Group")
                entry(entryName = "artifact", hint = "Artifact")
                entry(entryName = "version", hint = "Version")

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
            AzButton(
                onClick = { viewModel.downloadDependencies() },
                shape = AzButtonShape.NONE,
                text = "Download"
            )


            Spacer(modifier = Modifier.width(8.dp))
            AzButton(
                onClick = { viewModel.saveDependencies(dependencies.map { it.toString() }) },
                shape = AzButtonShape.NONE,
                text = "Save"
            )
        }
    }
}