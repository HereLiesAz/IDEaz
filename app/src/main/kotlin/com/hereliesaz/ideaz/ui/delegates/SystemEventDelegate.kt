package com.hereliesaz.ideaz.ui.delegates

import android.app.Application
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Rect
import android.os.Build
import androidx.core.content.ContextCompat
import com.hereliesaz.ideaz.models.ACTION_AI_LOG
import com.hereliesaz.ideaz.models.EXTRA_MESSAGE

/**
 * Delegate responsible for handling system-wide events via BroadcastReceivers.
 *
 * **Role:**
 * Acts as the event bus consumer for the application. It listens for:
 * - **Overlay Interactions:** When the user taps "Select" or submits a prompt from the floating overlay.
 * - **Service Events:** When screenshots are captured or services change state.
 * - **Zipline Hot Reloads:** Signals from the build system that new code is ready.
 * - **App Visibility:** Tracking if the target app is foregrounded.
 *
 * @param application The Application context.
 * @param aiDelegate Delegate to trigger AI tasks from overlay prompts.
 * @param overlayDelegate Delegate to update selection state.
 * @param stateDelegate Delegate to update shared UI state (logs, visibility).
 * @param onReloadZipline Callback to trigger Zipline code reload.
 */
class SystemEventDelegate(
    private val application: Application,
    private val aiDelegate: AIDelegate,
    private val overlayDelegate: OverlayDelegate,
    private val stateDelegate: StateDelegate,
    private val onReloadZipline: ((String) -> Unit)? = null
) {

    /**
     * Receiver for Overlay and AI related events.
     */
    private val promptReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            when (intent?.action) {
                // Toggled via Notification or Floating Button
                "com.hereliesaz.ideaz.TOGGLE_SELECT_MODE" -> {
                    val enable = intent.getBooleanExtra("ENABLE", false)
                    overlayDelegate.setInternalSelectMode(enable)
                }
                // User entered a prompt in the overlay
                "com.hereliesaz.ideaz.AI_PROMPT" -> {
                    val prompt = intent.getStringExtra("PROMPT")
                    if (!prompt.isNullOrBlank()) aiDelegate.startContextualAITask(prompt)
                }
                // User tapped a specific UI node (Accessibility Node)
                "com.hereliesaz.ideaz.PROMPT_SUBMITTED_NODE" -> {
                    val rect = if (Build.VERSION.SDK_INT >= 33) {
                        intent.getParcelableExtra("BOUNDS", Rect::class.java)
                    } else {
                        @Suppress("DEPRECATION")
                        intent.getParcelableExtra("BOUNDS")
                    }
                    val id = intent.getStringExtra("RESOURCE_ID")
                    if (rect != null) overlayDelegate.onSelectionMade(rect, id)
                }
                // User dragged a selection rectangle
                "com.hereliesaz.ideaz.SELECTION_MADE" -> {
                    val rect = if (Build.VERSION.SDK_INT >= 33) {
                        intent.getParcelableExtra("RECT", Rect::class.java)
                    } else {
                        @Suppress("DEPRECATION")
                        intent.getParcelableExtra("RECT")
                    }
                    if (rect != null) overlayDelegate.onSelectionMade(rect)
                }
                // Screenshot service completed capture
                "com.hereliesaz.ideaz.SCREENSHOT_TAKEN" -> {
                    val base64 = intent.getStringExtra("BASE64_SCREENSHOT")
                    if (base64 != null) overlayDelegate.onScreenshotTaken(base64)
                }
                // Build system finished compiling Zipline code
                "com.hereliesaz.ideaz.RELOAD_ZIPLINE" -> {
                    val path = intent.getStringExtra("MANIFEST_PATH")
                    if (path != null) {
                        onReloadZipline?.invoke(path)
                    }
                }
                // General AI logs from other components
                ACTION_AI_LOG -> {
                    val msg = intent.getStringExtra(EXTRA_MESSAGE)
                    if (!msg.isNullOrBlank()) stateDelegate.appendBuildLog(msg)
                }
            }
        }
    }

    /**
     * Receiver for Target App visibility changes.
     * Used to sync the IDE UI state with the visibility of the embedded app.
     */
    private val visibilityReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == "com.hereliesaz.ideaz.TARGET_APP_VISIBILITY") {
                val visible = intent.getBooleanExtra("IS_VISIBLE", false)
                stateDelegate.setTargetAppVisible(visible)
            }
        }
    }

    init {
        val promptFilter = IntentFilter().apply {
            addAction("com.hereliesaz.ideaz.TOGGLE_SELECT_MODE")
            addAction("com.hereliesaz.ideaz.AI_PROMPT")
            addAction("com.hereliesaz.ideaz.PROMPT_SUBMITTED_NODE")
            addAction("com.hereliesaz.ideaz.SELECTION_MADE")
            addAction("com.hereliesaz.ideaz.SCREENSHOT_TAKEN")
            addAction("com.hereliesaz.ideaz.RELOAD_ZIPLINE")
            addAction(ACTION_AI_LOG)
        }

        val visFilter = IntentFilter("com.hereliesaz.ideaz.TARGET_APP_VISIBILITY")

        // Register receivers with appropriate flags for Android 13+ (Tiramisu)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            application.registerReceiver(promptReceiver, promptFilter, Context.RECEIVER_NOT_EXPORTED)
            application.registerReceiver(visibilityReceiver, visFilter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            ContextCompat.registerReceiver(application, promptReceiver, promptFilter, ContextCompat.RECEIVER_NOT_EXPORTED)
            ContextCompat.registerReceiver(application, visibilityReceiver, visFilter, ContextCompat.RECEIVER_NOT_EXPORTED)
        }
    }

    /**
     * Unregisters receivers to prevent memory leaks.
     * Must be called when the ViewModel is cleared.
     */
    fun cleanup() {
        try { application.unregisterReceiver(promptReceiver) } catch (e: Exception) {}
        try { application.unregisterReceiver(visibilityReceiver) } catch (e: Exception) {}
    }
}
