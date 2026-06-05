package com.hereliesaz.ideaz.services

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.ClipData
import android.content.ClipboardManager
import android.content.pm.ServiceInfo
import android.graphics.PixelFormat
import android.graphics.Rect
import android.os.Build
import android.os.IBinder
import android.provider.Settings
import android.view.Gravity
import android.view.WindowManager
import androidx.core.app.NotificationCompat
import com.hereliesaz.ideaz.MainActivity
import com.hereliesaz.ideaz.R
import com.hereliesaz.ideaz.ui.inspection.OverlayView

/**
 * A Foreground Service that draws a System Alert Window overlay over other applications.
 *
 * **Purpose:**
 * Enables the "Selection Mode" feature, allowing users to:
 * 1.  Draw rectangles over the target app to select areas.
 * 2.  Interact with the "Post-Code" UI (chat bubbles) that floats above the app.
 *
 * **Key Mechanics:**
 * - **TYPE_APPLICATION_OVERLAY:** Uses this Window Type to appear above other apps.
 * - **Pass-through Logic:** Dynamically toggles `FLAG_NOT_TOUCHABLE`.
 *   - When `isSelectMode = true`: Touches are intercepted by the overlay (Selection).
 *   - When `isSelectMode = false`: Touches pass through to the underlying app (Interaction),
 *     but visual elements (like the chat bubble) remain interactive.
 */
class IdeazOverlayService : Service() {

    private lateinit var windowManager: WindowManager
    private var overlayView: OverlayView? = null
    private var isOverlayAdded = false

    /** Dark, touch-absorbing "please wait" scrim shown while the bridge runs. */
    private var blockView: android.view.View? = null

    /** Whether selection mode is currently active (touch interception requested). */
    private var selectMode = false

    /**
     * Whether the Gemini-bridge "block" is active. When true, a full-screen
     * touchable overlay absorbs ALL input so the user can't disturb the Gemini
     * app while the bridge drives and scrapes it. Cleared by the bridge when the
     * response arrives or times out.
     */
    private var blockMode = false

    /**
     * Screen bounds of the app currently being worked on, as reported by
     * [IdeazAccessibilityService]. When set, the touch-intercepting overlay is
     * constrained to exactly this region so it never blocks IDEaz, other apps, or
     * the system bars. Null means "no target window on screen" — the overlay then
     * stays fully pass-through (e.g. web/PWA projects, whose selection is handled
     * by the in-app SelectionOverlay).
     */
    private var targetBounds: Rect? = null

    /**
     * Receiver for commands from the main app process.
     */
    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            when (intent?.action) {
                // Command to enable/disable selection mode (intercept touches)
                "com.hereliesaz.ideaz.TOGGLE_SELECT_MODE" -> {
                    val enable = intent.getBooleanExtra("ENABLE", false)
                    handleSelectionMode(enable)
                }
                // Target-app window bounds, reported by IdeazAccessibilityService.
                "com.hereliesaz.ideaz.TARGET_WINDOW_BOUNDS" -> {
                    targetBounds = if (Build.VERSION.SDK_INT >= 33) {
                        intent.getParcelableExtra("BOUNDS", Rect::class.java)
                    } else {
                        @Suppress("DEPRECATION") intent.getParcelableExtra("BOUNDS")
                    }
                    if (isOverlayAdded) applyOverlayGeometry()
                }
                // Command to draw a highlight rectangle (e.g., after selecting an element)
                "com.hereliesaz.ideaz.HIGHLIGHT_RECT" -> {
                    val rect = if (Build.VERSION.SDK_INT >= 33) {
                         intent.getParcelableExtra("RECT", Rect::class.java)
                    } else {
                         @Suppress("DEPRECATION")
                         intent.getParcelableExtra("RECT")
                    }
                    if (rect != null) {
                        overlayView?.updateHighlight(rect)
                    } else {
                        overlayView?.clearHighlight()
                    }
                }
                // Command to raise/drop the bridge touch-block overlay.
                "com.hereliesaz.ideaz.BRIDGE_BLOCK" -> {
                    handleBlockMode(intent.getBooleanExtra("ENABLE", false))
                }
                // Command to show the update status popup
                "com.hereliesaz.ideaz.SHOW_UPDATE_POPUP" -> {
                    val prompt = intent.getStringExtra("PROMPT")
                    if (!prompt.isNullOrBlank()) {
                        copyToClipboard(prompt)
                    }
                    overlayView?.showUpdateSplash()
                }
            }
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // Handle immediate toggle via intent extras
        if (intent != null) {
            if (intent.hasExtra("ENABLE")) {
                 val enable = intent.getBooleanExtra("ENABLE", false)
                 handleSelectionMode(enable)
            }
            if (intent.hasExtra("BRIDGE_BLOCK")) {
                handleBlockMode(intent.getBooleanExtra("BRIDGE_BLOCK", false))
            }
        }
        return START_STICKY
    }

    override fun onCreate() {
        super.onCreate()
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        createNotificationChannel()

        // Create notification required for Foreground Service
        val notification = createNotification()

        // Start Foreground immediately to avoid ANR/Crash
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            try {
                 if (Build.VERSION.SDK_INT >= 34) {
                     // Android 14 requires specifying the service type
                     startForeground(SERVICE_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
                 } else {
                     startForeground(SERVICE_ID, notification)
                 }
            } catch (e: Exception) {
                // Fallback
                startForeground(SERVICE_ID, notification)
            }
        } else {
            startForeground(SERVICE_ID, notification)
        }

        // Add the overlay view if permission is granted
        if (Settings.canDrawOverlays(this)) {
            setupOverlay()
        }

        val filter = IntentFilter().apply {
            addAction("com.hereliesaz.ideaz.TOGGLE_SELECT_MODE")
            addAction("com.hereliesaz.ideaz.HIGHLIGHT_RECT")
            addAction("com.hereliesaz.ideaz.SHOW_UPDATE_POPUP")
            addAction("com.hereliesaz.ideaz.TARGET_WINDOW_BOUNDS")
            addAction("com.hereliesaz.ideaz.BRIDGE_BLOCK")
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(receiver, filter, RECEIVER_NOT_EXPORTED)
        } else {
            registerReceiver(receiver, filter)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        removeBlockView()
        if (isOverlayAdded && overlayView != null) {
            try {
                windowManager.removeView(overlayView)
            } catch (e: Exception) {
                // Ignore
            }
        }
        try {
            unregisterReceiver(receiver)
        } catch (e: Exception) {
            // Ignore
        }
    }

    private fun handleSelectionMode(enable: Boolean) {
        selectMode = enable
        if (overlayView == null && Settings.canDrawOverlays(this)) {
            setupOverlay()
        }
        overlayView?.setSelectionMode(enable)
        if (enable) {
            // Ask the accessibility service for the current target-app window
            // bounds so we can constrain interception to it before the user drags.
            sendBroadcast(
                Intent("com.hereliesaz.ideaz.REQUEST_TARGET_BOUNDS").apply {
                    setPackage(packageName)
                }
            )
        }
        applyOverlayGeometry()
    }

    /**
     * Raises or drops the full-screen touch-block used while the Gemini bridge
     * runs. When dropped, if nothing else needs the overlay the service stops
     * itself so it doesn't linger as a foreground service.
     */
    private fun handleBlockMode(enable: Boolean) {
        blockMode = enable
        if (enable) {
            if (Settings.canDrawOverlays(this)) addBlockView()
        } else {
            removeBlockView()
            if (!selectMode) stopSelf()
        }
    }

    /**
     * Adds a full-screen, touchable dark scrim with a "please wait" message.
     * Touchable (no FLAG_NOT_TOUCHABLE) so it absorbs every tap; the dark
     * background makes it obvious the screen is locked while Gemini works.
     */
    private fun addBlockView() {
        if (blockView != null) return
        val container = android.widget.FrameLayout(this).apply {
            setBackgroundColor(0xCC000000.toInt()) // ~80% black scrim
        }
        val label = android.widget.TextView(this).apply {
            text = "Please wait, don't interrupt."
            setTextColor(0xFFFFFFFF.toInt())
            textSize = 20f
            gravity = Gravity.CENTER
        }
        container.addView(
            label,
            android.widget.FrameLayout.LayoutParams(
                android.widget.FrameLayout.LayoutParams.WRAP_CONTENT,
                android.widget.FrameLayout.LayoutParams.WRAP_CONTENT,
                Gravity.CENTER,
            )
        )
        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        )
        try {
            windowManager.addView(container, params)
            blockView = container
        } catch (e: Exception) {
            android.util.Log.w("IdeazOverlay", "Block scrim add failed", e)
        }
    }

    private fun removeBlockView() {
        val v = blockView ?: return
        try {
            windowManager.removeView(v)
        } catch (e: Exception) {
            // Ignore — may already be detached.
        }
        blockView = null
    }

    /**
     * Initializes the OverlayView and adds it to the WindowManager.
     */
    private fun setupOverlay() {
        if (isOverlayAdded) return
        overlayView = OverlayView(this)

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY, // Alert Window
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or // Initially pass-through
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                    WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        )
        try {
            windowManager.addView(overlayView, params)
            isOverlayAdded = true
        } catch (e: Exception) {
            android.util.Log.w("IdeazOverlay", "Overlay service operation failed", e)
        }
    }

    /**
     * Positions and sizes the overlay window, and toggles touch interception.
     *
     * The overlay only ever intercepts touches when **both** selection mode is on
     * **and** a target-app window is known — and then only over that window's
     * exact bounds. In every other case it is a full-screen, pass-through layer
     * (visual highlights draw, but touches reach whatever is underneath). This is
     * what keeps drag-to-select from blocking taps over IDEaz, other apps, or the
     * system bars.
     */
    private fun applyOverlayGeometry() {
        if (!isOverlayAdded || overlayView == null) {
            if (selectMode && Settings.canDrawOverlays(this)) setupOverlay() else return
        }
        val params = overlayView?.layoutParams as? WindowManager.LayoutParams ?: return
        val bounds = targetBounds

        params.gravity = Gravity.TOP or Gravity.START
        if (selectMode && bounds != null && !bounds.isEmpty) {
            // Constrain the touchable window to the target app's window only.
            params.x = bounds.left
            params.y = bounds.top
            params.width = bounds.width()
            params.height = bounds.height()
            params.flags = params.flags and
                WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE.inv() and
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS.inv()
        } else {
            // Full-screen but pass-through: never intercept globally.
            params.x = 0
            params.y = 0
            params.width = WindowManager.LayoutParams.MATCH_PARENT
            params.height = WindowManager.LayoutParams.MATCH_PARENT
            params.flags = params.flags or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
        }
        try {
            windowManager.updateViewLayout(overlayView, params)
        } catch (e: Exception) {
            android.util.Log.w("IdeazOverlay", "Overlay geometry update failed", e)
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Overlay Service",
                NotificationManager.IMPORTANCE_LOW // Low importance to avoid sound/popups
            )
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }
    }

    private fun createNotification(): Notification {
        val intent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this, 0, intent, PendingIntent.FLAG_IMMUTABLE
        )

        val icon = android.R.drawable.ic_menu_view

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("IDEaz Overlay")
            .setContentText("Overlay is active")
            .setSmallIcon(icon)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .build()
    }

    private fun copyToClipboard(text: String) {
        try {
            val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            val clip = ClipData.newPlainText("Copied Prompt", text)
            clipboard.setPrimaryClip(clip)
        } catch (e: Exception) {
            android.util.Log.w("IdeazOverlay", "Overlay service operation failed", e)
        }
    }

    companion object {
        private const val CHANNEL_ID = "ideaz_overlay_channel"
        private const val SERVICE_ID = 1001
    }
}
