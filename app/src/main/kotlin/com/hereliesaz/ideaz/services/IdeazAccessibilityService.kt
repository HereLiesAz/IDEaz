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
import android.view.accessibility.AccessibilityWindowInfo
import androidx.preference.PreferenceManager

/**
 * An Accessibility Service that inspects the view hierarchy.
 * It listens for tap events broadcast by the OverlayView, finds the
 * UI element under the tap, and reports it back to the main app.
 */
class IdeazAccessibilityService : AccessibilityService() {

    private val TAG = "IdeazAccessibility"

    private val tapReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            when (intent?.action) {
                "com.hereliesaz.ideaz.INTERNAL_TAP_DETECTED" -> {
                    val x = intent.getIntExtra("X", -1)
                    val y = intent.getIntExtra("Y", -1)
                    if (x != -1 && y != -1) {
                        inspectAt(x, y)
                    }
                }
                // IdeazOverlayService is about to intercept touches and needs to
                // know the worked-on app's window bounds so it can constrain itself.
                "com.hereliesaz.ideaz.REQUEST_TARGET_BOUNDS" -> reportTargetWindowBounds()
            }
        }
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        Log.d(TAG, "Accessibility Service Connected")

        val filter = IntentFilter().apply {
            addAction("com.hereliesaz.ideaz.INTERNAL_TAP_DETECTED")
            addAction("com.hereliesaz.ideaz.REQUEST_TARGET_BOUNDS")
        }
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
            android.util.Log.w("IdeazAccessibility", "Receiver unregister failed", e)
        }
    }

    @android.annotation.SuppressLint("SwitchIntDef")
    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        // Inspection itself is driven by explicit taps. We do watch window changes
        // so the overlay's interception region keeps tracking the target app as it
        // opens, moves, or resizes.
        when (event?.eventType) {
            AccessibilityEvent.TYPE_WINDOWS_CHANGED,
            AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED -> reportTargetWindowBounds()
        }
    }

    override fun onInterrupt() {
        Log.d(TAG, "onInterrupt")
    }

    /** The package name of the app currently being worked on, if configured. */
    private fun targetPackage(): String? =
        PreferenceManager.getDefaultSharedPreferences(this)
            .getString("target_package_name", null)
            ?.takeIf { it.isNotBlank() }

    /**
     * Broadcasts the screen bounds of the target app's window (or no BOUNDS extra
     * if it isn't on screen) so [IdeazOverlayService] can constrain its
     * touch-intercepting overlay to exactly that region.
     */
    private fun reportTargetWindowBounds() {
        val pkg = targetPackage()
        val rect = if (pkg == null) null else findWindowBounds(pkg)
        val intent = Intent("com.hereliesaz.ideaz.TARGET_WINDOW_BOUNDS").apply {
            if (rect != null) putExtra("BOUNDS", rect)
            setPackage(packageName)
        }
        sendBroadcast(intent)
    }

    private fun findWindowBounds(pkg: String): Rect? {
        return try {
            windows?.firstOrNull { w ->
                w.type == AccessibilityWindowInfo.TYPE_APPLICATION &&
                    w.root?.packageName?.toString() == pkg
            }?.let { w ->
                val r = Rect()
                w.getBoundsInScreen(r)
                r.takeIf { !it.isEmpty }
            }
        } catch (e: Exception) {
            Log.w(TAG, "findWindowBounds failed", e)
            null
        }
    }

    private fun inspectAt(x: Int, y: Int) {
        val root = rootInActiveWindow ?: return

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
        }
    }

    /**
     * Find the smallest a11y node that contains (x, y), preferring nodes
     * drawn on top.
     */
    private fun findLeafNode(node: AccessibilityNodeInfo, x: Int, y: Int): AccessibilityNodeInfo? {
        val bounds = Rect()
        node.getBoundsInScreen(bounds)

        if (!bounds.contains(x, y)) {
            return null
        }

        // Prefer children drawn on top. drawingOrder reflects ViewGroup's
        // post-elevation paint order; falling back to descending index handles
        // the default LinearLayout case where last-added is drawn on top.
        val ordered = (0 until node.childCount).mapNotNull { i ->
            val child = node.getChild(i) ?: return@mapNotNull null
            i to child
        }.sortedByDescending { (i, child) ->
            child.drawingOrder.takeIf { it >= 0 } ?: i
        }

        var foundLeaf: AccessibilityNodeInfo? = null
        for ((_, child) in ordered) {
            val leaf = findLeafNode(child, x, y)
            if (leaf != null) {
                foundLeaf = leaf
                break
            }
        }

        // No child matched — `node` itself contains (x, y). Return it.
        return foundLeaf ?: node
    }

}
