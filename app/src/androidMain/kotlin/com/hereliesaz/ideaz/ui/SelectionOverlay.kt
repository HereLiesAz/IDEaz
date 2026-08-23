package com.hereliesaz.ideaz.ui

import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput

/**
 * A transparent tap-catcher over the preview while select mode is on.
 *
 * Tap is the entire gesture. The previous version also supported drag-to-select,
 * but the rect it produced was collapsed to its centre point by the very next
 * call anyway, and its threshold required more than 10px of movement on *both*
 * axes — so a horizontal drag was silently thrown away and the user got nothing.
 */
@Composable
fun SelectionOverlay(
    modifier: Modifier = Modifier,
    onTap: (Float, Float) -> Unit,
) {
    androidx.compose.foundation.layout.Box(
        modifier = modifier
            .fillMaxSize()
            .pointerInput(Unit) {
                detectTapGestures(onTap = { offset -> onTap(offset.x, offset.y) })
            }
    )
}
