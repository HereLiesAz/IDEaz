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
import android.content.pm.ServiceInfo
import android.graphics.PixelFormat
import android.graphics.Rect
import android.os.Build
import android.os.IBinder
import android.provider.Settings
import android.view.WindowManager
import androidx.core.app.NotificationCompat
import com.hereliesaz.ideaz.MainActivity
import com.hereliesaz.ideaz.R
import com.hereliesaz.ideaz.ui.inspection.OverlayView

class IdeazOverlayService : Service() {

    private lateinit var windowManager: WindowManager
    private var overlayView: OverlayView? = null
    private var isOverlayAdded = false

    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            when (intent?.action) {
                "com.hereliesaz.ideaz.TOGGLE_SELECT_MODE" -> {
                    val enable = intent.getBooleanExtra("ENABLE", false)
                    handleSelectionMode(enable)
                }
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
            }
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent != null) {
            if (intent.hasExtra("ENABLE")) {
                 val enable = intent.getBooleanExtra("ENABLE", false)
                 handleSelectionMode(enable)
            }
        }
        return START_STICKY
    }

    override fun onCreate() {
        super.onCreate()
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        createNotificationChannel()

        val notification = createNotification()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            try {
                 if (Build.VERSION.SDK_INT >= 34) {
                     startForeground(SERVICE_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
                 } else {
                     startForeground(SERVICE_ID, notification)
                 }
            } catch (e: Exception) {
                startForeground(SERVICE_ID, notification)
            }
        } else {
            startForeground(SERVICE_ID, notification)
        }

        if (Settings.canDrawOverlays(this)) {
            setupOverlay()
        }

        val filter = IntentFilter().apply {
            addAction("com.hereliesaz.ideaz.TOGGLE_SELECT_MODE")
            addAction("com.hereliesaz.ideaz.HIGHLIGHT_RECT")
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(receiver, filter, RECEIVER_NOT_EXPORTED)
        } else {
            registerReceiver(receiver, filter)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
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
        if (overlayView == null && Settings.canDrawOverlays(this)) {
            setupOverlay()
        }
        overlayView?.setSelectionMode(enable)
        updateOverlayParams(enable)
    }

    private fun setupOverlay() {
        if (isOverlayAdded) return
        overlayView = OverlayView(this)
        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                    WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        )
        try {
            windowManager.addView(overlayView, params)
            isOverlayAdded = true
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun updateOverlayParams(isSelectMode: Boolean) {
        if (!isOverlayAdded || overlayView == null) {
            if (isSelectMode && Settings.canDrawOverlays(this)) {
                setupOverlay()
            } else {
                return
            }
        }

        val params = overlayView?.layoutParams as? WindowManager.LayoutParams ?: return
        if (isSelectMode) {
            params.flags = params.flags and WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE.inv()
        } else {
            params.flags = params.flags or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
        }
        try {
            windowManager.updateViewLayout(overlayView, params)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Overlay Service",
                NotificationManager.IMPORTANCE_LOW
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

    companion object {
        private const val CHANNEL_ID = "ideaz_overlay_channel"
        private const val SERVICE_ID = 1001
    }
}
