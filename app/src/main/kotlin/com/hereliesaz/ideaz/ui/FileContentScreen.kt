package com.hereliesaz.ideaz.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.hereliesaz.aznavrail.AzButton
import com.hereliesaz.aznavrail.model.AzButtonShape
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

        OutlinedTextField(
            value = fileContent,
            onValueChange = { fileContent = it },
            modifier = Modifier.fillMaxSize()
        )
    }
}
