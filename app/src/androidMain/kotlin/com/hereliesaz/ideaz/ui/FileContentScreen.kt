package com.hereliesaz.ideaz.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.hereliesaz.aznavrail.AzButton
import com.hereliesaz.aznavrail.model.AzButtonShape
import io.github.rosemoe.sora.widget.CodeEditor
import io.github.rosemoe.sora.lang.EmptyLanguage
import com.hereliesaz.ideaz.ui.editor.EditorSetup
import com.hereliesaz.ideaz.ui.editor.EditorViewModel
import java.io.File

/**
 * Heuristic binary-file detector: a NUL byte anywhere in the first 8000 bytes
 * means this isn't text (the same heuristic `git` and most editors use). Text
 * files, including UTF-8/UTF-16 with BOMs, never legitimately contain NUL.
 */
private fun looksBinary(file: File): Boolean {
    val probe = ByteArray(8000)
    val read = file.inputStream().use { it.read(probe) }
    return (0 until read.coerceAtLeast(0)).any { probe[it] == 0.toByte() }
}

private sealed interface FileLoadResult {
    data class Text(val content: String) : FileLoadResult
    object Binary : FileLoadResult
    data class Unreadable(val message: String) : FileLoadResult
}

private fun loadFile(file: File): FileLoadResult {
    if (!file.isFile) return FileLoadResult.Unreadable("File no longer exists.")
    return runCatching {
        if (looksBinary(file)) FileLoadResult.Binary else FileLoadResult.Text(file.readText())
    }.getOrElse { e -> FileLoadResult.Unreadable(e.message ?: "Could not read file.") }
}

@Composable
fun FileContentScreen(
    filePath: String,
    viewModel: EditorViewModel? = null
) {
    val file = File(filePath)
    // Re-probed only when filePath changes, not on every recomposition -
    // loadFile() reads the file from disk.
    val loadResult = remember(filePath) { loadFile(file) }

    if (loadResult !is FileLoadResult.Text) {
        Column(
            modifier = Modifier.fillMaxSize().padding(16.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(file.name, style = MaterialTheme.typography.headlineMedium)
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                when (loadResult) {
                    is FileLoadResult.Binary ->
                        "This looks like a binary file. IDEaz's editor only supports text " +
                            "files - opening it here would corrupt its contents on save."
                    is FileLoadResult.Unreadable -> loadResult.message
                    is FileLoadResult.Text -> "" // unreachable
                },
                style = MaterialTheme.typography.bodyMedium,
            )
        }
        return
    }

    var fileContent by remember(filePath) { mutableStateOf(loadResult.content) }

    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(file.name, style = MaterialTheme.typography.headlineMedium)
            AzButton(
                onClick = { file.writeText(fileContent) },
                shape = AzButtonShape.NONE,
                text = "Save")
            }


        Spacer(modifier = Modifier.height(16.dp))

        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { context ->
                EditorSetup.ensureInitialized(context)
                CodeEditor(context).apply {
                    setText(fileContent)
                    val ext = file.extension
                    try {
                        setEditorLanguage(EditorSetup.createLanguage(ext))
                    } catch (e: Exception) {
                        setEditorLanguage(EmptyLanguage())
                    }
                    subscribeAlways(io.github.rosemoe.sora.event.ContentChangeEvent::class.java) {
                        val newText = this.text.toString()
                        fileContent = newText
                        if (file.extension == "kt") {
                            viewModel?.onCodeChange(newText)
                        }
                    }
                }
            },
            update = { editor ->
                if (editor.text.toString() != fileContent) {
                    editor.setText(fileContent)
                }
            }
        )
    }
}
