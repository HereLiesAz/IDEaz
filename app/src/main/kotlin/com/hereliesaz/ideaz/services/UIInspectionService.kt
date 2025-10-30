package com.hereliesaz.ideaz.services

import android.accessibilityservice.AccessibilityService
import android.view.accessibility.AccessibilityEvent
import com.hereliesaz.ideaz.ui.inspection.InspectionEvents
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import android.view.WindowManager
import android.graphics.PixelFormat
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.accessibility.AccessibilityNodeInfo
import com.hereliesaz.ideaz.R

class UIInspectionService : AccessibilityService() {

    private lateinit var windowManager: WindowManager
    private var overlayView: View? = null

    override fun onServiceConnected() {
        super.onServiceConnected()
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        showOverlay()
    }

    private fun showOverlay() {
        overlayView = LayoutInflater.from(this).inflate(R.layout.inspection_overlay, null)

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        )
        params.gravity = Gravity.TOP or Gravity.START

        overlayView?.setOnTouchListener { _, event ->
            findNodeAt(event.rawX, event.rawY)
            true
        }

        windowManager.addView(overlayView, params)
    }

    private fun findNodeAt(x: Float, y: Float) {
        val rootNode = rootInActiveWindow ?: return
        val node = findSmallestNodeAt(rootNode, x, y)
        val resourceId = node?.viewIdResourceName
        GlobalScope.launch {
            if (resourceId != null) {
                InspectionEvents.emit("Node selected: $resourceId")
            } else {
                InspectionEvents.emit("No node found with a resource ID.")
            }
        }
    }

    private fun findSmallestNodeAt(root: AccessibilityNodeInfo, x: Float, y: Float): AccessibilityNodeInfo? {
        val outRect = android.graphics.Rect()
        var smallestNode: AccessibilityNodeInfo? = null

        fun traverse(node: AccessibilityNodeInfo) {
            node.getBoundsInScreen(outRect)
            if (outRect.contains(x.toInt(), y.toInt())) {
                smallestNode = node
                for (i in 0 until node.childCount) {
                    val child = node.getChild(i)
                    if (child != null) {
                        traverse(child)
                    }
                }
            }
        }

        traverse(root)
        return smallestNode
    }


    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        // We are handling inspection via touch, so this can be minimal
    }

    override fun onInterrupt() {
        // Not yet implemented
    }

    override fun onDestroy() {
        super.onDestroy()
        if (overlayView != null) {
            windowManager.removeView(overlayView)
        }
    }
}
