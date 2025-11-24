package com.hereliesaz.ideaz.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.flow.Flow

@Composable
fun LiveOutputBottomCard(
    logStream: Flow<String>,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(top = 8.dp, bottom = 8.dp, start = 16.dp, end = 16.dp)
) {
    val logMessages by logStream.collectAsState(initial = "")

    // Reverse lines for reverseLayout
    val logLines = logMessages.trim().lines().reversed()
    val listState = rememberLazyListState()

    // Autoscroll to the bottom (index 0) when logLines size changes
    LaunchedEffect(logLines.size) {
        if (logLines.isNotEmpty()) {
            listState.animateScrollToItem(0)
        }
    }

    LazyColumn(
        state = listState, // Pass the state to the LazyColumn
        modifier = modifier.background(MaterialTheme.colorScheme.surface),
        reverseLayout = true, // Anchor content to the bottom
        contentPadding = contentPadding
    ) {
        // Add items in reverse order because of reverseLayout
        items(logLines) { line ->
            if (line.isNotBlank()) {
                Text(
                    text = line,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }

        item {
            DragIndication(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp, bottom = 8.dp)
            )
        }
    }
}