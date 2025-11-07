package com.hereliesaz.ideaz.services

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.view.accessibility.AccessibilityEvent
import android.graphics.PixelFormat
import android.graphics.Paint
import android.view.Gravity
import android.view.WindowManager
import android.widget.FrameLayout
import android.content.BroadcastReceiver
import android.content.Context
import android.content.IntentFilter
import android.view.accessibility.AccessibilityNodeInfo
import android.graphics.Rect
import android.os.IBinder
import android.util.Log
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.widget.Button
import android.widget.EditText
import android.widget.ScrollView
import android.widget.TextView
import com.hereliesaz.ideaz.R
import android.graphics.Canvas
import android.graphics.Color
import kotlin.math.abs
import kotlin.math.min // <-- FIX 1: Import min
import kotlin.math.max // <-- FIX 2: Import max

class UIInspectionService : AccessibilityService() {

    private var touchInterceptor: FrameLayout? = null
    private var logView: View? = null // The log box (sized to the rect)
    private var promptView: View? = null // The prompt input (below the rect)

    private var windowManager: WindowManager? = null

    // --- New variables for drag-selection ---
    private val selectionPaint = Paint().apply {
        color = Color.argb(100, 255, 0, 0) // Semi-transparent red
        style = Paint.Style.FILL
    }
    private var startX = 0f
    private var startY = 0f
    private var endX = 0f
    private var endY = 0f
    private var isDragging = false
    private var currentSelectionRect = Rect()
    // --- End new variables ---

    private var touchSlop = 0

    // --- Broadcast Receiver for commands from ViewModel ---
    private val commandReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            when (intent?.action) {
                // This is no longer used, the service shows its own UI
                // "com.hereliesaz.ideaz.SHOW_PROMPT" -> { ... }

                "com.hereliesaz.ideaz.AI_LOG" -> {
                    val message = intent.getStringExtra("MESSAGE") ?: "..."
                    // Must run UI on main thread
                    touchInterceptor?.post { updateLogUI(message) }
                }
                "com.hereliesaz.ideaz.TASK_FINISHED" -> {
                    // Must run UI on main thread
                    touchInterceptor?.post { hideOverlayUI() }
                }
            }
        }
    }
    // --- End Broadcast Receiver ---


    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
    }

    override fun onInterrupt() {
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        touchSlop = ViewConfiguration.get(this).scaledTouchSlop
        showTouchInterceptor()

        // Register the command receiver
        val filter = IntentFilter().apply {
            // addAction("com.hereliesaz.ideaz.SHOW_PROMPT") // No longer needed
            addAction("com.hereliesaz.ideaz.AI_LOG")
            addAction("com.hereliesaz.ideaz.TASK_FINISHED")
        }
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(commandReceiver, filter, Context.RECEIVER_EXPORTED)
        } else {
            registerReceiver(commandReceiver, filter)
        }
    }

    override fun onUnbind(intent: Intent?): Boolean {
        hideTouchInterceptor()
        hideOverlayUI()
        unregisterReceiver(commandReceiver) // Unregister
        return super.onUnbind(intent)
    }

    private fun showTouchInterceptor() {
        // MODIFIED: This FrameLayout now handles drawing the selection
        touchInterceptor = object : FrameLayout(this) {

            // This is crucial to allow onDraw to be called
            init { setWillNotDraw(false) }

            override fun onDraw(canvas: Canvas) {
                super.onDraw(canvas)
                if (isDragging) {
                    // Draw the real-time selection rectangle
                    currentSelectionRect.set(
                        min(startX, endX).toInt(), // <-- FIX 3: Use min()
                        min(startY, endY).toInt(), // <-- FIX 4: Use min()
                        max(startX, endX).toInt(), // <-- FIX 5: Use max()
                        max(startY, endY).toInt()  // <-- FIX 6: Use max()
                    )
                    canvas.drawRect(currentSelectionRect, selectionPaint)
                }
            }

            override fun onTouchEvent(event: MotionEvent): Boolean {
                // Only process drag if no UI is active
                if (logView != null || promptView != null) {
                    return super.onTouchEvent(event)
                }

                when (event.action) {
                    MotionEvent.ACTION_DOWN -> {
                        startX = event.rawX
                        startY = event.rawY
                        endX = startX
                        endY = startY
                        isDragging = false // Assume it's a tap until proven otherwise
                        invalidate()
                        return true
                    }
                    MotionEvent.ACTION_MOVE -> {
                        if (isDragging ||
                            abs(event.rawX - startX) > touchSlop ||
                            abs(event.rawY - startY) > touchSlop) {

                            isDragging = true
                            endX = event.rawX
                            endY = event.rawY
                            invalidate() // Request a redraw
                        }
                        return true
                    }
                    MotionEvent.ACTION_UP -> {
                        invalidate() // Clear the rect

                        if (isDragging) {
                            // --- This was a DRAG ---
                            val rect = Rect(
                                min(startX, endX).toInt(), // <-- FIX 7: Use min()
                                min(startY, endY).toInt(), // <-- FIX 8: Use min()
                                max(startX, endX).toInt(), // <-- FIX 9: Use max()
                                max(startY, endY).toInt()  // <-- FIX 10: Use max()
                            )

                            // Don't trigger if it's too small
                            if (rect.width() > 10 && rect.height() > 10) {
                                showLogUI(rect)
                                showPromptUI(rect, null) // Pass null for resourceId
                            }
                        } else {
                            // --- This was a TAP ---
                            val node = findNodeAt(rootInActiveWindow, startX.toInt(), startY.toInt())
                            if (node != null) {
                                val resourceId = node.viewIdResourceName
                                if (resourceId != null) {
                                    val bounds = Rect()
                                    node.getBoundsInScreen(bounds)
                                    showLogUI(bounds)
                                    showPromptUI(bounds, resourceId) // Pass the resourceId
                                } else {
                                    // Fallback for nodes without ID, treat as drag
                                    val bounds = Rect()
                                    node.getBoundsInScreen(bounds)
                                    showLogUI(bounds)
                                    showPromptUI(bounds, null)
                                }
                            }
                        }
                        isDragging = false
                        return true
                    }
                }
                return super.onTouchEvent(event)
            }
        }
        val lp = WindowManager.LayoutParams().apply {
            type = WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY
            format = PixelFormat.TRANSLUCENT
            width = WindowManager.LayoutParams.MATCH_PARENT
            height = WindowManager.LayoutParams.MATCH_PARENT
        }
        windowManager?.addView(touchInterceptor, lp)
    }

    private fun hideTouchInterceptor() {
        touchInterceptor?.let {
            windowManager?.removeView(it)
        }
        touchInterceptor = null
    }

    // New function to show the log box sized to the Rect
    private fun showLogUI(rect: Rect) {
        logView = LayoutInflater.from(this).inflate(R.layout.log_overlay, null)

        val lp = WindowManager.LayoutParams().apply {
            type = WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY
            format = PixelFormat.TRANSLUCENT
            flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE // Log box doesn't take touch

            // Set position and size from the drag Rect
            gravity = Gravity.TOP or Gravity.LEFT
            x = rect.left
            y = rect.top
            width = rect.width()
            height = rect.height()
        }

        windowManager?.addView(logView, lp)
    }

    // Modified function to show the prompt *under* the Rect
    private fun showPromptUI(rect: Rect, resourceId: String?) {
        promptView = LayoutInflater.from(this).inflate(R.layout.inspection_overlay, null)

        val lp = WindowManager.LayoutParams().apply {
            type = WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY
            format = PixelFormat.TRANSLUCENT
            // This UI *needs* focus to type
            flags = WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
            width = WindowManager.LayoutParams.WRAP_CONTENT
            height = WindowManager.LayoutParams.WRAP_CONTENT

            // Position it below the log box
            gravity = Gravity.TOP or Gravity.LEFT
            x = rect.left // Align left edge
            y = rect.bottom + 8 // Position 8dp below the log box
        }

        val promptInput = promptView?.findViewById<EditText>(R.id.prompt_input)
        val submitButton = promptView?.findViewById<Button>(R.id.submit_button)

        promptInput?.hint = if (resourceId != null) "Prompt for $resourceId..." else "Prompt for selected area..."

        submitButton?.setOnClickListener {
            val prompt = promptInput?.text.toString()
            if (prompt.isNotBlank()) {
                // Send prompt back to ViewModel via broadcast
                // We send a *different* action based on tap or drag
                if (resourceId != null) {
                    val promptIntent = Intent("com.hereliesaz.ideaz.PROMPT_SUBMITTED_NODE").apply {
                        putExtra("RESOURCE_ID", resourceId)
                        putExtra("PROMPT", prompt)
                    }
                    sendBroadcast(promptIntent)
                } else {
                    val promptIntent = Intent("com.hereliesaz.ideaz.PROMPT_SUBMITTED_RECT").apply {
                        putExtra("RECT", rect)
                        putExtra("PROMPT", prompt)
                    }
                    sendBroadcast(promptIntent)
                }

                // Hide the prompt UI, but leave the log UI
                hidePromptUI()
            }
        }

        windowManager?.addView(promptView, lp)
    }

    private fun updateLogUI(message: String) {
        logView?.let {
            val logText = it.findViewById<TextView>(R.id.log_text)
            if (logText.text.contains("Initializing")) {
                logText.text = "> $message" // Replace placeholder
            } else {
                logText.append("\n> $message")
            }

            // Auto-scroll the log
            val scrollView = it.findViewById<ScrollView>(R.id.log_scroll_view)
            scrollView?.post { scrollView.fullScroll(View.FOCUS_DOWN) }
        }
    }

    private fun hidePromptUI() {
        promptView?.let {
            windowManager?.removeView(it)
        }
        promptView = null
    }

    private fun hideOverlayUI() {
        hidePromptUI()
        logView?.let {
            windowManager?.removeView(it)
        }
        logView = null
    }

    // This function is no longer dead.
    private fun findNodeAt(root: AccessibilityNodeInfo?, x: Int, y: Int): AccessibilityNodeInfo? {
        if (root == null) return null

        val rect = Rect()
        root.getBoundsInScreen(rect)
        if (!rect.contains(x, y)) {
            return null
        }

        // Traverse children first (depth-first)
        for (i in 0 until root.childCount) {
            val child = root.getChild(i)
            val result = findNodeAt(child, x, y)
            if (result != null) {
                return result // Return the deepest child that matches
            }
        }

        // If no child matches, this node (the parent) is the match
        return root
    }
}