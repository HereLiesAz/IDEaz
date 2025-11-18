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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.flow.Flow

@Composable
fun LiveOutputBottomCard(
    logStream: Flow<String>,
    modifier: Modifier = Modifier,
    bottomPadding: Dp = 0.dp // New parameter for padding
) {
    val logMessages by logStream.collectAsState(initial = "")

    // Reversing the list so the newest line is at the top (index 0)
    val logLines = logMessages.lines().reversed()
    val listState = rememberLazyListState()

    // Autoscroll to the top (index 0) when logLines size changes
    LaunchedEffect(logLines.size) {
        if (logLines.isNotEmpty()) {
            listState.animateScrollToItem(0)
        }
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(Color.Transparent)
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
            // Apply padding to the content, not the column
            contentPadding = PaddingValues(top = 8.dp, bottom = bottomPadding, start = 16.dp, end = 16.dp)
        ) {

            items(logLines) { line ->
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