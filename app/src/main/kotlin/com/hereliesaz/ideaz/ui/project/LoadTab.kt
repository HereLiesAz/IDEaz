package com.hereliesaz.ideaz.ui.project

import android.content.Context
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
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

    val apkPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri != null) {
            val intent = Intent(Intent.ACTION_VIEW)
            intent.setDataAndType(uri, "application/vnd.android.package-archive")
            intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION
            try {
                context.startActivity(intent)
            } catch (e: Exception) {
                Toast.makeText(context, "Could not open APK: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    LazyColumn(modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
        if (projectMetadataList.isEmpty()) {
            item {
                Spacer(modifier = Modifier.height(16.dp))
                Text("No local projects.", color = MaterialTheme.colorScheme.onBackground)
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
                        IconButton(onClick = { viewModel.deleteProject(project.name) }) {
                            Icon(Icons.Default.Delete, contentDescription = "Delete Project")
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
                modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)
            )

            AzButton(
                onClick = {
                    checkAndRequestStoragePermission(context) {
                        apkPickerLauncher.launch(arrayOf("application/vnd.android.package-archive"))
                    }
                },
                text = "Select APK",
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