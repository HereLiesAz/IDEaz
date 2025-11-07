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
import android.widget.Button
import android.widget.EditText
import android.widget.ScrollView
import android.widget.TextView
import com.hereliesaz.ideaz.R

class UIInspectionService : AccessibilityService() {

    private var touchInterceptor: FrameLayout? = null
    private var overlayView: View? = null // The UI (prompt/log)
    private var windowManager: WindowManager? = null
    private var currentResourceId: String? = null

    // --- Broadcast Receiver for commands from ViewModel ---
    private val commandReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            when (intent?.action) {
                "com.hereliesaz.ideaz.SHOW_PROMPT" -> {
                    currentResourceId = intent.getStringExtra("RESOURCE_ID")
                    currentResourceId?.let {
                        // Must run UI on main thread
                        touchInterceptor?.post { showPromptUI(it) }
                    }
                }
                "com.hereliesaz.ideaz.AI_LOG" -> {
                    val message = intent.getStringExtra("MESSAGE") ?: "..."
                    // Must run UI on main thread
                    touchInterceptor?.post { updateLogUI(message) }
                }
                "com.hereliesaz.ideaz.TASK_FINISHED" -> {
                    currentResourceId = null
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
        showTouchInterceptor()

        // Register the command receiver
        val filter = IntentFilter().apply {
            addAction("com.hereliesaz.ideaz.SHOW_PROMPT")
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
        touchInterceptor = object : FrameLayout(this) {
            override fun onTouchEvent(event: MotionEvent): Boolean {
                if (event.action == MotionEvent.ACTION_DOWN) {
                    // Only process tap if no UI is active
                    if (overlayView == null) {
                        val x = event.rawX.toInt()
                        val y = event.rawY.toInt()
                        findNode(x, y)
                    }
                }
                // Let touches fall through to the prompt/log UI if it's visible
                return super.onTouchEvent(event)
            }
        }
        val lp = WindowManager.LayoutParams().apply {
            type = WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY
            format = PixelFormat.TRANSLUCENT
            // FLAG_NOT_FOCUSABLE lets touches pass through.
            flags = flags or WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
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

    private fun showPromptUI(resourceId: String) {
        hideOverlayUI() // Remove any existing UI

        overlayView = LayoutInflater.from(this).inflate(R.layout.inspection_overlay, touchInterceptor, false)

        val lp = WindowManager.LayoutParams().apply {
            type = WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY
            format = PixelFormat.TRANSLUCENT
            // This UI *needs* focus to type
            flags = WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
            width = WindowManager.LayoutParams.WRAP_CONTENT
            height = WindowManager.LayoutParams.WRAP_CONTENT
            gravity = Gravity.CENTER
        }

        val logText = overlayView?.findViewById<TextView>(R.id.log_text)
        val promptInput = overlayView?.findViewById<EditText>(R.id.prompt_input)
        val submitButton = overlayView?.findViewById<Button>(R.id.submit_button)
        val logContainer = overlayView?.findViewById<FrameLayout>(R.id.log_container)

        // Fix for nullability error
        logContainer?.visibility = View.GONE
        promptInput?.visibility = View.VISIBLE
        submitButton?.visibility = View.VISIBLE
        logText?.text = "AI Task for $resourceId"

        submitButton?.setOnClickListener {
            val prompt = promptInput?.text.toString() // Safe call
            if (prompt.isNotBlank()) {
                // User submitted prompt, hide input and show log
                promptInput?.visibility = View.GONE // Safe call
                submitButton?.visibility = View.GONE // Safe call
                logContainer?.visibility = View.VISIBLE

                // Send prompt back to ViewModel via broadcast
                val promptIntent = Intent("com.hereliesaz.ideaz.PROMPT_SUBMITTED").apply {
                    putExtra("RESOURCE_ID", resourceId)
                    putExtra("PROMPT", prompt)
                }
                sendBroadcast(promptIntent)
            }
        }

        windowManager?.addView(overlayView, lp)
    }

    private fun updateLogUI(message: String) {
        overlayView?.let {
            val logText = it.findViewById<TextView>(R.id.log_text)
            logText?.append("\n> $message")

            // Auto-scroll the log
            val scrollView = it.findViewById<ScrollView>(R.id.log_container)
            scrollView?.post { scrollView.fullScroll(View.FOCUS_DOWN) }
        }
    }

    private fun hideOverlayUI() {
        overlayView?.let {
            windowManager?.removeView(it)
        }
        overlayView = null
    }

    private fun findNode(x: Int, y: Int) {
        val rootNode = rootInActiveWindow
        if (rootNode != null) {
            val node = findNodeAt(rootNode, x, y)
            if (node != null) {
                val resourceId = node.viewIdResourceName
                if (resourceId != null) {
                    // Send broadcast to notify VM
                    val intent = Intent("com.hereliesaz.ideaz.INSPECTION_RESULT")
                    intent.putExtra("RESOURCE_ID", resourceId)
                    sendBroadcast(intent) // Use standard sendBroadcast
                }
            }
        }
    }

    private fun findNodeAt(root: AccessibilityNodeInfo, x: Int, y: Int): AccessibilityNodeInfo? {
        val rect = Rect()
        root.getBoundsInScreen(rect)
        if (!rect.contains(x, y)) {
            return null
        }

        for (i in 0 until root.childCount) {
            val child = root.getChild(i)
            if (child == null) continue
            val result = findNodeAt(child, x, y)
            if (result != null) {
                return result
            }
        }
        return root
    }
}