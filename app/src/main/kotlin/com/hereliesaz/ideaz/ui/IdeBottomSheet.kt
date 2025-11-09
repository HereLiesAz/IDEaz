package com.hereliesaz.ideaz.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import com.hereliesaz.aznavrail.AzButton
import com.hereliesaz.aznavrail.model.AzButtonShape
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
import com.hereliesaz.ideaz.api.Activity
import com.hereliesaz.ideaz.api.Session
import kotlinx.coroutines.launch

@Composable
fun IdeBottomSheet(
    sheetState: BottomSheetState,
    viewModel: MainViewModel,
    peekDetent: SheetDetent,
    halfwayDetent: SheetDetent,
    chatHeight: Dp,
    buildStatus: String,
    aiStatus: String,
    sessions: List<Session>,
    activities: List<Activity>
) {
    val isChatVisible = sheetState.currentDetent == peekDetent || sheetState.currentDetent == halfwayDetent
    val isHalfwayExpanded = sheetState.currentDetent == halfwayDetent
    val logMessages by viewModel.combinedLog.collectAsState(initial = "")
    val clipboardManager = LocalClipboardManager.current
    val coroutineScope = rememberCoroutineScope()

    BottomSheet(
        state = sheetState,
        modifier = Modifier.fillMaxSize()
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            LiveOutputBottomCard(
                logStream = viewModel.combinedLog,
                modifier = Modifier.fillMaxSize(),
                bottomPadding = if (isChatVisible) chatHeight else 0.dp
            )

            if (isHalfwayExpanded) {
                Row(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(16.dp)
                ) {
                    AzButton(
                        onClick = { coroutineScope.launch { clipboardManager.setText(AnnotatedString(logMessages)) } },
                        text = "Copy",
                        shape = AzButtonShape.RECTANGLE
                    )
                    AzButton(
                        onClick = { viewModel.clearLog() },
                        text = "Clear",
                        shape = AzButtonShape.RECTANGLE
                    )
                }
            }
        }
    }
}