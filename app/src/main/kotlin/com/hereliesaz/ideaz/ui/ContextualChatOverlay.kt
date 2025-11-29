package com.hereliesaz.ideaz.ui

import android.graphics.Rect
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
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
    val logs by viewModel.filteredLog.collectAsState()
    val scrollState = androidx.compose.foundation.lazy.rememberLazyListState()

    // Auto-scroll to bottom
    LaunchedEffect(logs.size) {
        if (logs.isNotEmpty()) {
            scrollState.animateScrollToItem(logs.size - 1)
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {

        // Close Button (Above)
        // Ensure it doesn't go off-screen top
        val closeButtonY = (rect.top - (50 * density.density).toInt()).coerceAtLeast(0)

        AzButton(
            onClick = onClose,
            text = "X",
            shape = AzButtonShape.CIRCLE,
            modifier = Modifier
                .offset { IntOffset(rect.right - (40 * density.density).toInt(), closeButtonY) } // Top Right of rect
        )

        // Chat Display (Inside Rect)
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
                 items(logs) { log ->
                     Text(
                         text = log,
                         color = Color.White,
                         modifier = Modifier.padding(vertical = 2.dp)
                     )
                 }
             }
        }

        // Input Field (Below)
        // Ensure it doesn't go off-screen bottom?
        // AzTextBox handles its own height?
        AzTextBox(
            modifier = Modifier
                .offset { IntOffset(rect.left, rect.bottom) }
                .width(with(density) { rect.width().toDp() }),
            hint = "Ask AI...",
            onSubmit = { text ->
                viewModel.submitContextualPrompt(text)
            },
            submitButtonContent = { Text("Send") }
        )
    }
}
