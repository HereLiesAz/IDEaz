// --- NEW FILE ---
package com.hereliesaz.ideaz.services

import android.accessibilityservice.AccessibilityService
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo

/**
 * A skeleton Accessibility Service.
 * This is required for the system to list our app in the Accessibility settings.
 * We can add logic here later to inspect the view hierarchy.
 */
class IdeazAccessibilityService : AccessibilityService() {

    private val TAG = "IdeazAccessibility"

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        // Log.d(TAG, "onAccessibilityEvent: $event")

        // Inspection logic (Disabled for production safety)
        /*
        if (event?.eventType == AccessibilityEvent.TYPE_VIEW_CLICKED) {
            val root = rootInActiveWindow
            if (root != null) {
                Log.d(TAG, "--- Inspection Start ---")
                dumpNode(root)
                Log.d(TAG, "--- Inspection End ---")
                root.recycle()
            }
        }
        */
    }

    private fun dumpNode(node: AccessibilityNodeInfo, depth: Int = 0) {
        val prefix = "  ".repeat(depth)
        val id = node.viewIdResourceName ?: "no-id"
        val cls = node.className ?: "unknown-class"
        val text = node.text ?: ""
        val desc = node.contentDescription ?: ""

        Log.d(TAG, "$prefix Node: $cls | ID: $id | Text: $text | Desc: $desc")

        for (i in 0 until node.childCount) {
            val child = node.getChild(i)
            if (child != null) {
                dumpNode(child, depth + 1)
                child.recycle()
            }
        }
    }

    override fun onInterrupt() {
        Log.d(TAG, "onInterrupt")
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        Log.d(TAG, "Accessibility Service Connected")
    }
}