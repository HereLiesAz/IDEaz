package com.hereliesaz.ideaz.services

import android.accessibilityservice.AccessibilityService
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Rect
import android.os.Build
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo

/**
 * An Accessibility Service that inspects the view hierarchy.
 * It listens for tap events broadcast by the OverlayView, finds the
 * UI element under the tap, and reports it back to the main app.
 */
class IdeazAccessibilityService : AccessibilityService() {

    private val TAG = "IdeazAccessibility"

    private val tapReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == "com.hereliesaz.ideaz.INTERNAL_TAP_DETECTED") {
                val x = intent.getIntExtra("X", -1)
                val y = intent.getIntExtra("Y", -1)
                if (x != -1 && y != -1) {
                    inspectAt(x, y)
                }
            }
        }
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        Log.d(TAG, "Accessibility Service Connected")

        val filter = IntentFilter("com.hereliesaz.ideaz.INTERNAL_TAP_DETECTED")
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(tapReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            registerReceiver(tapReceiver, filter)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        try {
            unregisterReceiver(tapReceiver)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        // We only care about explicit inspection via tap
    }

    override fun onInterrupt() {
        Log.d(TAG, "onInterrupt")
    }

    private fun inspectAt(x: Int, y: Int) {
        val root = rootInActiveWindow ?: return

        // Traverse to find the smallest node containing (x,y)
        val leaf = findLeafNode(root, x, y)

        if (leaf != null) {
            val bounds = Rect()
            leaf.getBoundsInScreen(bounds)

            val resourceId = leaf.viewIdResourceName

            Log.d(TAG, "Found Node: $resourceId at $bounds")

            val intent = Intent("com.hereliesaz.ideaz.PROMPT_SUBMITTED_NODE").apply {
                putExtra("BOUNDS", bounds)
                if (resourceId != null) {
                    putExtra("RESOURCE_ID", resourceId)
                }
                setPackage(packageName) // Send to self (the app)
            }
            sendBroadcast(intent)

            if (leaf != root) {
                // If we found a child, we must recycle the root separately?
                // No, findLeafNode recycling logic handles intermediate children.
                // Root is passed in, findLeafNode checks it.
                // If it returns a child, it does NOT recycle root.
                // So we must recycle root.
                root.recycle()
            }
            leaf.recycle()
        } else {
            root.recycle()
        }
    }

    private fun findLeafNode(node: AccessibilityNodeInfo, x: Int, y: Int): AccessibilityNodeInfo? {
        val bounds = Rect()
        node.getBoundsInScreen(bounds)

        if (!bounds.contains(x, y)) {
            return null // Node doesn't contain point
        }

        // Check children (last one on top usually)
        for (i in node.childCount - 1 downTo 0) {
            val child = node.getChild(i)
            if (child != null) {
                val leaf = findLeafNode(child, x, y)
                if (leaf != null) {
                    // We found a descendant.
                    // If leaf is different from child, it means child was just a container.
                    // We recycle child if it's not the returned leaf.
                    if (leaf != child) {
                        child.recycle()
                    }
                    return leaf
                }
                child.recycle() // Child didn't contain it
            }
        }

        // No child contains it, but this node does. Return this node.
        return node
    }
}
