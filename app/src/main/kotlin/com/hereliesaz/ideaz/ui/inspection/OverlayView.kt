package com.hereliesaz.ideaz.ui.inspection

import android.content.Context
import android.content.Intent
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.view.MotionEvent
import android.view.View

/**
 * A custom view added to the WindowManager by the AccessibilityService.
 * It is responsible for:
 * 1. Being transparent mostly.
 * 2. Drawing a bounding box when instructed.
 * 3. Detecting taps when in "Select Mode".
 */
class OverlayView(context: Context) : View(context) {

    private val paint = Paint().apply {
        color = Color.GREEN
        style = Paint.Style.STROKE
        strokeWidth = 8f
    }

    private val fillPaint = Paint().apply {
        color = Color.argb(50, 0, 255, 0) // Semi-transparent green
        style = Paint.Style.FILL
    }

    private var highlightRect: Rect? = null
    private var isSelectionMode = false

    fun setSelectionMode(enabled: Boolean) {
        isSelectionMode = enabled
        // Force redraw to show/hide selection cues if needed
        invalidate()
    }

    fun updateHighlight(rect: Rect?) {
        highlightRect = rect
        invalidate()
    }

    fun clearHighlight() {
        highlightRect = null
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        // Visual cue that we are in "Select Mode" (dim the screen slightly)
        if (isSelectionMode) {
            canvas.drawColor(Color.argb(30, 0, 0, 0))
        }

        highlightRect?.let {
            canvas.drawRect(it, fillPaint)
            canvas.drawRect(it, paint)
        }
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        // If not in selection mode, WindowManager flags should prevent this call,
        // but we add a check for safety.
        if (!isSelectionMode) return false

        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                // Send tap coordinates back to service
                val intent = Intent("com.hereliesaz.ideaz.INTERNAL_TAP_DETECTED").apply {
                    putExtra("X", event.rawX.toInt())
                    putExtra("Y", event.rawY.toInt())
                    setPackage(context.packageName)
                }
                context.sendBroadcast(intent)
                return true
            }
        }
        return super.onTouchEvent(event)
    }
}