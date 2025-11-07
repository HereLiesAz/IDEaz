package com.hereliesaz.ideaz.ui

import android.widget.Toast
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.hereliesaz.ideaz.api.Session
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.text.input.PasswordVisualTransformation

@Composable
fun SettingsScreen(
    settingsViewModel: SettingsViewModel = viewModel(),
    sessions: List<Session> // Accept the sessions list
) {
    val context = LocalContext.current
    var apiKey by remember { mutableStateOf(settingsViewModel.getApiKey(context) ?: "") }

    Column {
        Spacer(modifier = Modifier.weight(0.2f))

        Column(
            modifier = Modifier
                .weight(0.8f)
                .padding(all = 8.dp)
                .verticalScroll(rememberScrollState())
        ) {
            Text("Settings")
            Spacer(modifier = Modifier.height(16.dp))
            TextField(
                value = apiKey,
                onValueChange = { apiKey = it },
                label = {
                    Text("Jules API Key")
                },
                // Use PasswordVisualTransformation to mask the key
                visualTransformation = PasswordVisualTransformation()
            )
            Spacer(modifier = Modifier.height(16.dp))
            Button(onClick = {
                settingsViewModel.saveApiKey(context, apiKey)
                Toast.makeText(context, "API Key Saved", Toast.LENGTH_SHORT).show()
            }) {
                Text("Save")
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Display the list of sessions
            Text("Active Sessions")
            if (sessions.isEmpty()) {
                Text("No active sessions found.")
            } else {
                sessions.forEach {
                    Text(it.name)
                }
            }
        }
    }
}