package com.hereliesaz.ideaz.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import java.io.File

@Composable
fun FileContentScreen(
    filePath: String
) {
    val file = File(filePath)
    val fileContent = file.readText()

    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Text(file.name, style = MaterialTheme.typography.headlineMedium)

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = fileContent,
            modifier = Modifier.verticalScroll(rememberScrollState())
        )
    }
}
