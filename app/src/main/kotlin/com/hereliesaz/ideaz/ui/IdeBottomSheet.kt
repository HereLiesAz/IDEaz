package com.hereliesaz.ideaz.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.composables.core.BottomSheet
import com.composables.core.BottomSheetState
import com.composables.core.SheetDetent
import com.hereliesaz.ideaz.api.Activity
import com.hereliesaz.ideaz.api.Session

@Composable
fun IdeBottomSheet(
    sheetState: BottomSheetState,
    viewModel: MainViewModel,
    peekDetent: SheetDetent,
    halfwayDetent: SheetDetent,
    chatHeight: Dp,
    // MODIFIED: Removed all status variables
    buildStatus: String,
    aiStatus: String,
    sessions: List<Session>,
    activities: List<Activity>
) {
    val isChatVisible = sheetState.currentDetent == peekDetent || sheetState.currentDetent == halfwayDetent

    BottomSheet(
        state = sheetState,
        modifier = Modifier.fillMaxSize()
    ) {
        // MODIFIED: The sheet content is JUST the log card
        // All status text is now part of the logStream in the ViewModel
        LiveOutputBottomCard(
            logStream = viewModel.combinedLog,
            modifier = Modifier.fillMaxSize(),
            // Add padding to the bottom so logs can scroll above the chat input
            bottomPadding = if (isChatVisible) chatHeight else 0.dp
        )
    }
}