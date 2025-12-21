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
import java.io.File

@Composable
fun FileContentScreen(
    filePath: String
) {
    val file = File(filePath)
    var fileContent by remember { mutableStateOf(file.readText()) }

    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Spacer(modifier = Modifier.height(64.dp))

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
                        fileContent = this.text.toString()
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
