package com.hereliesaz.ideaz.ui

import androidx.compose.ui.unit.dp
import com.composables.core.SheetDetent

val AlmostHidden = SheetDetent("almost_hidden") { containerHeight, _ -> containerHeight * 0.01f }
val Peek = SheetDetent("peek") { containerHeight, _ -> containerHeight * 0.2f }
val Halfway = SheetDetent("halfway") { containerHeight, _ -> containerHeight * 0.5f }
val FullyExpanded = SheetDetent("fully_expanded") { containerHeight, _ -> containerHeight * 0.95f }
