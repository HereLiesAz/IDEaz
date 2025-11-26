package com.hereliesaz.ideaz.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GitScreen(
    viewModel: MainViewModel,
    settingsViewModel: SettingsViewModel
) {
    val commitHistory by viewModel.commitHistory.collectAsState()
    val branches by viewModel.branches.collectAsState()
    var expanded by remember { mutableStateOf(false) }
    var selectedBranch by remember { mutableStateOf(settingsViewModel.getBranchName()) }

    LaunchedEffect(Unit) {
        viewModel.refreshGitData()
    }

    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Text("Git Integration", style = MaterialTheme.typography.headlineMedium)

        Spacer(modifier = Modifier.height(16.dp))

        ExposedDropdownMenuBox(
            expanded = expanded,
            onExpandedChange = { expanded = !expanded }
        ) {
            TextField(
                readOnly = true,
                value = selectedBranch,
                onValueChange = { },
                label = { Text("Current Branch") },
                trailingIcon = {
                    ExposedDropdownMenuDefaults.TrailingIcon(
                        expanded = expanded
                    )
                },
                colors = ExposedDropdownMenuDefaults.textFieldColors(),
                modifier = Modifier.menuAnchor()
            )
            ExposedDropdownMenu(
                expanded = expanded,
                onDismissRequest = {
                    expanded = false
                }
            ) {
                branches.forEach { selectionOption ->
                    DropdownMenuItem(
                        text = { Text(selectionOption) },
                        onClick = {
                            selectedBranch = selectionOption
                            expanded = false
                            viewModel.switchBranch(selectionOption)
                        }
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        Text("Commit History", style = MaterialTheme.typography.headlineSmall)

        LazyColumn {
            items(commitHistory) { commit ->
                Text(commit)
            }
        }
    }
}
