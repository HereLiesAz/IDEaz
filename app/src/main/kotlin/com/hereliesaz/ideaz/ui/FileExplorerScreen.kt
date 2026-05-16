package com.hereliesaz.ideaz.ui

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CreateNewFolder
import androidx.compose.material.icons.filled.NoteAdd
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.hereliesaz.aznavrail.AzButton
import com.hereliesaz.aznavrail.AzTextBox
import com.hereliesaz.aznavrail.model.AzButtonShape
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.net.URLEncoder
import java.nio.charset.StandardCharsets

private sealed class FileExplorerDialog {
    object NewFile : FileExplorerDialog()
    object NewFolder : FileExplorerDialog()
    /** Long-press picker: choose Rename vs Delete for [target]. */
    data class Manage(val target: File) : FileExplorerDialog()
    data class Rename(val target: File) : FileExplorerDialog()
    data class Delete(val target: File) : FileExplorerDialog()
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun FileExplorerScreen(
    settingsViewModel: SettingsViewModel,
    navController: NavController,
    viewModel: MainViewModel
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val appName = settingsViewModel.getAppName()
    val projectDir = remember(appName) {
        appName?.let { File(context.filesDir, it) }
    }

    var currentPath by remember(projectDir) { mutableStateOf(projectDir) }
    var refreshTick by remember { mutableIntStateOf(0) }
    var activeDialog by remember { mutableStateOf<FileExplorerDialog?>(null) }
    var dialogInput by remember { mutableStateOf("") }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    // External re-list signal — e.g. Gemini wrote files into the project.
    val externalReloadTrigger by viewModel.stateDelegate.fileTreeReloadTrigger.collectAsState()

    val files = remember(currentPath, refreshTick, externalReloadTrigger) {
        // Bolt: Optimized sorting - Single pass, Directories first, then alphabetical.
        currentPath?.listFiles()?.sortedWith(compareBy({ !it.isDirectory }, { it.name })) ?: emptyList()
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

    fun safeRefresh() {
        refreshTick++
    }

    // --- Dialogs ---

    when (val dialog = activeDialog) {
        is FileExplorerDialog.Manage -> {
            AlertDialog(
                onDismissRequest = { activeDialog = null },
                title = { Text(dialog.target.name) },
                text = { Text("Rename or delete this ${if (dialog.target.isDirectory) "folder" else "file"}.") },
                confirmButton = {
                    Row {
                        AzButton(
                            onClick = {
                                dialogInput = dialog.target.name
                                errorMessage = null
                                activeDialog = FileExplorerDialog.Rename(dialog.target)
                            },
                            text = "Rename",
                            shape = AzButtonShape.RECTANGLE
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        TextButton(
                            onClick = { activeDialog = FileExplorerDialog.Delete(dialog.target) },
                            colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                        ) { Text("Delete") }
                    }
                },
                dismissButton = {
                    TextButton(onClick = { activeDialog = null }) { Text("Cancel") }
                }
            )
        }
        FileExplorerDialog.NewFile, FileExplorerDialog.NewFolder -> {
            val isFolder = dialog == FileExplorerDialog.NewFolder
            AlertDialog(
                onDismissRequest = { activeDialog = null; dialogInput = "" },
                title = { Text(if (isFolder) "New folder" else "New file") },
                text = {
                    Column {
                        AzTextBox(
                            value = dialogInput,
                            onValueChange = { dialogInput = it },
                            hint = if (isFolder) "Folder name" else "File name",
                            onSubmit = {}
                        )
                        errorMessage?.let { msg ->
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(msg, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                        }
                    }
                },
                confirmButton = {
                    AzButton(
                        onClick = {
                            val name = dialogInput.trim()
                            val parent = currentPath
                            if (name.isBlank() || parent == null) {
                                errorMessage = "Name cannot be blank."
                                return@AzButton
                            }
                            if (name.contains('/') || name.contains('\\')) {
                                errorMessage = "Use a single name; navigate into folders to add nested items."
                                return@AzButton
                            }
                            val target = File(parent, name)
                            if (target.exists()) {
                                errorMessage = "'$name' already exists."
                                return@AzButton
                            }
                            scope.launch {
                                val ok = withContext(Dispatchers.IO) {
                                    runCatching {
                                        if (isFolder) target.mkdirs() else target.createNewFile()
                                    }.getOrDefault(false)
                                }
                                if (ok) {
                                    activeDialog = null
                                    dialogInput = ""
                                    errorMessage = null
                                    safeRefresh()
                                } else {
                                    errorMessage = "Create failed."
                                }
                            }
                        },
                        text = "Create",
                        shape = AzButtonShape.RECTANGLE
                    )
                },
                dismissButton = {
                    TextButton(onClick = { activeDialog = null; dialogInput = ""; errorMessage = null }) {
                        Text("Cancel")
                    }
                }
            )
        }
        is FileExplorerDialog.Rename -> {
            // Prefill the field with the current name on first open.
            LaunchedEffect(dialog) { if (dialogInput.isBlank()) dialogInput = dialog.target.name }
            AlertDialog(
                onDismissRequest = { activeDialog = null; dialogInput = "" },
                title = { Text("Rename ${dialog.target.name}") },
                text = {
                    Column {
                        AzTextBox(
                            value = dialogInput,
                            onValueChange = { dialogInput = it },
                            hint = "New name",
                            onSubmit = {}
                        )
                        errorMessage?.let { msg ->
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(msg, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                        }
                    }
                },
                confirmButton = {
                    AzButton(
                        onClick = {
                            val newName = dialogInput.trim()
                            if (newName.isBlank() || newName == dialog.target.name) {
                                activeDialog = null
                                dialogInput = ""
                                errorMessage = null
                                return@AzButton
                            }
                            if (newName.contains('/') || newName.contains('\\')) {
                                errorMessage = "Use a single name."
                                return@AzButton
                            }
                            val newFile = File(dialog.target.parentFile, newName)
                            if (newFile.exists()) {
                                errorMessage = "'$newName' already exists."
                                return@AzButton
                            }
                            scope.launch {
                                val ok = withContext(Dispatchers.IO) {
                                    runCatching { dialog.target.renameTo(newFile) }.getOrDefault(false)
                                }
                                if (ok) {
                                    activeDialog = null
                                    dialogInput = ""
                                    errorMessage = null
                                    safeRefresh()
                                } else {
                                    errorMessage = "Rename failed."
                                }
                            }
                        },
                        text = "Rename",
                        shape = AzButtonShape.RECTANGLE
                    )
                },
                dismissButton = {
                    TextButton(onClick = { activeDialog = null; dialogInput = ""; errorMessage = null }) {
                        Text("Cancel")
                    }
                }
            )
        }
        is FileExplorerDialog.Delete -> {
            AlertDialog(
                onDismissRequest = { activeDialog = null },
                title = { Text("Delete ${dialog.target.name}?") },
                text = {
                    Text(
                        if (dialog.target.isDirectory)
                            "Recursively delete this folder and all its contents? This cannot be undone."
                        else
                            "Delete this file? This cannot be undone."
                    )
                },
                confirmButton = {
                    TextButton(
                        onClick = {
                            scope.launch {
                                val ok = withContext(Dispatchers.IO) {
                                    runCatching {
                                        if (dialog.target.isDirectory) dialog.target.deleteRecursively()
                                        else dialog.target.delete()
                                    }.getOrDefault(false)
                                }
                                activeDialog = null
                                if (ok) safeRefresh()
                            }
                        },
                        colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                    ) { Text("Delete") }
                },
                dismissButton = {
                    TextButton(onClick = { activeDialog = null }) { Text("Cancel") }
                }
            )
        }
        null -> Unit
    }

    // --- UI ---

    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        // Clearance for the rail-provided "Files" screen title.
        Spacer(modifier = Modifier.height(RAIL_TITLE_CLEARANCE))

        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            Text(
                text = currentPath?.absolutePath?.removePrefix(projectDir.absolutePath)?.ifBlank { "/" } ?: "/",
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.weight(1f)
            )
            IconButton(onClick = {
                dialogInput = ""
                errorMessage = null
                activeDialog = FileExplorerDialog.NewFile
            }) {
                Icon(Icons.Default.NoteAdd, contentDescription = "New file")
            }
            IconButton(onClick = {
                dialogInput = ""
                errorMessage = null
                activeDialog = FileExplorerDialog.NewFolder
            }) {
                Icon(Icons.Default.CreateNewFolder, contentDescription = "New folder")
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        LazyColumn {
            if (currentPath != projectDir) {
                item {
                    Text(
                        text = "..",
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { currentPath = currentPath?.parentFile }
                            .padding(vertical = 8.dp)
                    )
                }
            }
            items(files, key = { it.absolutePath }) { file ->
                Text(
                    text = if (file.isDirectory) "${file.name}/" else file.name,
                    modifier = Modifier
                        .fillMaxWidth()
                        .combinedClickable(
                            onClick = {
                                if (file.isDirectory) {
                                    currentPath = file
                                } else {
                                    val encodedPath = URLEncoder.encode(file.absolutePath, StandardCharsets.UTF_8.toString())
                                    navController.navigate("file_content/$encodedPath")
                                }
                            },
                            onLongClick = {
                                dialogInput = ""
                                errorMessage = null
                                activeDialog = FileExplorerDialog.Manage(file)
                            }
                        )
                        .padding(vertical = 8.dp)
                )
            }
        }
    }
}
