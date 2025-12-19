package com.hereliesaz.ideaz.ui.delegates

import android.app.Application

class SystemEventDelegate(
    private val application: Application,
    private val aiDelegate: AIDelegate,
    private val overlayDelegate: OverlayDelegate,
    private val stateDelegate: StateDelegate
) {
    fun flushNonFatalErrors() {
        // Logic to flush errors
    }
}