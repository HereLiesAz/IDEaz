package com.hereliesaz.ideaz.ui

import android.graphics.Rect
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

@Composable
fun SelectionOverlay(
    modifier: Modifier = Modifier,
    onTap: (Float, Float) -> Unit,
    onDragEnd: (Rect) -> Unit
) {
    var startOffset by remember { mutableStateOf<Offset?>(null) }
    var endOffset by remember { mutableStateOf<Offset?>(null) }
    var isDragging by remember { mutableStateOf(false) }

    Box(
        modifier = modifier
            .fillMaxSize()
            .pointerInput(Unit) {
                detectTapGestures(
                    onTap = { offset ->
                        onTap(offset.x, offset.y)
                    }
                )
            }
            .pointerInput(Unit) {
                detectDragGestures(
                    onDragStart = { offset ->
                        startOffset = offset
                        endOffset = offset
                        isDragging = true
                    },
                    onDrag = { change, dragAmount ->
                        change.consume()
                        endOffset = endOffset?.plus(dragAmount)
                    },
                    onDragEnd = {
                        isDragging = false
                        val start = startOffset
                        val end = endOffset
                        if (start != null && end != null) {
                            val left = min(start.x, end.x).toInt()
                            val top = min(start.y, end.y).toInt()
                            val right = max(start.x, end.x).toInt()
                            val bottom = max(start.y, end.y).toInt()

                            if (abs(right - left) > 10 && abs(bottom - top) > 10) {
                                onDragEnd(Rect(left, top, right, bottom))
                            }
                        }
                        startOffset = null
                        endOffset = null
                    },
                    onDragCancel = {
                        isDragging = false
                        startOffset = null
                        endOffset = null
                    }
                )
            }
    ) {
        if (isDragging && startOffset != null && endOffset != null) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val start = startOffset!!
                val end = endOffset!!

                val topLeft = Offset(min(start.x, end.x), min(start.y, end.y))
                val size = androidx.compose.ui.geometry.Size(
                    abs(start.x - end.x),
                    abs(start.y - end.y)
                )

                drawRect(
                    color = Color.Green.copy(alpha = 0.3f),
                    topLeft = topLeft,
                    size = size,
                    style = Fill
                )
                drawRect(
                    color = Color.Green,
                    topLeft = topLeft,
                    size = size,
                    style = Stroke(width = 6f)
                )
            }
        }
    }
}
