package com.hereliesaz.ideaz.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.hereliesaz.aznavrail.AzTextBox
import com.hereliesaz.ideaz.ai.AttachmentResolver
import com.hereliesaz.ideaz.models.ProjectType
import com.hereliesaz.ideaz.ui.widget.Attachment
import com.hereliesaz.ideaz.ui.widget.PromptInputAttachmentRow
import kotlinx.coroutines.launch

@Composable
fun ContextlessChatInput(
    modifier: Modifier = Modifier,
    viewModel: MainViewModel,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var attachments by remember { mutableStateOf<List<Attachment>>(emptyList()) }

    val defaultModelId = viewModel.settingsViewModel
        .getAiAssignment(SettingsViewModel.KEY_AI_ASSIGNMENT_DEFAULT)
    val defaultModel = AiModels.findById(defaultModelId) ?: AiModels.GEMINI
    val providerSupportsImages = defaultModel.supportsImages

    Column(modifier = modifier.fillMaxWidth()) {
        AzTextBox(
            modifier = Modifier
                .fillMaxWidth()
                .padding(8.dp),
            hint = "Contextless Prompt...",
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
                    viewModel.sendChatMessage(annotated, resolved.referenceParts)
                    attachments = emptyList()
                }
            },
            submitButtonContent = {
                Icon(Icons.AutoMirrored.Filled.Send, contentDescription = "Send")
            }
        )
        PromptInputAttachmentRow(
            attachments = attachments,
            onAttachmentsChanged = { attachments = it },
            providerSupportsImages = providerSupportsImages,
            modifier = Modifier.padding(horizontal = 8.dp),
        )
    }
}
