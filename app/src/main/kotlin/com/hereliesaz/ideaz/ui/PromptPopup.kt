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
    // Dialog handles the dimming (scrim) and touch blocking by default.
    // If this was appearing "over the project screen" it means the user clicked 'Prompt'
    // in the nav rail while on the project screen.
    // The Dialog composable is modal, so it naturally blocks interaction with the background.
    // This is expected behavior for a popup.
    // If the user found it "unresponsive", it's because this dialog was open.
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
