package com.hereliesaz.ideaz.ui

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.hereliesaz.aznavrail.AzTextBox

@Composable
fun ContextlessChatInput(
    modifier: Modifier = Modifier,
    onSend: (String) -> Unit
) {
    AzTextBox(
        modifier = modifier
            .fillMaxWidth()
            .padding(8.dp),
        hint = "Contextless Prompt...",
        onSubmit = {
            if (it.isNotBlank()) {
                onSend(it)
            }
        },
        submitButtonContent = {
            Icon(Icons.AutoMirrored.Filled.Send, contentDescription = "Send")
        }
    )
}
