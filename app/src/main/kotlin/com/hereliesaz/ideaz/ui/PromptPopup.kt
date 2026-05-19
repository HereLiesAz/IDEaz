package com.hereliesaz.ideaz.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.hereliesaz.aznavrail.AzTextBox
import com.hereliesaz.ideaz.ai.AttachmentResolver
import com.hereliesaz.ideaz.models.ProjectType
import com.hereliesaz.ideaz.ui.widget.Attachment
import com.hereliesaz.ideaz.ui.widget.PromptInputAttachmentRow
import kotlinx.coroutines.launch

@Composable
fun PromptPopup(
    onDismiss: () -> Unit,
    viewModel: MainViewModel,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var attachments by remember { mutableStateOf<List<Attachment>>(emptyList()) }

    // Provider capability check for the chip's "Reference" mode.
    val defaultModelId = viewModel.settingsViewModel.getAiAssignment(
        SettingsViewModel.KEY_AI_ASSIGNMENT_OVERLAY
    )
    val defaultModel = AiModels.findById(defaultModelId) ?: AiModels.GEMINI
    val providerSupportsImages = defaultModel.supportsImages

    Dialog(onDismissRequest = onDismiss) {
        Card {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("Enter your prompt")
                Spacer(modifier = Modifier.height(8.dp))
                AzTextBox(
                    hint = "Your prompt...",
                    onSubmit = { text ->
                        if (text.isBlank() && attachments.isEmpty()) return@AzTextBox
                        scope.launch {
                            val appName = viewModel.settingsViewModel.getAppName()
                            val projectDir = if (appName != null) {
                                viewModel.settingsViewModel.getProjectPath(appName)
                            } else null
                            val projectType = ProjectType.fromString(
                                viewModel.settingsViewModel.getProjectType()
                            )

                            val resolved = if (attachments.isNotEmpty() && projectDir != null) {
                                AttachmentResolver.resolve(context, projectDir, projectType, attachments)
                            } else {
                                AttachmentResolver.Resolved(emptyList(), emptyList(), emptyList())
                            }

                            val annotated = buildString {
                                append(text)
                                if (resolved.annotationLines.isNotEmpty()) {
                                    if (isNotEmpty()) append("\n\n")
                                    append("Attached files:\n")
                                    append(resolved.annotationLines.joinToString("\n"))
                                }
                                if (resolved.warnings.isNotEmpty()) {
                                    if (isNotEmpty()) append("\n\n")
                                    append(resolved.warnings.joinToString("\n"))
                                }
                            }
                            viewModel.sendPrompt(annotated, resolved.referenceParts)
                            attachments = emptyList()
                            onDismiss()
                        }
                    },
                    submitButtonContent = {
                        Text("Submit")
                    }
                )
                PromptInputAttachmentRow(
                    attachments = attachments,
                    onAttachmentsChanged = { attachments = it },
                    providerSupportsImages = providerSupportsImages,
                )
            }
        }
    }
}
