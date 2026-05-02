package com.hereliesaz.ideaz.ui.project

import android.content.Context
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.hereliesaz.aznavrail.AzButton
import com.hereliesaz.aznavrail.model.AzButtonShape
import com.hereliesaz.ideaz.ui.MainViewModel
import com.hereliesaz.ideaz.ui.SettingsViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import android.provider.Settings
import android.net.Uri
import android.content.Intent
import android.os.Environment
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import com.hereliesaz.ideaz.utils.checkAndRequestStoragePermission

@Composable
fun ProjectLoadTab(
    viewModel: MainViewModel,
    settingsViewModel: SettingsViewModel,
    context: Context,
    onProjectSelected: (String) -> Unit
) {
    var projectMetadataList by remember { mutableStateOf<List<com.hereliesaz.ideaz.ui.ProjectMetadata>>(emptyList()) }
    val localProjects by settingsViewModel.localProjects.collectAsState()

    var showDeleteDialog by remember { mutableStateOf(false) }
    var projectToDelete by remember { mutableStateOf<String?>(null) }

    if (showDeleteDialog && projectToDelete != null) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = { Text("Delete Project?") },
            text = { Text("Are you sure you want to delete '${projectToDelete}'? This action cannot be undone.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        projectToDelete?.let { viewModel.deleteProject(it) }
                        showDeleteDialog = false
                    },
                    colors = ButtonDefaults.textButtonColors(
                        contentColor = MaterialTheme.colorScheme.error
                    )
                ) {
                    Text("Delete")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    LaunchedEffect(localProjects) {
        viewModel.scanLocalProjects()
        withContext(Dispatchers.IO) {
            projectMetadataList = viewModel.getLocalProjectsWithMetadata()
        }
    }

    val externalProjectLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree()
    ) { uri: Uri? ->
        if (uri != null) {
            viewModel.registerExternalProject(uri)
        }
    }

    LazyColumn(modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
        if (projectMetadataList.isEmpty()) {
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 32.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            imageVector = Icons.Default.Info,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(48.dp)
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = "No local projects found",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "Import an existing project to get started.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        } else {
            items(projectMetadataList) { project ->
                Card(
                    modifier = Modifier
                        .padding(bottom = 8.dp)
                        .fillMaxWidth()
                        .clickable { onProjectSelected(project.name) },
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surface,
                        contentColor = MaterialTheme.colorScheme.onSurface
                    ),
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = project.name,
                                style = MaterialTheme.typography.titleMedium
                            )
                            Text(
                                text = "Size: ${formatSize(project.sizeBytes)}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                            )
                        }
                        IconButton(onClick = {
                            projectToDelete = project.name
                            showDeleteDialog = true
                        }) {
                            Icon(Icons.Default.Delete, contentDescription = "Delete ${project.name}")
                        }
                    }
                }
            }
        }

        item {
            Spacer(modifier = Modifier.height(16.dp))

            AzButton(
                onClick = {
                    checkAndRequestStoragePermission(context) {
                        externalProjectLauncher.launch(null)
                    }
                },
                text = "Add External Project",
                shape = AzButtonShape.RECTANGLE,
                modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp)
            )
        }
    }
}

private fun formatSize(size: Long): String {
    if (size <= 0) return "0 B"
    val units = arrayOf("B", "KB", "MB", "GB", "TB")
    val digitGroups = (Math.log10(size.toDouble()) / Math.log10(1024.0)).toInt()
    return java.text.DecimalFormat("#,##0.#").format(size / Math.pow(1024.0, digitGroups.toDouble())) + " " + units[digitGroups]
}