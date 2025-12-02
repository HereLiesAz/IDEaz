package com.hereliesaz.ideaz.ui.inspection

import android.graphics.Rect
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import kotlin.math.abs
import kotlin.math.min

@Composable
fun OverlayCanvas(
    isSelectMode: Boolean,
    selectionRect: Rect?,
    onTap: (Float, Float) -> Unit,
    onDragSelection: (Rect) -> Unit
) {
    var dragStart by remember { mutableStateOf<Offset?>(null) }
    var dragCurrent by remember { mutableStateOf<Offset?>(null) }

    Canvas(
        modifier = Modifier
            .fillMaxSize()
            .then(
                if (isSelectMode) {
                    Modifier
                        .background(Color.Black.copy(alpha = 0.3f)) // Dim screen in select mode
                        .pointerInput(Unit) {
                            detectTapGestures { offset ->
                                onTap(offset.x, offset.y)
                            }
                        }
                        .pointerInput(Unit) {
                            detectDragGestures(
                                onDragStart = { dragStart = it; dragCurrent = it },
                                onDrag = { change, dragAmount ->
                                    change.consume()
                                    dragCurrent = dragCurrent?.plus(dragAmount)
                                },
                                onDragEnd = {
                                    val start = dragStart
                                    val end = dragCurrent
                                    if (start != null && end != null) {
                                        val left = min(start.x, end.x).toInt()
                                        val top = min(start.y, end.y).toInt()
                                        val right = (left + abs(start.x - end.x)).toInt()
                                        val bottom = (top + abs(start.y - end.y)).toInt()
                                        if (abs(right - left) > 20 && abs(bottom - top) > 20) {
                                            onDragSelection(Rect(left, top, right, bottom))
                                        }
                                    }
                                    dragStart = null; dragCurrent = null
                                }
                            )
                        }
                } else Modifier
            )
    ) {
        // Draw confirmed selection
        selectionRect?.let { rect ->
            drawRect(
                color = Color.Green,
                topLeft = Offset(rect.left.toFloat(), rect.top.toFloat()),
                size = Size(rect.width().toFloat(), rect.height().toFloat()),
                style = Stroke(width = 8f)
            )
        }

        // Draw active drag
        if (isSelectMode && dragStart != null && dragCurrent != null) {
            val start = dragStart!!
            val end = dragCurrent!!
            val topLeft = Offset(min(start.x, end.x), min(start.y, end.y))
            val size = Size(abs(start.x - end.x), abs(start.y - end.y))

            drawRect(color = Color.Green.copy(alpha = 0.3f), topLeft = topLeft, size = size, style = Fill)
            drawRect(color = Color.Green, topLeft = topLeft, size = size, style = Stroke(width = 5f))
        }
    }
}