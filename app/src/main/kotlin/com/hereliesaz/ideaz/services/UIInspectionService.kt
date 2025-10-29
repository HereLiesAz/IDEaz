package com.hereliesaz.ideaz.services

import android.accessibilityservice.AccessibilityService
import android.view.accessibility.AccessibilityEvent
import com.hereliesaz.ideaz.ui.inspection.InspectionEvents
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch

class UIInspectionService : AccessibilityService() {

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        GlobalScope.launch {
            InspectionEvents.emit("Accessibility event: ${event?.eventType}")
        }
    }

    override fun onInterrupt() {
        // Not yet implemented
    }
}
