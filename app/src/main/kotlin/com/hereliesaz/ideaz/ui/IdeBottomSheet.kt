package com.hereliesaz.ideaz.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.composables.core.BottomSheet
import com.composables.core.BottomSheetState
import com.composables.core.SheetDetent
import kotlinx.coroutines.launch

@Composable
fun IdeBottomSheet(
    sheetState: BottomSheetState,
    viewModel: MainViewModel,
    peekDetent: SheetDetent,
    halfwayDetent: SheetDetent,
    screenHeight: Dp,
    onSendPrompt: (String) -> Unit
) {
    val isHalfwayExpanded = sheetState.currentDetent == halfwayDetent
    val logMessages by viewModel.filteredLog.collectAsState(initial = "")
    val clipboardManager = LocalClipboardManager.current
    val coroutineScope = rememberCoroutineScope()

    // Calculate height based on detent
    // We use the passed screenHeight to match the detent definitions
    val contentHeight = when (sheetState.currentDetent) {
        halfwayDetent -> screenHeight * 0.5f
        peekDetent -> screenHeight * 0.25f // Matches the updated Peek definition
        else -> 0.dp
    }

    // The requested 10% buffer
    val bottomBufferHeight = screenHeight * 0.075f

    BottomSheet(
        state = sheetState,
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surface)
    ) {
        Box(modifier = Modifier.fillMaxSize()) {

            if (contentHeight > 0.dp) {
                Column(modifier = Modifier.height(contentHeight)) {

                    // 1. Log Stream (Fills remaining space)
                    LiveOutputBottomCard(
                        logStream = viewModel.filteredLog,
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f)
                    )

                    // 2. Input (Fixed height)
                    ContextlessChatInput(
                        modifier = Modifier.fillMaxWidth(),
                        onSend = onSendPrompt
                    )

                    // 3. The 10% Buffer (Fixed height)
                    // This pushes the input up so its bottom edge is at 10% screen height.
                    Spacer(modifier = Modifier.height(bottomBufferHeight))
                }
            }

            // Copy and Clear buttons (Top Right) - Visible only when expanded
            if (isHalfwayExpanded) {
                Row(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(top = 16.dp, end = 16.dp)
                ) {
                    IconButton(onClick = {
                        coroutineScope.launch {
                            clipboardManager.setText(AnnotatedString(logMessages))
                        }
                    }) {
                        Icon(
                            imageVector = Icons.Default.ContentCopy,
                            contentDescription = "Copy Log",
                            tint = MaterialTheme.colorScheme.onSurface
                        )
                    }
                    IconButton(onClick = { viewModel.clearLog() }) {
                        Icon(
                            imageVector = Icons.Default.Clear,
                            contentDescription = "Clear Log",
                            tint = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            }
        }
    }
}