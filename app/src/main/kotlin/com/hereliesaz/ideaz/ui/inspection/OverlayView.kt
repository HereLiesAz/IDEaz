package com.hereliesaz.ideaz.ui.inspection

import android.content.Context
import android.content.Intent
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.view.MotionEvent
import android.view.View
import androidx.core.graphics.withTranslation
import kotlin.math.abs

/**
 * A custom view added to the WindowManager by the AccessibilityService.
 * It is responsible for:
 * 1. Being transparent mostly.
 * 2. Drawing a bounding box when instructed.
 * 3. Detecting taps and drags when in "Select Mode".
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

    private val selectionPaint = Paint().apply {
        color = Color.CYAN
        style = Paint.Style.STROKE
        strokeWidth = 5f
    }

    private var highlightRect: Rect? = null
    private var isSelectionMode = false

    // Drag selection state
    private var startX = 0f
    private var startY = 0f
    private var currentX = 0f
    private var currentY = 0f
    private var isDragging = false

    // Update Splash State
    private var isUpdateSplashVisible = false
    private val loc = IntArray(2)
    private val textPaint = Paint().apply {
        color = Color.WHITE
        textSize = 60f
        textAlign = Paint.Align.CENTER
        setShadowLayer(10f, 0f, 0f, Color.BLACK)
    }
    private val bgPaint = Paint().apply {
        color = Color.argb(200, 0, 0, 0)
        style = Paint.Style.FILL
    }

    fun showUpdateSplash() {
        isUpdateSplashVisible = true
        invalidate()
        // Auto-hide after 3 seconds
        postDelayed({
            isUpdateSplashVisible = false
            invalidate()
        }, 3000)
    }

    fun setSelectionMode(enabled: Boolean) {
        isSelectionMode = enabled
        isDragging = false
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

        if (isUpdateSplashVisible) {
            canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), bgPaint)
            canvas.drawText("Updating, gimme a sec...", width / 2f, height / 2f, textPaint)
        }

        // Visual cue that we are in "Select Mode" (dim the region slightly)
        if (isSelectionMode) {
            canvas.drawColor(Color.argb(30, 0, 0, 0))
        }

        // highlightRect and the drag points are in screen coordinates (rawX/rawY).
        // When the overlay window is constrained to the target app's bounds the
        // view's origin is offset from the screen origin, so translate by the
        // window's on-screen location before drawing them. (Full-screen → (0,0).)
        getLocationOnScreen(loc)
        canvas.withTranslation(-loc[0].toFloat(), -loc[1].toFloat()) {
            highlightRect?.let {
                drawRect(it, fillPaint)
                drawRect(it, paint)
            }

            if (isDragging) {
                val left = kotlin.math.min(startX, currentX)
                val top = kotlin.math.min(startY, currentY)
                val right = kotlin.math.max(startX, currentX)
                val bottom = kotlin.math.max(startY, currentY)
                drawRect(left, top, right, bottom, selectionPaint)
            }
        }
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        // If not in selection mode, WindowManager flags should prevent this call,
        // but we add a check for safety.
        if (!isSelectionMode) return false

        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                startX = event.rawX
                startY = event.rawY
                currentX = startX
                currentY = startY
                isDragging = true
                invalidate()
                return true
            }
            MotionEvent.ACTION_MOVE -> {
                currentX = event.rawX
                currentY = event.rawY
                invalidate()
                return true
            }
            MotionEvent.ACTION_UP -> {
                isDragging = false
                invalidate()

                val dx = abs(currentX - startX)
                val dy = abs(currentY - startY)
                // Use a small slop threshold to distinguish a tap from a drag.
                // ViewConfiguration.getScaledTouchSlop() is the canonical value;
                // 24dp at default density ~= 48px, which is fine for a touch
                // overlay where pixel precision isn't expected.
                val touchSlopPx = android.view.ViewConfiguration.get(context).scaledTouchSlop
                val isTap = dx < touchSlopPx && dy < touchSlopPx

                if (isTap) {
                    // Tap: defer to the AccessibilityService to walk the view
                    // hierarchy and find the smallest a11y node containing
                    // (x, y). It will broadcast PROMPT_SUBMITTED_NODE with
                    // BOUNDS + RESOURCE_ID once it's done.
                    val intent = Intent("com.hereliesaz.ideaz.INTERNAL_TAP_DETECTED").apply {
                        putExtra("X", startX.toInt())
                        putExtra("Y", startY.toInt())
                        setPackage(context.packageName)
                    }
                    context.sendBroadcast(intent)
                } else {
                    // Drag: emit the rectangle directly. No node lookup; the
                    // bounds *are* the user's intent.
                    val left = kotlin.math.min(startX, currentX).toInt()
                    val top = kotlin.math.min(startY, currentY).toInt()
                    val right = kotlin.math.max(startX, currentX).toInt()
                    val bottom = kotlin.math.max(startY, currentY).toInt()
                    val rect = Rect(left, top, right, bottom)

                    val intent = Intent("com.hereliesaz.ideaz.SELECTION_MADE").apply {
                        putExtra("RECT", rect)
                        setPackage(context.packageName)
                    }
                    context.sendBroadcast(intent)
                }
                return true
            }
        }
        return super.onTouchEvent(event)
    }
}
