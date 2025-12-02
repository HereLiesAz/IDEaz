package com.hereliesaz.ideaz.services

import android.accessibilityservice.AccessibilityService
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.PixelFormat
import android.graphics.Rect
import android.util.Log
import android.view.Gravity
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import androidx.core.content.ContextCompat
import com.hereliesaz.ideaz.ui.inspection.OverlayView

class UIInspectionService : AccessibilityService() {

    private val TAG = "UIInspectionService"
    private var windowManager: WindowManager? = null
    private var overlayView: OverlayView? = null
    private var layoutParams: WindowManager.LayoutParams? = null

    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "UIInspectionService Created")
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        registerBroadcastReceivers()
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        Log.d(TAG, "Accessibility Service Connected")
        setupOverlay()
    }

    private fun setupOverlay() {
        if (overlayView != null) return

        overlayView = OverlayView(this)

        // TYPE_ACCESSIBILITY_OVERLAY is crucial. It sits above other apps.
        // It requires the AccessibilityService to be active.
        val type = WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY

        layoutParams = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            type,
            // Start in "Interact Mode" (Pass-through)
            // FLAG_NOT_TOUCHABLE: Events pass through to the app behind.
            // FLAG_NOT_FOCUSABLE: We don't steal key input (Back button works for the app behind).
            WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                    WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        )

        layoutParams?.gravity = Gravity.TOP or Gravity.START

        try {
            windowManager?.addView(overlayView, layoutParams)
        } catch (e: Exception) {
            Log.e(TAG, "Error adding overlay view", e)
        }
    }

    /**
     * Updates the window flags to toggle between "Pass-through" and "Intercept" modes.
     */
    private fun setInteractionState(enableSelection: Boolean) {
        if (layoutParams == null || overlayView == null) return

        if (enableSelection) {
            // Select Mode: We want to catch touch events.
            // Remove FLAG_NOT_TOUCHABLE so OverlayView.onTouchEvent gets called.
            // Keep FLAG_NOT_FOCUSABLE so the system nav bar / keyboard still work mostly.
            layoutParams?.flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
            overlayView?.setSelectionMode(true)
        } else {
            // Interact Mode: Touches pass through.
            layoutParams?.flags = WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                    WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
            overlayView?.setSelectionMode(false)
            overlayView?.clearHighlight()
        }

        try {
            windowManager?.updateViewLayout(overlayView, layoutParams)
        } catch (e: Exception) {
            Log.e(TAG, "Error updating view layout", e)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        if (overlayView != null) {
            try {
                windowManager?.removeView(overlayView)
            } catch (e: Exception) {
                Log.e(TAG, "Error removing overlay view", e)
            }
            overlayView = null
        }
        try {
            unregisterReceiver(commandReceiver)
        } catch (e: Exception) {}
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        // Optional: Monitor events to auto-update highlights if the screen scrolls
    }

    override fun onInterrupt() {}

    // --- Broadcast Handling ---

    private val commandReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            when (intent?.action) {
                "com.hereliesaz.ideaz.TOGGLE_SELECT_MODE" -> {
                    val enable = intent.getBooleanExtra("ENABLE", false)
                    setInteractionState(enable)
                }
                "com.hereliesaz.ideaz.HIGHLIGHT_RECT" -> {
                    val rect = intent.getParcelableExtra<Rect>("RECT")
                    if (rect != null) {
                        overlayView?.updateHighlight(rect)
                    }
                }
                // Handle tap events forwarded from the OverlayView back to us
                "com.hereliesaz.ideaz.INTERNAL_TAP_DETECTED" -> {
                    val x = intent.getIntExtra("X", 0)
                    val y = intent.getIntExtra("Y", 0)
                    inspectNodeAt(x, y)
                }
            }
        }
    }

    private fun registerBroadcastReceivers() {
        val filter = IntentFilter().apply {
            addAction("com.hereliesaz.ideaz.TOGGLE_SELECT_MODE")
            addAction("com.hereliesaz.ideaz.HIGHLIGHT_RECT")
            addAction("com.hereliesaz.ideaz.INTERNAL_TAP_DETECTED")
        }
        // Use RECEIVER_EXPORTED (or equivalent logic) to allow app <-> service communication
        ContextCompat.registerReceiver(this, commandReceiver, filter, ContextCompat.RECEIVER_EXPORTED)
    }

    private fun inspectNodeAt(x: Int, y: Int) {
        val root = rootInActiveWindow ?: return

        // Find the leaf node at these coordinates
        val node = findNodeAt(root, x, y)

        if (node != null) {
            val rect = Rect()
            node.getBoundsInScreen(rect)

            // Draw visual feedback immediately
            overlayView?.updateHighlight(rect)

            // Identify the node
            val id = node.viewIdResourceName?.substringAfterLast(":id/")
                ?: node.text?.toString()?.take(20)
                ?: node.className?.toString()?.substringAfterLast(".")
                ?: "Unknown"

            // Send info back to the main app (ViewModel)
            val intent = Intent("com.hereliesaz.ideaz.PROMPT_SUBMITTED_NODE").apply {
                putExtra("RESOURCE_ID", id)
                putExtra("BOUNDS", rect)
                setPackage(packageName)
            }
            sendBroadcast(intent)
        }
    }

    private fun findNodeAt(root: AccessibilityNodeInfo, x: Int, y: Int): AccessibilityNodeInfo? {
        val rect = Rect()
        root.getBoundsInScreen(rect)
        if (!rect.contains(x, y)) return null

        // Search children (reverse order usually helps find top-most view in Z-order)
        for (i in root.childCount - 1 downTo 0) {
            val child = root.getChild(i)
            if (child != null) {
                val found = findNodeAt(child, x, y)
                if (found != null) return found
            }
        }
        return root
    }
}