package com.hereliesaz.ideaz.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.hereliesaz.ideaz.ai.ChatMessage
import com.hereliesaz.ideaz.ai.local.LocalProviderFailure
import com.hereliesaz.ideaz.ui.delegates.LocalEditReviewState
import com.hereliesaz.ideaz.ui.delegates.LocalEditReviewStatus
import com.hereliesaz.ideaz.ui.delegates.LocalCloudConsultState
import com.hereliesaz.ideaz.ui.delegates.LocalCloudConsultStatus

/**
 * Chat UI for the AI tab in [IdeBottomSheet].
 *
 * Displays the ordered [messages] history with user bubbles right-aligned and
 * model bubbles left-aligned. Shows a loading spinner when [isLoading] is true.
 *
 * @param messages  Ordered conversation history (user + model turns).
 * @param failure Structured provider failure rendered outside model history.
 * @param editReview Validated on-device changes awaiting approval or eligible for undo.
 * @param cloudConsult Exact cloud payload awaiting explicit one-shot consent.
 * @param isLoading True while waiting for an AI response.
 * @param viewModel MainViewModel to handle message sending.
 */
@Composable
fun AiChatTab(
    messages: List<ChatMessage>,
    failure: LocalProviderFailure?,
    editReview: LocalEditReviewState?,
    cloudConsult: LocalCloudConsultState?,
    isLoading: Boolean,
    viewModel: MainViewModel,
    modifier: Modifier = Modifier
) {
    val listState = rememberLazyListState()

    // Same provider sendChatMessage() actually routes to (KEY_AI_ASSIGNMENT_DEFAULT,
    // falling back to Gemini) - model replies were previously labeled "Gemini"
    // unconditionally, including for on-device and every other provider.
    val chatModelId = viewModel.settingsViewModel.getAiAssignment(SettingsViewModel.KEY_AI_ASSIGNMENT_DEFAULT)
    val providerDisplayName = (AiModels.findById(chatModelId) ?: AiModels.GEMINI).displayName

    // Auto-scroll to bottom when new messages arrive
    LaunchedEffect(messages.size, failure?.diagnosticId, editReview?.status, cloudConsult?.status) {
        when {
            failure != null || editReview != null || cloudConsult != null -> listState.scrollToItem(messages.size)
            messages.isNotEmpty() -> listState.scrollToItem(messages.size - 1)
        }
    }

    Column(modifier = modifier.fillMaxSize()) {

        // Message list
        LazyColumn(
            state = listState,
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp)
        ) {
            itemsIndexed(messages, key = { index, _ -> index }) { _, msg ->
                ChatBubble(msg, providerDisplayName)
                Spacer(modifier = Modifier.height(6.dp))
            }

            failure?.let { localFailure ->
                item(key = "local-provider-failure-${localFailure.diagnosticId}") {
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.errorContainer,
                            contentColor = MaterialTheme.colorScheme.onErrorContainer,
                        ),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Column(Modifier.padding(12.dp)) {
                            Text("On-device provider stopped", fontWeight = FontWeight.Bold)
                            Text(localFailure.message, style = MaterialTheme.typography.bodySmall)
                            Text(
                                buildString {
                                    append(if (localFailure.retryable) "Retry available" else "Retry unavailable")
                                    append(" · Cloud fallback ")
                                    append(if (localFailure.cloudFallbackAllowed) "eligible with approval" else "blocked")
                                    append(" · ").append(localFailure.diagnosticId)
                                },
                                style = MaterialTheme.typography.labelSmall,
                            )
                            if (localFailure.retryable) {
                                Button(
                                    onClick = { viewModel.retryLocalFailure(localFailure.diagnosticId) },
                                ) {
                                    Text("Retry on device")
                                }
                            }
                            if (localFailure.cloudFallbackAllowed) {
                                Text(
                                    "Gemini fallback sends this conversation and project context to Google's cloud once. " +
                                        "Nothing is sent unless you approve.",
                                    style = MaterialTheme.typography.bodySmall,
                                )
                                if (viewModel.hasCloudFallbackCredential()) {
                                    Button(
                                        onClick = { viewModel.approveCloudFallback(localFailure.diagnosticId) },
                                    ) {
                                        Text("Approve Gemini once")
                                    }
                                } else {
                                    Text(
                                        "Add a Gemini API key in Settings to enable fallback.",
                                        style = MaterialTheme.typography.labelSmall,
                                    )
                                }
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(6.dp))
                }
            }

            editReview?.let { state ->
                item(key = "local-edit-${state.approval.review.checkpoint.checkpointId}") {
                    LocalEditReviewCard(state, viewModel)
                    Spacer(modifier = Modifier.height(6.dp))
                }
            }

            cloudConsult?.let { state ->
                item(key = "local-cloud-consult-${state.request.requestId}") {
                    LocalCloudConsultCard(state, viewModel)
                    Spacer(modifier = Modifier.height(6.dp))
                }
            }

            // Loading indicator as the last list item
            if (isLoading) {
                item {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            strokeWidth = 2.dp
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Thinking…",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                        )
                    }
                }
            }
        }

        // Reuse the existing chat input component
        ContextlessChatInput(
            modifier = Modifier.fillMaxWidth(),
            viewModel = viewModel
        )
    }
}

@Composable
private fun LocalCloudConsultCard(state: LocalCloudConsultState, viewModel: MainViewModel) {
    val request = state.request
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("Local model requests ${request.provider} advice", fontWeight = FontWeight.Bold)
            Text(
                "Only the text below will be sent to Google's Gemini API once. " +
                    "No files, images, history, repo map, credentials, or IDE tools are included.",
                style = MaterialTheme.typography.bodySmall,
            )
            Text("Exact Gemini prompt", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
            Text(request.prompt, style = MaterialTheme.typography.bodySmall)
            Text(
                "${request.model} · ${request.byteCount} bytes · request ${request.requestId.take(8)}",
                style = MaterialTheme.typography.labelSmall,
            )
            state.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            if (state.status == LocalCloudConsultStatus.PROCESSING) {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            } else {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        enabled = state.advice != null || viewModel.hasCloudFallbackCredential(),
                        onClick = { viewModel.approveLocalCloudConsult(request.requestId) },
                    ) {
                        Text(if (state.advice == null) "Send once" else "Retry locally")
                    }
                    OutlinedButton(onClick = { viewModel.rejectLocalCloudConsult(request.requestId) }) {
                        Text("Keep local")
                    }
                }
                if (state.advice == null && !viewModel.hasCloudFallbackCredential()) {
                    Text("Add a Gemini API key in Settings to enable consultation.", style = MaterialTheme.typography.labelSmall)
                }
            }
        }
    }
}

@Composable
private fun LocalEditReviewCard(state: LocalEditReviewState, viewModel: MainViewModel) {
    val review = state.approval.review
    val checkpointId = review.checkpoint.checkpointId
    val source = state.approval.source
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp)) {
            Text(
                when (state.status) {
                    LocalEditReviewStatus.PENDING -> "Review $source edit"
                    LocalEditReviewStatus.PROCESSING -> "Applying edit decision…"
                    LocalEditReviewStatus.APPROVED -> "$source edit approved"
                    LocalEditReviewStatus.REJECTED -> "$source edit rejected"
                    LocalEditReviewStatus.UNDONE -> "$source edit undone"
                },
                fontWeight = FontWeight.Bold,
            )
            Text(
                review.changedFiles.joinToString(separator = "\n") { "• $it" },
                style = MaterialTheme.typography.bodySmall,
            )
            Text(
                if (review.validationErrors.isEmpty()) {
                    "Validated · checkpoint ${checkpointId.take(8)}"
                } else {
                    "Validation failed · checkpoint ${checkpointId.take(8)}"
                },
                style = MaterialTheme.typography.labelSmall,
            )
            review.validationErrors.forEach { error ->
                Text("• $error", style = MaterialTheme.typography.bodySmall)
            }
            if (!review.rollbackAllowed) {
                Text(
                    "The app stopped during a file write. Review the files; automatic rollback is disabled " +
                        "because IDEaz cannot distinguish later edits from the interrupted write.",
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            when (state.status) {
                LocalEditReviewStatus.PENDING -> Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = { viewModel.approveLocalEdit(checkpointId) }) {
                        Text(if (review.validationErrors.isEmpty()) "Approve & reload" else "Recheck files")
                    }
                    if (review.rollbackAllowed) {
                        OutlinedButton(onClick = { viewModel.rejectLocalEdit(checkpointId) }) {
                            Text("Reject")
                        }
                    }
                }
                LocalEditReviewStatus.APPROVED -> if (review.rollbackAllowed) {
                    OutlinedButton(onClick = { viewModel.undoLocalEdit(checkpointId) }) {
                        Text("Undo edit")
                    }
                }
                LocalEditReviewStatus.PROCESSING -> Unit
                LocalEditReviewStatus.REJECTED, LocalEditReviewStatus.UNDONE -> Unit
            }
        }
    }
}

@Composable
private fun ChatBubble(msg: ChatMessage, providerDisplayName: String) {
    val isUser = msg.role == "user"
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(0.85f),
            horizontalAlignment = if (isUser) Alignment.End else Alignment.Start
        ) {
            Text(
                text = if (isUser) "You" else providerDisplayName,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f)
            )
            Text(
                text = msg.content,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
    }
}
