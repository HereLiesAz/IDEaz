package com.hereliesaz.ideaz.ui.delegates

import android.app.Application
import android.content.Intent
import android.graphics.Rect
import android.widget.Toast
import com.hereliesaz.ideaz.models.SourceMapEntry
import com.hereliesaz.ideaz.services.IdeazOverlayService
import com.hereliesaz.ideaz.ui.SettingsViewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

class OverlayDelegate(
    private val application: Application,
    private val settings: SettingsViewModel,
    private val scope: CoroutineScope,
    private val onAiLog: (String) -> Unit
) {

    private val _isSelectMode = MutableStateFlow(false)
    val isSelectMode = _isSelectMode.asStateFlow()

    private val _activeSelectionRect = MutableStateFlow<Rect?>(null)
    val activeSelectionRect = _activeSelectionRect.asStateFlow()

    private val _isContextualChatVisible = MutableStateFlow(false)
    val isContextualChatVisible = _isContextualChatVisible.asStateFlow()

    var sourceMap: Map<String, SourceMapEntry>? = null

    fun toggleSelectMode(enable: Boolean) {
        _isSelectMode.value = enable
        if (enable) {
            Toast.makeText(application, "Select Mode Enabled", Toast.LENGTH_SHORT).show()
        } else {
            clearSelection()
        }
    }

    fun clearSelection() {
        _activeSelectionRect.value = null
        _isContextualChatVisible.value = false
        _isSelectMode.value = false
    }

    fun setScreenCapturePermission(code: Int, data: Intent) {
        // Store permission intent for ScreenshotService
    }

    fun hasScreenCapturePermission(): Boolean {
        // Logic to check if we have the media projection token
        return false
    }

    fun requestScreenCapturePermission() {
        // UI should trigger the intent
    }
}