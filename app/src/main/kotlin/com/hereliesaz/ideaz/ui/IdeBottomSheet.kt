package com.hereliesaz.ideaz.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import com.hereliesaz.aznavrail.AzButton
import com.hereliesaz.aznavrail.model.AzButtonShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
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
    halfwayDetent: SheetDetent
) {
    val isHalfwayExpanded = sheetState.currentDetent == halfwayDetent
    val logMessages by viewModel.filteredLog.collectAsState(initial = "")
    val clipboardManager = LocalClipboardManager.current
    val coroutineScope = rememberCoroutineScope()

    BottomSheet(
        state = sheetState,
        modifier = Modifier.fillMaxSize()
    ) {
        // BottomSheet content is now empty or minimal as requested components are layered externally.
        // If the drag handle is part of the BottomSheet implementation (library specific), it remains here.
        // Otherwise, this block defines the "sheet" surface.

        // Keeping the Box to provide a surface for the sheet, but empty for now as content is lifted out.
        Box(modifier = Modifier.fillMaxSize())
    }
}