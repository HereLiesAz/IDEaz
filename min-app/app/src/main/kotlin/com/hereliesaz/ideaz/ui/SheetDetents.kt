package com.hereliesaz.ideaz.ui

import androidx.compose.ui.unit.dp
import com.composables.core.SheetDetent

internal val Peek = SheetDetent("peek") { containerHeight, _ -> containerHeight * 0.25f }
internal val Halfway = SheetDetent("halfway") { containerHeight, _ -> containerHeight * 0.5f }
internal val FullyExpanded = SheetDetent("expanded") { containerHeight, _ -> containerHeight }
internal val AlmostHidden = SheetDetent("almost_hidden") { _, _ -> 0.dp }
