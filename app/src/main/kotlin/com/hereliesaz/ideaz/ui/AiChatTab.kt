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

/**
 * Chat UI for the AI tab in [IdeBottomSheet].
 *
 * Displays the ordered [messages] history with user bubbles right-aligned and
 * model bubbles left-aligned. Shows a loading spinner when [isLoading] is true.
 *
 * @param messages  Ordered conversation history (user + model turns).
 * @param isLoading True while waiting for an AI response.
 * @param viewModel MainViewModel to handle message sending.
 */
@Composable
fun AiChatTab(
    messages: List<ChatMessage>,
    isLoading: Boolean,
    viewModel: MainViewModel,
    modifier: Modifier = Modifier
) {
    val listState = rememberLazyListState()

    // Auto-scroll to bottom when new messages arrive
    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) listState.scrollToItem(messages.size - 1)
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
                ChatBubble(msg)
                Spacer(modifier = Modifier.height(6.dp))
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
            viewModel = viewMode,
        )
    }
}

@Composable
private fun ChatBubble(msg: ChatMessage) {
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
                text = if (isUser) "You" else "Gemini",
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
