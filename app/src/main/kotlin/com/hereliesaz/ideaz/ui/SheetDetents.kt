package com.hereliesaz.ideaz.ui

import androidx.compose.ui.unit.dp
import com.composables.core.SheetDetent

internal val AlmostHidden by lazy { SheetDetent("almost_hidden") { _, _ -> 6.dp } }
internal val Peek = SheetDetent("peek") { containerHeight, _ -> containerHeight * 0.2f }
internal val Halfway = SheetDetent("halfway") { containerHeight, _ -> containerHeight * 0.5f }
