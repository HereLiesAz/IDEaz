package com.hereliesaz.ideaz.ui

import android.graphics.Rect
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.hereliesaz.aznavrail.AzButton
import com.hereliesaz.aznavrail.AzTextBox
import com.hereliesaz.aznavrail.model.AzButtonShape

@Composable
fun ContextualChatOverlay(
    rect: Rect,
    viewModel: MainViewModel,
    onClose: () -> Unit
) {
    val density = LocalDensity.current
    // Show the actual AI conversation, not the global log feed. The bottom-sheet
    // Chat tab also reads stateDelegate.chatMessages, so what the user sees here
    // mirrors what's there.
    val chatMessages by viewModel.stateDelegate.chatMessages.collectAsState()
    val scrollState = androidx.compose.foundation.lazy.rememberLazyListState()

    // Auto-scroll to bottom
    LaunchedEffect(chatMessages.size) {
        if (chatMessages.isNotEmpty()) {
            scrollState.animateScrollToItem(chatMessages.size - 1)
        }
    }

    // When rect is empty (web-context tap), show a full-width bottom panel.
    // When rect is a real screen-space selection, show the positioned overlay.
    val isEmpty = rect.isEmpty

    Box(modifier = Modifier.fillMaxSize()) {
        if (isEmpty) {
            // Web context mode: a bottom-anchored panel, 40% screen height
            androidx.compose.foundation.layout.Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .fillMaxHeight(0.4f)
                    .align(androidx.compose.ui.Alignment.BottomCenter)
                    .background(Color.Black.copy(alpha = 0.9f))
                    .border(2.dp, Color.Green)
                    .padding(8.dp)
            ) {
                // Close button row
                androidx.compose.foundation.layout.Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = androidx.compose.foundation.layout.Arrangement.End
                ) {
                    AzButton(
                        onClick = onClose,
                        text = "X",
                        shape = AzButtonShape.CIRCLE,
                        contentPadding = PaddingValues(0.dp)
                    )
                }

                // Chat log (scrollable)
                LazyColumn(
                    state = scrollState,
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .padding(vertical = 4.dp)
                ) {
                    items(chatMessages) { msg ->
                        val prefix = if (msg.role == "user") "You: " else "Gemini: "
                        Text(
                            text = prefix + msg.content,
                            color = Color.White,
                            modifier = Modifier.padding(vertical = 2.dp)
                        )
                    }
                }

                // Input field
                AzTextBox(
                    modifier = Modifier.fillMaxWidth(),
                    hint = "Ask AI...",
                    onSubmit = { text -> viewModel.submitContextualPrompt(text) },
                    submitButtonContent = { Icon(Icons.AutoMirrored.Filled.Send, contentDescription = "Send", tint = Color.Green) },
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send)
                )
            }
        } else {
            // Android selection mode: positioned overlay anchored to the selection rect
            val closeButtonY = (rect.top - (50 * density.density).toInt()).coerceAtLeast(0)

            AzButton(
                onClick = onClose,
                text = "X",
                shape = AzButtonShape.CIRCLE,
                contentPadding = PaddingValues(0.dp),
                modifier = Modifier
                    .offset { IntOffset(rect.right - (40 * density.density).toInt(), closeButtonY) }
            )

            Box(
                modifier = Modifier
                    .offset { IntOffset(rect.left, rect.top) }
                    .width(with(density) { rect.width().toDp() })
                    .height(with(density) { rect.height().toDp() })
                    .background(Color.Black.copy(alpha = 0.8f))
                    .border(2.dp, Color.Green)
                    .padding(4.dp)
            ) {
                LazyColumn(
                    state = scrollState,
                    modifier = Modifier.fillMaxSize()
                ) {
                    items(chatMessages) { msg ->
                        val prefix = if (msg.role == "user") "You: " else "Gemini: "
                        Text(
                            text = prefix + msg.content,
                            color = Color.White,
                            modifier = Modifier.padding(vertical = 2.dp)
                        )
                    }
                }
            }

            AzTextBox(
                modifier = Modifier
                    .offset { IntOffset(rect.left, rect.bottom) }
                    .width(with(density) { rect.width().toDp() }),
                hint = "Ask AI...",
                onSubmit = { text -> viewModel.submitContextualPrompt(text) },
                submitButtonContent = { Icon(Icons.AutoMirrored.Filled.Send, contentDescription = "Send", tint = Color.Green) },
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send)
            )
        }
    }
}