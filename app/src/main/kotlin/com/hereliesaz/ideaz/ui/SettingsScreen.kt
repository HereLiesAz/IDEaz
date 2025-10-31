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

@Composable
fun SettingsScreen(
    settingsViewModel: SettingsViewModel = viewModel()
) {
    val context = LocalContext.current
    var apiKey by remember { mutableStateOf(settingsViewModel.getApiKey(context) ?: "") }

    Column {
        // Re-added the top 20% spacer
        Spacer(modifier = Modifier.weight(0.2f))

        Column(modifier = Modifier.weight(0.8f)) {
            Text("Settings")
            Spacer(modifier = Modifier.height(16.dp))
            TextField(
                value = apiKey,
                onValueChange = { apiKey = it },
                label = {
                    Text("Jules API Key") }
            )
            Spacer(modifier = Modifier.height(16.dp))
            Button(onClick = {
                settingsViewModel.saveApiKey(context, apiKey)
                Toast.makeText(context, "API Key Saved", Toast.LENGTH_SHORT).show()
            }) {
                Text("Save")

            }
        }
    }
}