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
import android.os.Build
import android.os.IBinder
import android.util.Log
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.widget.Button
import android.widget.EditText
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import com.hereliesaz.ideaz.R
import android.graphics.Canvas
import android.graphics.Color
import com.hereliesaz.ideaz.ui.SettingsViewModel
import kotlin.math.abs
import kotlin.math.min
import kotlin.math.max

class UIInspectionService : AccessibilityService() {

    private var touchInterceptor: FrameLayout? = null
    // MODIFIED: We only have one overlay view now
    private var overlayView: View? = null
    private var logContainer: View? = null
    private var promptContainer: View? = null

    private var windowManager: WindowManager? = null

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

    private var touchSlop = 0

    private lateinit var settingsViewModel: SettingsViewModel
    private var targetPackageName: String? = null

    // --- Broadcast Receiver for commands from ViewModel ---
    private val commandReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            Log.d("UIInspectionService", "Received broadcast: ${intent?.action}")
            when (intent?.action) {

                "com.hereliesaz.ideaz.AI_LOG" -> {
                    val message = intent.getStringExtra("MESSAGE") ?: "..."
                    // Must run UI on main thread
                    touchInterceptor?.post { updateLogUI(message) }
                }
                "com.hereliesaz.ideaz.TASK_FINISHED" -> {
                    // Must run UI on main thread
                    touchInterceptor?.post { hideOverlayUI() }
                }
                // NEW: Command from VM to re-show the log box *after* screenshot
                "com.hereliesaz.ideaz.SHOW_LOG_UI" -> {
                    val rect = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        intent.getParcelableExtra("RECT", Rect::class.java)
                    } else {
                        @Suppress("DEPRECATION")
                        intent.getParcelableExtra("RECT")
                    }
                    rect?.let {
                        touchInterceptor?.post { showLogUI(it) }
                    }
                }
                SettingsViewModel.ACTION_TARGET_PACKAGE_CHANGED -> {
                    targetPackageName = intent.getStringExtra("PACKAGE_NAME")
                    // Re-evaluate overlay visibility based on new package name
                    val currentPackage = rootInActiveWindow?.packageName?.toString()
                    Log.d("UIInspectionService", "Target package changed to: $targetPackageName. Current app: $currentPackage")
                    touchInterceptor?.post {
                        if (currentPackage == targetPackageName) {
                            if (touchInterceptor == null) showTouchInterceptor()
                        } else {
                            if (touchInterceptor != null) hideTouchInterceptor()
                        }
                    }
                }
            }
        }
    }
    // --- End Broadcast Receiver ---


    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event?.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {
            val currentPackage = event.packageName?.toString()
            Log.d("UIInspectionService", "Window changed. App: $currentPackage, Target: $targetPackageName")
            if (currentPackage == targetPackageName) {
                // Use post to ensure this runs on the main thread
                // and to avoid issues with window state transitions
                touchInterceptor?.post {
                    if (touchInterceptor == null) {
                        Log.d("UIInspectionService", "Target app ($targetPackageName) is in foreground. Showing overlay.")
                        showTouchInterceptor()
                    }
                }
            } else {
                touchInterceptor?.post {
                    if (touchInterceptor != null) {
                        Log.d("UIInspectionService", "Target app ($targetPackageName) is not in foreground. Hiding overlay.")
                        hideTouchInterceptor()
                    }
                }
            }
        }
    }

    override fun onInterrupt() {
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        touchSlop = ViewConfiguration.get(this).scaledTouchSlop

        // Get the target package name on connect
        settingsViewModel = SettingsViewModel(application)
        targetPackageName = settingsViewModel.getTargetPackageName()
        Log.d("UIInspectionService", "Service connected. Target package: $targetPackageName")

        // Register the command receiver
        val filter = IntentFilter().apply {
            addAction("com.hereliesaz.ideaz.AI_LOG")
            addAction("com.hereliesaz.ideaz.TASK_FINISHED")
            addAction("com.hereliesaz.ideaz.SHOW_LOG_UI") // Add new action
            addAction(SettingsViewModel.ACTION_TARGET_PACKAGE_CHANGED)
        }
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(commandReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            registerReceiver(commandReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
        }
    }

    override fun onUnbind(intent: Intent?): Boolean {
        hideTouchInterceptor()
        hideOverlayUI()
        unregisterReceiver(commandReceiver) // Unregister
        return super.onUnbind(intent)
    }

    private fun showTouchInterceptor() {
        // Prevent showing if it somehow already exists
        if (touchInterceptor != null) {
            Log.w("UIInspectionService", "showTouchInterceptor called but interceptor already exists.")
            return
        }

        touchInterceptor = object : FrameLayout(this) {

            init { setWillNotDraw(false) }

            override fun onDraw(canvas: Canvas) {
                super.onDraw(canvas)
                if (isDragging) {
                    currentSelectionRect.set(
                        min(startX, endX).toInt(),
                        min(startY, endY).toInt(),
                        max(startX, endX).toInt(),
                        max(startY, endY).toInt()
                    )
                    canvas.drawRect(currentSelectionRect, selectionPaint)
                }
            }

            override fun onTouchEvent(event: MotionEvent): Boolean {
                // Only process drag if no UI is active
                if (overlayView != null) {
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
                                min(startX, endX).toInt(),
                                min(startY, endY).toInt(),
                                max(startX, endX).toInt(),
                                max(startY, endY).toInt()
                            )

                            // Don't trigger if it's too small
                            if (rect.width() > 10 && rect.height() > 10) {
                                showPromptUI(rect, null) // Pass null for resourceId
                            }
                        } else {
                            // --- This was a TAP ---
                            val node = findNodeAt(rootInActiveWindow, startX.toInt(), startY.toInt())
                            if (node != null) {
                                val resourceId = node.viewIdResourceName
                                val bounds = Rect()
                                node.getBoundsInScreen(bounds)

                                showPromptUI(bounds, resourceId) // Pass the resourceId (can be null)
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
            flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
            width = WindowManager.LayoutParams.MATCH_PARENT
            height = WindowManager.LayoutParams.MATCH_PARENT
        }
        windowManager?.addView(touchInterceptor, lp)
    }

    private fun hideTouchInterceptor() {
        // Prevent crashing if it's already hidden
        if (touchInterceptor == null) {
            Log.w("UIInspectionService", "hideTouchInterceptor called but interceptor was already null.")
            return
        }
        touchInterceptor?.let {
            windowManager?.removeView(it)
        }
        touchInterceptor = null
    }


    // NEW function to show *only* the log UI
    private fun showLogUI(rect: Rect) {
        Log.d("UIInspectionService", "showLogUI called")
        // If overlayView already exists (which it should), just update it
        if (overlayView == null) {
            overlayView = LayoutInflater.from(this).inflate(R.layout.inspection_overlay, null)
        }

        val lp = WindowManager.LayoutParams().apply {
            type = WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY
            format = PixelFormat.TRANSLUCENT
            // Log box is not focusable or touchable (except cancel)
            flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE

            // Set position and size from the drag Rect
            gravity = Gravity.TOP or Gravity.LEFT
            x = rect.left
            y = rect.top
            width = rect.width()
            height = rect.height()
        }

        logContainer = overlayView?.findViewById(R.id.log_container)
        promptContainer = overlayView?.findViewById(R.id.prompt_container)
        val logText = overlayView?.findViewById<TextView>(R.id.log_text)
        val cancelButton = overlayView?.findViewById<ImageButton>(R.id.cancel_button)

        logText?.text = "Initializing..."
        logContainer?.visibility = View.VISIBLE
        promptContainer?.visibility = View.GONE

        cancelButton?.setOnClickListener {
            val cancelIntent = Intent("com.hereliesaz.ideaz.CANCEL_TASK_REQUESTED").apply {
                setPackage(packageName)
            }
            sendBroadcast(cancelIntent)
        }

        try {
            if (overlayView?.windowToken == null) {
                windowManager?.addView(overlayView, lp)
            } else {
                windowManager?.updateViewLayout(overlayView, lp)
            }
        } catch (e: Exception) {
            Log.e("UIInspectionService", "Error showing log UI", e)
        }
    }

    // MODIFIED: This function now shows the *single* overlay
    // and positions it based on the rect
    private fun showPromptUI(rect: Rect, resourceId: String?) {
        Log.d("UIInspectionService", "showPromptUI called")
        // Ensure any old UI is gone
        hideOverlayUI()

        overlayView = LayoutInflater.from(this).inflate(R.layout.inspection_overlay, null)

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
            y = rect.bottom + 8 // Position 8dp below the rect
        }

        // Get all the components from the single layout
        logContainer = overlayView?.findViewById(R.id.log_container)
        promptContainer = overlayView?.findViewById(R.id.prompt_container)
        val promptInput = overlayView?.findViewById<EditText>(R.id.prompt_input)
        val submitButton = overlayView?.findViewById<Button>(R.id.submit_button)
        val cancelButton = overlayView?.findViewById<ImageButton>(R.id.cancel_button)

        // Start with log hidden, prompt visible
        logContainer?.visibility = View.GONE
        promptContainer?.visibility = View.VISIBLE

        promptInput?.hint = if (resourceId != null) "Prompt for $resourceId..." else "Prompt for selected area..."

        submitButton?.setOnClickListener {
            val prompt = promptInput?.text.toString()
            if (prompt.isNotBlank()) {

                // --- MODIFIED SCREENSHOT FLOW ---
                // 1. Hide the prompt UI immediately
                hidePromptUI()
                // 2. Send the broadcast. VM will handle screenshot, THEN show log UI.
                // ---

                // Send prompt back to ViewModel via broadcast
                if (resourceId != null) {
                    val promptIntent = Intent("com.hereliesaz.ideaz.PROMPT_SUBMITTED_NODE").apply {
                        setPackage(packageName)
                        putExtra("RESOURCE_ID", resourceId)
                        putExtra("PROMPT", prompt)
                        putExtra("BOUNDS", rect)
                    }
                    sendBroadcast(promptIntent)
                } else {
                    val promptIntent = Intent("com.hereliesaz.ideaz.PROMPT_SUBMITTED_RECT").apply {
                        setPackage(packageName)
                        putExtra("RECT", rect)
                        putExtra("PROMPT", prompt)
                    }
                    sendBroadcast(promptIntent)
                }
            }
        }

        cancelButton?.setOnClickListener {
            val cancelIntent = Intent("com.hereliesaz.ideaz.CANCEL_TASK_REQUESTED").apply {
                setPackage(packageName)
            }
            sendBroadcast(cancelIntent)
        }

        windowManager?.addView(overlayView, lp)
    }

    private fun updateLogUI(message: String) {
        overlayView?.let {
            val logText = it.findViewById<TextView>(R.id.log_text)
            if (logText.text.contains("Initializing")) {
                logText.text = "> $message" // Replace placeholder
            } else {
                logText.append("\n> $message")
            }

            val scrollView = it.findViewById<ScrollView>(R.id.log_scroll_view)
            scrollView?.post { scrollView.fullScroll(View.FOCUS_DOWN) }
        }
    }

    // Hides *only* the prompt part of the overlay
    private fun hidePromptUI() {
        if (promptContainer?.visibility == View.VISIBLE) {
            promptContainer?.visibility = View.GONE
        }

        // If the main view is still just a prompt, hide the whole thing
        if (logContainer?.visibility == View.GONE) {
            hideOverlayUI()
        }
    }

    private fun hideOverlayUI() {
        overlayView?.let {
            try {
                windowManager?.removeView(it)
            } catch (e: Exception) {
                Log.w("UIInspectionService", "Error removing overlay view, it may have already been removed.", e)
            }
        }
        overlayView = null
        logContainer = null
        promptContainer = null
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
