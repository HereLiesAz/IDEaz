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
 * Handles system-wide events via BroadcastReceivers.
 * Listens for AI prompts from notifications, selection events from the overlay service,
 * screenshot captures, and target app visibility changes.
 *
 * @param application The Application context.
 * @param aiDelegate Delegate to trigger AI tasks.
 * @param overlayDelegate Delegate to handle UI overlay updates.
 * @param stateDelegate Delegate to update shared UI state.
 */
class SystemEventDelegate(
    private val application: Application,
    private val aiDelegate: AIDelegate,
    private val overlayDelegate: OverlayDelegate,
    private val stateDelegate: StateDelegate,
    private val onReloadZipline: (String) -> Unit
) {

    private val promptReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            when (intent?.action) {
                "com.hereliesaz.ideaz.TOGGLE_SELECT_MODE" -> {
                    val enable = intent.getBooleanExtra("ENABLE", false)
                    overlayDelegate.setInternalSelectMode(enable)
                }
                "com.hereliesaz.ideaz.AI_PROMPT" -> {
                    val prompt = intent.getStringExtra("PROMPT")
                    if (!prompt.isNullOrBlank()) aiDelegate.startContextualAITask(prompt)
                }
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
                "com.hereliesaz.ideaz.SELECTION_MADE" -> {
                    val rect = if (Build.VERSION.SDK_INT >= 33) {
                        intent.getParcelableExtra("RECT", Rect::class.java)
                    } else {
                        @Suppress("DEPRECATION")
                        intent.getParcelableExtra("RECT")
                    }
                    if (rect != null) overlayDelegate.onSelectionMade(rect)
                }
                "com.hereliesaz.ideaz.SCREENSHOT_TAKEN" -> {
                    val base64 = intent.getStringExtra("BASE64_SCREENSHOT")
                    if (base64 != null) overlayDelegate.onScreenshotTaken(base64)
                }
                ACTION_AI_LOG -> {
                    val msg = intent.getStringExtra(EXTRA_MESSAGE)
                    if (!msg.isNullOrBlank()) stateDelegate.appendBuildLog(msg)
                }
                "com.hereliesaz.ideaz.RELOAD_ZIPLINE" -> {
                    val path = intent.getStringExtra("MANIFEST_PATH")
                    if (!path.isNullOrBlank()) onReloadZipline(path)
                }
            }
        }
    }

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
            addAction(ACTION_AI_LOG)
            addAction("com.hereliesaz.ideaz.RELOAD_ZIPLINE")
        }

        val visFilter = IntentFilter("com.hereliesaz.ideaz.TARGET_APP_VISIBILITY")

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            application.registerReceiver(promptReceiver, promptFilter, Context.RECEIVER_EXPORTED)
            application.registerReceiver(visibilityReceiver, visFilter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            ContextCompat.registerReceiver(application, promptReceiver, promptFilter, ContextCompat.RECEIVER_EXPORTED)
            ContextCompat.registerReceiver(application, visibilityReceiver, visFilter, ContextCompat.RECEIVER_NOT_EXPORTED)
        }
    }

    /**
     * Unregisters receivers to prevent memory leaks.
     */
    fun cleanup() {
        try { application.unregisterReceiver(promptReceiver) } catch (e: Exception) {}
        try { application.unregisterReceiver(visibilityReceiver) } catch (e: Exception) {}
    }
}
