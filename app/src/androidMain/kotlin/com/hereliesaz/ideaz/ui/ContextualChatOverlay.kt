package com.hereliesaz.ideaz.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import com.hereliesaz.aznavrail.AzButton
import com.hereliesaz.aznavrail.AzTextBox
import com.hereliesaz.ideaz.ui.delegates.EditReviewStatus

/**
 * The panel that opens when the user taps an element in the preview: what they
 * tapped, the conversation about it, and — crucially — the approve/reject
 * controls for whatever the AI changes.
 *
 * This used to render only [chatMessages] and had no approval controls at all,
 * which broke the flagship gesture end to end: the AI's edit landed in a PENDING
 * review the panel could not display or resolve, so the conversation went silent,
 * the preview never changed, and every retry was discarded. The Approve button
 * existed four interactions away, in the bottom sheet, on tab six of six — the
 * surface the design doc calls an escape hatch.
 *
 * It also positioned itself from a screen-space rect. A tap produced a 1x1 px
 * rect, so on the path where the element lookup did not come back the whole panel
 * rendered one pixel wide. There is no rect any more; the panel is always the
 * bottom sheet it was always meant to be.
 */
@Composable
fun ContextualChatOverlay(
    viewModel: MainViewModel,
    onClose: () -> Unit,
) {
    val chatMessages by viewModel.stateDelegate.chatMessages.collectAsState()
    val chatFailure by viewModel.stateDelegate.chatFailure.collectAsState()
    val editReview by viewModel.stateDelegate.editReview.collectAsState()
    val isChatLoading by viewModel.stateDelegate.isChatLoading.collectAsState()
    val scrollState = rememberLazyListState()

    val element = remember { viewModel.selectionDelegate.pendingContextInfo }

    LaunchedEffect(chatMessages.size) {
        if (chatMessages.isNotEmpty()) scrollState.animateScrollToItem(chatMessages.size - 1)
    }

    val pending = editReview?.status == EditReviewStatus.PENDING
    val processing = editReview?.status == EditReviewStatus.PROCESSING

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .fillMaxHeight(0.45f)
            .background(MaterialTheme.colorScheme.surface)
            .padding(12.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = elementLabel(element),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
            )
            AzButton(onClick = onClose, text = "Close")
        }

        sourceLabel(element)?.let {
            Text(
                text = it,
                style = MaterialTheme.typography.bodySmall,
                fontFamily = FontFamily.Monospace,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 2.dp),
            )
        }

        LazyColumn(
            state = scrollState,
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .padding(vertical = 8.dp),
        ) {
            items(chatMessages) { msg ->
                Text(
                    text = "${if (msg.role == "user") "You" else (msg.provider ?: "AI")}: ${msg.content}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.padding(vertical = 2.dp),
                )
            }
        }

        chatFailure?.let {
            Text(
                text = it,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.padding(bottom = 4.dp),
            )
        }

        when {
            // The whole point of this rewrite: resolve the edit where it was made.
            pending || processing -> {
                val review = editReview!!.approval.review
                Text(
                    text = "${editReview!!.approval.source} changed " +
                        "${review.changedFiles.size} file(s). Review before it reloads.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                review.changedFiles.take(6).forEach {
                    Text(
                        text = "  $it",
                        style = MaterialTheme.typography.bodySmall,
                        fontFamily = FontFamily.Monospace,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Row(
                    modifier = Modifier.fillMaxWidth().wrapContentHeight(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    AzButton(
                        onClick = { viewModel.approveEdit(review.checkpoint.checkpointId) },
                        text = if (processing) "Working…" else "Approve & reload",
                    )
                    AzButton(
                        onClick = { viewModel.rejectEdit(review.checkpoint.checkpointId) },
                        text = "Reject",
                    )
                }
            }

            isChatLoading -> {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(modifier = Modifier.padding(end = 8.dp))
                    Text("Thinking…", style = MaterialTheme.typography.bodySmall)
                }
            }

            else -> {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    AzTextBox(
                        modifier = Modifier.weight(1f),
                        hint = "What should this element do?",
                        onSubmit = { text ->
                            if (text.isNotBlank()) viewModel.submitContextualPrompt(text)
                        },
                    )
                }
            }
        }
    }
}

/** A short human label for the tapped element, read out of the bridge payload. */
internal fun elementLabel(element: String?): String {
    if (element.isNullOrBlank()) return "Selected element"
    val tag = Regex("\"tagName\"\\s*:\\s*\"([^\"]+)\"").find(element)?.groupValues?.get(1)
    return if (tag != null) "Selected <$tag>" else "Selected element"
}

/**
 * `file:line` when the bridge resolved one. This is the payoff of enabling
 * Babel's jsx-source transform: the element knows where it came from, so the
 * user can see it and the model is told to go straight there.
 */
internal fun sourceLabel(element: String?): String? {
    if (element.isNullOrBlank()) return null
    // The line number was never reachable in a single pass: a lazy "any
    // chars" quantifier immediately followed by an *optional* group lets the
    // regex engine satisfy the optional group by matching zero occurrences
    // right where the lazy quantifier starts, since nothing after it is
    // mandatory - it never gets pressured to backtrack forward to where
    // "lineNumber" actually appears, so that capture group was always empty.
    // Extracting the source block first and searching each key independently
    // (key order in the payload isn't guaranteed anyway) sidesteps that.
    val block = Regex("\"source\"\\s*:\\s*\\{([^}]*)}").find(element)?.groupValues?.get(1) ?: return null
    val fileValue = Regex("\"fileName\"\\s*:\\s*\"([^\"]+)\"").find(block)?.groupValues?.get(1) ?: return null
    val file = fileValue.substringAfterLast('/')
    val line = Regex("\"lineNumber\"\\s*:\\s*(\\d+)").find(block)?.groupValues?.get(1)
    return if (line != null) "$file:$line" else file
}
