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

@Composable
fun IdeBottomSheet(
    sheetState: BottomSheetState,
    viewModel: MainViewModel,
    peekDetent: SheetDetent,
    halfwayDetent: SheetDetent,
    chatHeight: Dp
) {
    val isChatVisible = sheetState.currentDetent == peekDetent || sheetState.currentDetent == halfwayDetent

    BottomSheet(
        state = sheetState,
        modifier = Modifier.fillMaxSize()
    ) {
        // The sheet content is JUST the log card
        LiveOutputBottomCard(
            logStream = viewModel.buildLog,
            modifier = Modifier.fillMaxSize(),
            // Add padding to the bottom so logs can scroll above the chat input
            bottomPadding = if (isChatVisible) chatHeight else 0.dp
        )
    }
}