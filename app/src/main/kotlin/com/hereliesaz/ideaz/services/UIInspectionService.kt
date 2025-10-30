package com.hereliesaz.ideaz.services

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.view.accessibility.AccessibilityEvent
import android.graphics.PixelFormat
import android.graphics.Color
import android.graphics.Paint
import android.graphics.PorterDuff
import android.view.Gravity
import android.view.WindowManager
import android.widget.FrameLayout
import android.content.BroadcastReceiver
import android.content.Context
import android.content.IntentFilter
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import android.view.accessibility.AccessibilityNodeInfo
import android.graphics.Rect
import android.view.MotionEvent

class UIInspectionService : AccessibilityService() {

    private var overlay: FrameLayout? = null
    private val highlightPaint = Paint().apply {
        color = Color.RED
        style = Paint.Style.STROKE
        strokeWidth = 5f
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
    }

    override fun onInterrupt() {
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        showOverlay()
    }

    override fun onUnbind(intent: Intent?): Boolean {
        hideOverlay()
        return super.onUnbind(intent)
    }

    private fun showOverlay() {
        val wm = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        overlay = object : FrameLayout(this) {
            override fun onTouchEvent(event: MotionEvent): Boolean {
                if (event.action == MotionEvent.ACTION_DOWN) {
                    val x = event.rawX.toInt()
                    val y = event.rawY.toInt()
                    findNode(x, y)
                }
                return super.onTouchEvent(event)
            }
        }
        val lp = WindowManager.LayoutParams().apply {
            type = WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY
            format = PixelFormat.TRANSLUCENT
            flags = flags or WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
            width = WindowManager.LayoutParams.MATCH_PARENT
            height = WindowManager.LayoutParams.MATCH_PARENT
        }
        wm.addView(overlay, lp)
    }

    private fun hideOverlay() {
        overlay?.let {
            val wm = getSystemService(Context.WINDOW_SERVICE) as WindowManager
            wm.removeView(it)
        }
    }

    private fun findNode(x: Int, y: Int) {
        val rootNode = rootInActiveWindow
        if (rootNode != null) {
            val node = findNodeAt(rootNode, x, y)
            if (node != null) {
                val resourceId = node.viewIdResourceName
                if (resourceId != null) {
                    val intent = Intent("com.hereliesaz.ideaz.INSPECTION_RESULT")
                    intent.putExtra("RESOURCE_ID", resourceId)
                    LocalBroadcastManager.getInstance(this).sendBroadcast(intent)
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
            val result = findNodeAt(child, x, y)
            if (result != null) {
                return result
            }
        }
        return root
    }
}
