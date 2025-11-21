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
    modifier: Modifier = Modifier
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

    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surface)
    ) {
        // Add the drag handle
        DragIndication(
            modifier = Modifier
                .align(Alignment.CenterHorizontally)
                .padding(top = 8.dp, bottom = 8.dp)
        )

        LazyColumn(
            state = listState, // Pass the state to the LazyColumn
            modifier = Modifier.weight(1f),
            reverseLayout = true, // Anchor content to the bottom
            // Apply padding to the content, not the column
            contentPadding = PaddingValues(top = 8.dp, bottom = 8.dp, start = 16.dp, end = 16.dp)
        ) {

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
        }
    }
}