package com.hereliesaz.ideaz.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.hereliesaz.aznavrail.AzTextBox

@Composable
fun PromptPopup(
    onDismiss: () -> Unit,
    onSubmit: (String) -> Unit
) {
    Dialog(onDismissRequest = onDismiss) {
        Card {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("Enter your prompt")
                Spacer(modifier = Modifier.height(8.dp))
                AzTextBox(
                    hint = "Your prompt...",
                    onSubmit = {
                        if (it.isNotBlank()) {
                            onSubmit(it)
                        }
                    },
                    submitButtonContent = {
                        Text("Submit")
                    }
                )
            }
        }
    }
}
