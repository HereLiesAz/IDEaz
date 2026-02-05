package com.hereliesaz.ideaz.ui.delegates

import android.app.Activity
import android.app.Application
import android.content.Intent
import android.graphics.Rect
import android.net.Uri
import com.hereliesaz.ideaz.models.SourceMapEntry
import com.hereliesaz.ideaz.services.ScreenshotService
import com.hereliesaz.ideaz.ui.SettingsViewModel
import com.hereliesaz.ideaz.utils.SourceContextHelper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Delegate responsible for managing the UI Overlay, Selection Mode, and Screen Capture.
 *
 * **Role:**
 * This delegate bridges the gap between the main IDE UI and the "Overlay" experience.
 * It coordinates:
 * 1.  **Service Lifecycle:** Starting/Stopping the [IdeazOverlayService].
 * 2.  **Selection State:** Tracking whether the user is in "Select Mode" (inspection).
 * 3.  **Context Resolution:** Mapping selected UI coordinates/IDs to source code locations using [SourceContextHelper].
 * 4.  **Screen Capture:** orchestrating the [ScreenshotService] to capture the selected region.
 *
 * @param application The Application context.
 * @param settingsViewModel ViewModel to access project path/name.
 * @param scope CoroutineScope for background operations.
 * @param onOverlayLog Callback to log messages to the UI.
 */
class OverlayDelegate(
    private val application: Application,
    private val settingsViewModel: SettingsViewModel,
    private val scope: CoroutineScope,
    private val onOverlayLog: (String) -> Unit
) {

    // --- StateFlows ---

    private val _isSelectMode = MutableStateFlow(false)
    /**
     * Whether the "Select" (inspection) mode is active.
     * When active, the Overlay Service intercepts touches to allow element selection.
     */
    val isSelectMode = _isSelectMode.asStateFlow()

    private val _isContextualChatVisible = MutableStateFlow(false)
    /**
     * Whether the contextual chat bubble/window is visible.
     * Typically becomes true after a selection is made and screenshot taken.
     */
    val isContextualChatVisible = _isContextualChatVisible.asStateFlow()

    private val _activeSelectionRect = MutableStateFlow<Rect?>(null)
    /** The currently selected screen area rectangle (in screen coordinates). */
    val activeSelectionRect = _activeSelectionRect.asStateFlow()

    private val _requestScreenCapture = MutableStateFlow(false)
    /**
     * Signal to the UI to request screen capture permission (MediaProjection) from the user.
     * The Delegate cannot request permissions directly as it requires an Activity context.
     */
    val requestScreenCapture = _requestScreenCapture.asStateFlow()

    // --- Internal State ---

    private var screenCaptureResultCode: Int? = null
    private var screenCaptureData: Intent? = null

    /**
     * Context information derived from the selected element (e.g., "File: Main.kt, Line: 42").
     * Used to prepopulate the AI prompt.
     */
    var pendingContextInfo: String? = null
        private set

    /** Base64 encoded screenshot of the selected area. */
    var pendingBase64Screenshot: String? = null
        private set

    private var pendingRect: Rect? = null

    /**
     * Map of view IDs to source file locations.
     * Must be updated by [MainViewModel] whenever a new build generates a source map.
     */
    var sourceMap: Map<String, SourceMapEntry> = emptyMap()

    // --- Public Operations ---

    /**
     * Toggles the selection mode.
     * Starts or stops the [IdeazOverlayService].
     *
     * @param enable True to enable selection mode, False to disable.
     */
    fun toggleSelectMode(enable: Boolean) {
        if (enable) {
            // Check for System Alert Window permission
            if (!android.provider.Settings.canDrawOverlays(application)) {
                val intent = Intent(
                    android.provider.Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    Uri.parse("package:${application.packageName}")
                )
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                application.startActivity(intent)
                return
            }

            // Start Service with ENABLE flag
            val serviceIntent = Intent(application, com.hereliesaz.ideaz.services.IdeazOverlayService::class.java).apply {
                putExtra("ENABLE", true)
            }
            application.startForegroundService(serviceIntent)
        } else {
            // Stop Service
            val serviceIntent = Intent(application, com.hereliesaz.ideaz.services.IdeazOverlayService::class.java)
            application.stopService(serviceIntent)
        }

        setInternalSelectMode(enable)

        // Pre-emptively request screen capture permission if missing
        if (enable && !hasScreenCapturePermission()) {
            _requestScreenCapture.value = true
        }
    }

    /**
     * Updates the internal selection mode state without triggering side effects like broadcasts.
     * Used by [SystemEventDelegate] to sync state when the Overlay Service toggles itself (e.g., via floating button).
     */
    fun setInternalSelectMode(enable: Boolean) {
        _isSelectMode.value = enable
    }

    /**
     * Clears the current selection state, hides the chat, and notifies the Overlay Service to remove highlights.
     */
    fun clearSelection() {
        _activeSelectionRect.value = null
        _isContextualChatVisible.value = false
        pendingRect = null

        // Notify Overlay Service to clear drawings
        val intent = Intent("com.hereliesaz.ideaz.HIGHLIGHT_RECT").apply {
            setPackage(application.packageName)
        }
        application.sendBroadcast(intent)
    }

    /**
     * Called when a user makes a selection (drag or tap).
     *
     * **Logic:**
     * 1. Updates state with the new Rect.
     * 2. Resolves source context if a resource ID is provided (using R8 mapping/SourceMap).
     * 3. Triggers a screenshot of the selected area.
     *
     * @param rect The screen bounds of the selection.
     * @param resourceId The Android resource ID name (e.g., "id/myButton") if available.
     */
    fun onSelectionMade(rect: Rect, resourceId: String? = null) {
        pendingRect = rect
        _activeSelectionRect.value = rect
        _isSelectMode.value = false // Exit selection mode after making a selection

        scope.launch {
            if (resourceId != null && resourceId != "contextless_chat") {
                val appName = settingsViewModel.getAppName()
                if (!appName.isNullOrBlank()) {
                    val projectDir = settingsViewModel.getProjectPath(appName)

                    // Resolve Context on IO thread
                    val contextResult = withContext(Dispatchers.IO) {
                        SourceContextHelper.resolveContext(resourceId, projectDir, sourceMap)
                    }

                    if (!contextResult.isError) {
                        pendingContextInfo = """
                            Context (Element $resourceId):
                            File: ${contextResult.file}
                            Line: ${contextResult.line}
                            """.trimIndent()
                    } else {
                        pendingContextInfo = "Context: Element ID $resourceId"
                    }
                } else {
                    pendingContextInfo = "Context: Element ID $resourceId (No Project Loaded)"
                }
            } else {
                pendingContextInfo = "Context: Screen area Rect(${rect.left}, ${rect.top}, ${rect.right}, ${rect.bottom})"
            }

            takeScreenshot(rect)
        }
    }

    /**
     * Captures a screenshot of the specified area.
     * Uses [ScreenshotService] (foreground service) because MediaProjection requires a foreground service context on newer Android versions.
     */
    private fun takeScreenshot(rect: Rect) {
        if (!hasScreenCapturePermission()) {
            onOverlayLog("Error: Missing screen capture permission.")
            return
        }

        scope.launch {
            // Temporarily hide the highlight to get a clean screenshot
            val clearIntent = Intent("com.hereliesaz.ideaz.HIGHLIGHT_RECT").apply {
                setPackage(application.packageName)
            }
            application.sendBroadcast(clearIntent)

            delay(250) // Wait for UI to update

            val serviceIntent = Intent(application, ScreenshotService::class.java).apply {
                putExtra(ScreenshotService.EXTRA_RESULT_CODE, screenCaptureResultCode)
                putExtra(ScreenshotService.EXTRA_DATA, screenCaptureData)
                putExtra(ScreenshotService.EXTRA_RECT, rect)
            }
            application.startForegroundService(serviceIntent)
        }
    }

    /**
     * Callback invoked by [SystemEventDelegate] when the screenshot is ready (via Broadcast).
     * Shows the context chat and restores the highlight overlay.
     */
    fun onScreenshotTaken(base64: String) {
        // Restore highlight
        val intent = Intent("com.hereliesaz.ideaz.HIGHLIGHT_RECT").apply {
            setPackage(application.packageName)
            putExtra("RECT", pendingRect)
        }
        application.sendBroadcast(intent)

        pendingBase64Screenshot = base64
        _isContextualChatVisible.value = true
    }

    // --- Permission Management ---

    fun hasScreenCapturePermission() = screenCaptureData != null
    fun requestScreenCapturePermission() { _requestScreenCapture.value = true }
    fun screenCaptureRequestHandled() { _requestScreenCapture.value = false }

    /**
     * Stores the MediaProjection permission result for later use by the ScreenshotService.
     */
    fun setScreenCapturePermission(code: Int, data: Intent?) {
        if (code == Activity.RESULT_OK) {
            screenCaptureResultCode = code
            screenCaptureData = data
        }
    }
}
