package com.hereliesaz.ideaz.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.composables.core.BottomSheet
import com.composables.core.BottomSheetState
import com.composables.core.SheetDetent

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
    val logMessages by viewModel.filteredLog.collectAsState(initial = emptyList())
    val clipboardManager = LocalClipboardManager.current

    val contentHeight = when (sheetState.currentDetent) {
        halfwayDetent -> screenHeight * 0.5f
        peekDetent -> screenHeight * 0.25f
        else -> 0.dp
    }

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

                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f)
                            .padding(horizontal = 16.dp)
                    ) {
                        items(logMessages) { message ->
                            Text(text = message, style = MaterialTheme.typography.bodySmall)
                        }
                    }

                    ContextlessChatInput(
                        modifier = Modifier.fillMaxWidth(),
                        onSend = onSendPrompt
                    )

                    Spacer(modifier = Modifier.height(bottomBufferHeight))
                }
            }

            if (isHalfwayExpanded) {
                Row(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(top = 16.dp, end = 16.dp)
                ) {
                    IconButton(onClick = {
                        clipboardManager.setText(AnnotatedString(logMessages.joinToString("\n")))
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
