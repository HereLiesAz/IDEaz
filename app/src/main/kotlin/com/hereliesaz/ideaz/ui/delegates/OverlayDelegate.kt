package com.hereliesaz.ideaz.ui.delegates

import android.app.Activity
import android.app.Application
import android.content.Intent
import android.graphics.Rect
import android.net.Uri
import com.hereliesaz.ideaz.services.IdeazOverlayService
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

class OverlayDelegate(
    private val application: Application,
    private val settingsViewModel: SettingsViewModel,
    private val scope: CoroutineScope,
    private val onOverlayLog: (String) -> Unit
) {

    private val _isSelectMode = MutableStateFlow(false)
    val isSelectMode = _isSelectMode.asStateFlow()

    private val _isContextualChatVisible = MutableStateFlow(false)
    val isContextualChatVisible = _isContextualChatVisible.asStateFlow()

    private val _activeSelectionRect = MutableStateFlow<Rect?>(null)
    val activeSelectionRect = _activeSelectionRect.asStateFlow()

    private val _requestScreenCapture = MutableStateFlow(false)
    val requestScreenCapture = _requestScreenCapture.asStateFlow()

    private var screenCaptureResultCode: Int? = null
    private var screenCaptureData: Intent? = null

    var pendingContextInfo: String? = null
        private set
    var pendingBase64Screenshot: String? = null
        private set
    private var pendingRect: Rect? = null

    // This must be set by MainViewModel when sourceMap is updated
    var sourceMap: Map<String, SourceMapEntry> = emptyMap()

    fun toggleSelectMode(enable: Boolean) {
        if (enable && android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M && !android.provider.Settings.canDrawOverlays(application)) {
            val intent = Intent(android.provider.Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:${application.packageName}")).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            application.startActivity(intent)
            onOverlayLog("Please grant overlay permission.")
            return
        }

        _isSelectMode.value = enable

        val serviceIntent = Intent(application, com.hereliesaz.ideaz.services.UIInspectionService::class.java)
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            application.startForegroundService(serviceIntent)
        } else {
            application.startService(serviceIntent)
        }

        val broadcastIntent = Intent("com.hereliesaz.ideaz.TOGGLE_SELECT_MODE").apply {
            putExtra("ENABLE", enable)
            setPackage(application.packageName)
        }
        application.sendBroadcast(broadcastIntent)

        if (enable && !hasScreenCapturePermission()) {
            _requestScreenCapture.value = true
        }
    }

    fun clearSelection() {
        _activeSelectionRect.value = null
        _isContextualChatVisible.value = false
        pendingRect = null

        val intent = Intent("com.hereliesaz.ideaz.HIGHLIGHT_RECT").apply {
            setPackage(application.packageName)
        }
        application.sendBroadcast(intent)
    }

    fun onSelectionMade(rect: Rect, resourceId: String? = null) {
        pendingRect = rect
        _activeSelectionRect.value = rect
        _isSelectMode.value = false

        scope.launch {
            if (resourceId != null && resourceId != "contextless_chat") {
                val appName = settingsViewModel.getAppName()
                if (!appName.isNullOrBlank()) {
                    val projectDir = settingsViewModel.getProjectPath(appName)
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

    private fun takeScreenshot(rect: Rect) {
        if (!hasScreenCapturePermission()) {
            onOverlayLog("Error: Missing screen capture permission.")
            return
        }

        scope.launch {
            val clearIntent = Intent("com.hereliesaz.ideaz.HIGHLIGHT_RECT").apply {
                setPackage(application.packageName)
            }
            application.sendBroadcast(clearIntent)

            delay(250)
            val serviceIntent = Intent(application, ScreenshotService::class.java).apply {
                putExtra(ScreenshotService.EXTRA_RESULT_CODE, screenCaptureResultCode)
                putExtra(ScreenshotService.EXTRA_DATA, screenCaptureData)
                putExtra(ScreenshotService.EXTRA_RECT, rect)
            }
            application.startForegroundService(serviceIntent)
        }
    }

    fun onScreenshotTaken(base64: String) {
        val intent = Intent("com.hereliesaz.ideaz.HIGHLIGHT_RECT").apply {
            setPackage(application.packageName)
            putExtra("RECT", pendingRect)
        }
        application.sendBroadcast(intent)

        pendingBase64Screenshot = base64
        _isContextualChatVisible.value = true

        // Launch overlay service if not running, or update it
        if (android.provider.Settings.canDrawOverlays(application)) {
            val serviceIntent = Intent(application, IdeazOverlayService::class.java)
            androidx.core.content.ContextCompat.startForegroundService(application, serviceIntent)
        } else {
            val intent = Intent(android.provider.Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:${application.packageName}")).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            application.startActivity(intent)
            onOverlayLog("Please grant overlay permission.")
        }
    }

    fun hasScreenCapturePermission() = screenCaptureData != null
    fun requestScreenCapturePermission() { _requestScreenCapture.value = true }
    fun screenCaptureRequestHandled() { _requestScreenCapture.value = false }
    fun setScreenCapturePermission(code: Int, data: Intent?) {
        if (code == Activity.RESULT_OK) {
            screenCaptureResultCode = code
            screenCaptureData = data
        }
    }
}