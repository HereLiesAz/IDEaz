package com.hereliesaz.ideaz.ui.project

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.hereliesaz.ideaz.models.ProjectType
import com.hereliesaz.ideaz.ui.MainViewModel

@Composable
fun CreateTab(viewModel: MainViewModel) {
    var repoName by remember { mutableStateOf("") }
    var repoDesc by remember { mutableStateOf("") }
    var isPrivate by remember { mutableStateOf(false) }
    var selectedType by remember { mutableStateOf(ProjectType.ANDROID) }
    var packageName by remember { mutableStateOf("com.example.app") }

    val context = LocalContext.current

    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Text("Create New Repository", style = MaterialTheme.typography.headlineSmall)
        }

        item {
            OutlinedTextField(
                value = repoName,
                onValueChange = { repoName = it },
                label = { Text("Repository Name") },
                modifier = Modifier.fillMaxWidth()
            )
        }

        item {
            OutlinedTextField(
                value = repoDesc,
                onValueChange = { repoDesc = it },
                label = { Text("Description") },
                modifier = Modifier.fillMaxWidth()
            )
        }

        item {
            Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                Checkbox(checked = isPrivate, onCheckedChange = { isPrivate = it })
                Text("Private Repository")
            }
        }

        item {
            Text("Project Type", style = MaterialTheme.typography.titleMedium)
            ProjectType.values().forEach { type ->
                Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                    RadioButton(
                        selected = (type == selectedType),
                        onClick = { selectedType = type }
                    )
                    // Fix: Ensure we just use the name property or toString
                    Text(text = type.name, modifier = Modifier.padding(start = 8.dp))
                }
            }
        }

        item {
            Button(
                onClick = {
                    viewModel.createGitHubRepository(repoName, repoDesc, isPrivate, selectedType, packageName, context) { name, url ->
                        // Callback logic
                    }
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Create Repository")
            }
        }
    }
}