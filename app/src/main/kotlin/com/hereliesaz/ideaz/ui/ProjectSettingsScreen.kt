package com.hereliesaz.ideaz.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun ProjectSettingsScreen(
    viewModel: MainViewModel
) {
    var appName by remember { mutableStateOf("HelloWorld") }
    var namespace by remember { mutableStateOf("com.example.helloworld") }
    var initialPrompt by remember { mutableStateOf("") }

    Column {
        Spacer(modifier = Modifier.weight(0.2f))
        Column(
            modifier = Modifier
                .weight(0.8f)
                .padding(all = 8.dp)
                .verticalScroll(rememberScrollState())
        ) {
            Text("Project Settings")
            Spacer(modifier = Modifier.height(16.dp))

            TextField(
                value = appName,
                onValueChange = { appName = it },
                label = { Text("App Name") }
            )
            Spacer(modifier = Modifier.height(8.dp))

            TextField(
                value = namespace,
                onValueChange = { namespace = it },
                label = { Text("Namespace") }
            )
            Spacer(modifier = Modifier.height(16.dp))

            Text("Initial Prompt")
            TextField(
                value = initialPrompt,
                onValueChange = { initialPrompt = it },
                label = { Text("Describe your app...") },
                modifier = Modifier.height(150.dp)
            )
            Spacer(modifier = Modifier.height(16.dp))

            Button(onClick = {
                // TODO: Wire appName and namespace to build process

                // Send the initial prompt to the AI
                viewModel.sendPrompt(initialPrompt)
            }) {
                Text("Create Project & Build")
            }
        }
    }
}