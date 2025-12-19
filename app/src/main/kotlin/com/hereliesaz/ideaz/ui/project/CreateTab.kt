package com.hereliesaz.ideaz.ui.project

import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Text
import androidx.compose.material3.Switch
import androidx.compose.material3.TextField
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.hereliesaz.aznavrail.AzButton
import com.hereliesaz.aznavrail.AzTextBox
import com.hereliesaz.aznavrail.model.AzButtonShape
import com.hereliesaz.ideaz.models.ProjectType
import com.hereliesaz.ideaz.ui.MainViewModel
import com.hereliesaz.ideaz.ui.SettingsViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProjectCreateTab(
    viewModel: MainViewModel,
    settingsViewModel: SettingsViewModel,
    context: Context,
    onExecute: (() -> Unit) -> Unit
) {
    var appName by remember { mutableStateOf("") }
    var repoDescription by remember { mutableStateOf("Created with IDEaz") }
    var packageName by remember { mutableStateOf("com.example.app") }
    var selectedType by remember { mutableStateOf(ProjectType.ANDROID) }
    var isPrivateRepo by remember { mutableStateOf(false) }

    LazyColumn(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        item {
            Text("Create New Repository")
            Spacer(Modifier.height(16.dp))
            AzTextBox(value = appName, onValueChange = { appName = it }, hint = "App Name", onSubmit = {})
            Spacer(Modifier.height(8.dp))
            AzTextBox(value = repoDescription, onValueChange = { repoDescription = it }, hint = "Description", onSubmit = {})
            Spacer(Modifier.height(8.dp))

            var expanded by remember { mutableStateOf(false) }
            ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = !expanded }) {
                TextField(
                    modifier = Modifier.menuAnchor().fillMaxWidth(),
                    readOnly = true,
                    value = selectedType.displayName,
                    onValueChange = {},
                    label = { Text("Type") },
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                    colors = ExposedDropdownMenuDefaults.textFieldColors()
                )
                ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                    ProjectType.values().forEach { type ->
                        DropdownMenuItem(text = { Text(type.displayName) }, onClick = { selectedType = type; expanded = false })
                    }
                }
            }
            Spacer(Modifier.height(8.dp))

            if (selectedType == ProjectType.ANDROID) {
                AzTextBox(value = packageName, onValueChange = { packageName = it }, hint = "Package Name", onSubmit = {})
                Spacer(Modifier.height(8.dp))
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Private Repository")
                Spacer(Modifier.weight(1f))
                Switch(checked = isPrivateRepo, onCheckedChange = { isPrivateRepo = it })
            }
            Spacer(Modifier.height(24.dp))

            AzButton(
                onClick = {
                    onExecute {
                        viewModel.createGitHubRepository(appName, repoDescription, isPrivateRepo, selectedType, packageName, context) {}
                        Toast.makeText(context, "Creating...", Toast.LENGTH_SHORT).show()
                    }
                },
                text = "Create & Continue",
                shape = AzButtonShape.RECTANGLE,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}