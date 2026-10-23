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
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import com.hereliesaz.aznavrail.AzButton
import com.hereliesaz.aznavrail.model.AzButtonShape
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import com.hereliesaz.ideaz.ai.ChatMessage
import com.hereliesaz.ideaz.ui.delegates.EditReviewState
import com.hereliesaz.ideaz.ui.delegates.EditReviewStatus

/**
 * Chat UI for the AI tab in [IdeBottomSheet].
 *
 * Displays the ordered [messages] history with user bubbles right-aligned and
 * model bubbles left-aligned. Shows a loading spinner when [isLoading] is true.
 *
 * @param messages  Ordered conversation history (user + model turns).
 * @param failure Provider failure rendered outside model history.
 * @param editReview Validated AI changes awaiting approval or eligible for undo.
 * @param isLoading True while waiting for an AI response.
 * @param viewModel MainViewModel to handle message sending.
 */
@Composable
fun AiChatTab(
    messages: List<ChatMessage>,
    failure: String?,
    editReview: EditReviewState?,
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
    LaunchedEffect(messages.size, failure, editReview?.status) {
        when {
            failure != null || editReview != null -> listState.scrollToItem(messages.size)
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
                ChatBubble(msg, providerDisplayName, onOpenSettings = { viewModel.requestOpenSettings() })
                Spacer(modifier = Modifier.height(6.dp))
            }

            failure?.let { message ->
                item(key = "chat-failure") {
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.errorContainer,
                            contentColor = MaterialTheme.colorScheme.onErrorContainer,
                        ),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Column(Modifier.padding(12.dp).semantics { liveRegion = LiveRegionMode.Polite }) {
                            Text(message, style = MaterialTheme.typography.bodySmall)
                            if (mentionsSettings(message)) {
                                Spacer(Modifier.height(8.dp))
                                AzButton(
                                    onClick = { viewModel.requestOpenSettings() },
                                    text = "Open Settings",
                                    shape = AzButtonShape.RECTANGLE,
                                )
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(6.dp))
                }
            }

            editReview?.let { state ->
                item(key = "ai-edit-${state.approval.review.checkpoint.checkpointId}") {
                    EditReviewCard(state, viewModel)
                    Spacer(modifier = Modifier.height(6.dp))
                }
            }


            // Loading indicator as the last list item
            if (isLoading) {
                item {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp)
                            .semantics { liveRegion = LiveRegionMode.Polite },
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
private fun EditReviewCard(state: EditReviewState, viewModel: MainViewModel) {
    val review = state.approval.review
    val checkpointId = review.checkpoint.checkpointId
    val source = state.approval.source
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp)) {
            Text(
                when (state.status) {
                    EditReviewStatus.PENDING -> "Review $source edit"
                    EditReviewStatus.PROCESSING -> "Applying edit decision…"
                    EditReviewStatus.APPROVED -> "$source edit approved"
                    EditReviewStatus.REJECTED -> "$source edit rejected"
                    EditReviewStatus.UNDONE -> "$source edit undone"
                },
                fontWeight = FontWeight.Bold,
            )
            Text(
                review.changedFiles.joinToString(separator = "\n") { "• $it" },
                style = MaterialTheme.typography.bodySmall,
            )
            var showChanges by remember(checkpointId) { mutableStateOf(false) }
            val diffs by produceState<Map<String, String>>(emptyMap(), checkpointId, showChanges) {
                value = if (showChanges) {
                    withContext(Dispatchers.IO) {
                        review.changedFiles.take(MAX_DIFF_FILES).associateWith {
                            viewModel.editDiff(review.checkpoint, it)
                        }
                    }
                } else {
                    emptyMap()
                }
            }
            if (review.changedFiles.isNotEmpty()) {
                TextButton(onClick = { showChanges = !showChanges }) {
                    Text(if (showChanges) "Hide changes" else "Show changes")
                }
            }
            if (showChanges) {
                diffs.forEach { (file, diff) ->
                    Text(file, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                    Text(
                        diff,
                        style = MaterialTheme.typography.bodySmall,
                        fontFamily = FontFamily.Monospace,
                    )
                }
                if (review.changedFiles.size > MAX_DIFF_FILES) {
                    Text(
                        "…and ${review.changedFiles.size - MAX_DIFF_FILES} more file(s)",
                        style = MaterialTheme.typography.labelSmall,
                    )
                }
            }
            Text(
                if (review.validationErrors.isEmpty()) {
                    "Checked. Reject puts your files back as they were."
                } else {
                    "Checks failed. Reject puts your files back as they were."
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
            if (state.restoreConflict && state.status != EditReviewStatus.PROCESSING) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    AzButton(
                        onClick = { viewModel.forceRestoreEdit(checkpointId) },
                        text = "Restore anyway",
                        shape = AzButtonShape.RECTANGLE,
                    )
                    AzButton(
                        onClick = { viewModel.keepCurrentFiles(checkpointId) },
                        text = "Keep current files",
                        shape = AzButtonShape.RECTANGLE,
                    )
                }
            } else when (state.status) {
                EditReviewStatus.PENDING -> Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    AzButton(
                        onClick = { viewModel.approveEdit(checkpointId) },
                        text = if (review.validationErrors.isEmpty()) "Approve & reload" else "Recheck files",
                        shape = AzButtonShape.RECTANGLE,
                    )
                    if (review.rollbackAllowed) {
                        AzButton(
                            onClick = { viewModel.rejectEdit(checkpointId) },
                            text = "Reject",
                            shape = AzButtonShape.RECTANGLE,
                        )
                    }
                }
                EditReviewStatus.APPROVED -> if (review.rollbackAllowed) {
                    AzButton(
                        onClick = { viewModel.undoEdit(checkpointId) },
                        text = "Undo edit",
                        shape = AzButtonShape.RECTANGLE,
                    )
                }
                EditReviewStatus.PROCESSING -> Unit
                EditReviewStatus.REJECTED, EditReviewStatus.UNDONE -> Unit
            }
        }
    }
}

@Composable
private fun ChatBubble(msg: ChatMessage, fallbackProviderDisplayName: String, onOpenSettings: () -> Unit) {
    val isUser = msg.role == "user"
    // msg.provider is set at the point each reply was actually produced, so
    // switching the "Default" AI assignment mid-conversation no longer
    // relabels every earlier reply as having come from the new provider.
    // Older/synthetic messages that predate that field fall back to the
    // current provider name, same as before this existed.
    val label = when {
        isUser -> "You"
        msg.isError -> "Error"
        else -> msg.provider ?: fallbackProviderDisplayName
    }
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(0.85f),
            horizontalAlignment = if (isUser) Alignment.End else Alignment.Start
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                color = if (msg.isError) {
                    MaterialTheme.colorScheme.error
                } else {
                    MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f)
                }
            )
            Text(
                text = msg.content,
                style = MaterialTheme.typography.bodySmall,
                color = if (msg.isError) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface
            )
            if (msg.isError && mentionsSettings(msg.content)) {
                Spacer(Modifier.height(4.dp))
                AzButton(onClick = onOpenSettings, text = "Open Settings", shape = AzButtonShape.RECTANGLE)
            }
        }
    }
}

/** Most files whose before/after text the review card expands. */
private const val MAX_DIFF_FILES = 10

/** True for an error that tells the user to fix something in Settings, so the card can offer a button. */
private fun mentionsSettings(message: String): Boolean = message.contains("Settings")
