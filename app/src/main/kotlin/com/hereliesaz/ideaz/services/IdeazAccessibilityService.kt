// --- NEW FILE ---
package com.hereliesaz.ideaz.services

import android.accessibilityservice.AccessibilityService
import android.util.Log
import android.view.accessibility.AccessibilityEvent

/**
 * A skeleton Accessibility Service.
 * This is required for the system to list our app in the Accessibility settings.
 * We can add logic here later to inspect the view hierarchy.
 */
class IdeazAccessibilityService : AccessibilityService() {

    private val TAG = "IdeazAccessibility"

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        Log.d(TAG, "onAccessibilityEvent: $event")
        // Later, we can use this to get node info without screenshots
    }

    override fun onInterrupt() {
        Log.d(TAG, "onInterrupt")
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        Log.d(TAG, "Accessibility Service Connected")
    }
}