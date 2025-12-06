package com.hereliesaz.ideaz.services

import android.accessibilityservice.AccessibilityService
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.PixelFormat
import android.graphics.Rect
import android.os.Build
import android.util.Log
import android.view.Gravity
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import androidx.compose.runtime.*
import androidx.compose.ui.platform.ComposeView
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.hereliesaz.ideaz.R
import com.hereliesaz.ideaz.ui.inspection.OverlayCanvas
import com.hereliesaz.ideaz.utils.ComposeLifecycleHelper

class UIInspectionService : AccessibilityService() {

    private val TAG = "UIInspectionService"
    private var windowManager: WindowManager? = null

    private var selectionView: ComposeView? = null
    private var selectionParams: WindowManager.LayoutParams? = null
    private var selectionLifecycle: ComposeLifecycleHelper? = null

    private val _isSelectMode = mutableStateOf(false)
    private val _selectionRect = mutableStateOf<Rect?>(null)

    private val commandReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            when (intent?.action) {
                "com.hereliesaz.ideaz.TOGGLE_SELECT_MODE" -> {
                    val enable = intent.getBooleanExtra("ENABLE", false)
                    Log.d(TAG, "Command: TOGGLE_SELECT_MODE = $enable")
                    _isSelectMode.value = enable
                }
                "com.hereliesaz.ideaz.HIGHLIGHT_RECT" -> {
                    val rect = if (Build.VERSION.SDK_INT >= 33) {
                        intent.getParcelableExtra("RECT", Rect::class.java)
                    } else {
                        @Suppress("DEPRECATION")
                        intent.getParcelableExtra("RECT")
                    }
                    Log.d(TAG, "Command: HIGHLIGHT_RECT = $rect")
                    _selectionRect.value = rect
                }
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        registerBroadcastReceivers()
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val notification = NotificationCompat.Builder(this, "ideaz_inspection_channel")
            .setContentTitle("IDEaz Overlay")
            .setContentText("Ready to inspect")
            .setSmallIcon(R.mipmap.ic_launcher)
            .setPriority(NotificationCompat.PRIORITY_MIN)
            .build()

        try {
            startForeground(2001, notification)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start foreground", e)
        }

        return super.onStartCommand(intent, flags, startId)
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                "ideaz_inspection_channel",
                "Inspection Service",
                NotificationManager.IMPORTANCE_LOW
            )
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        Log.d(TAG, "Accessibility Service Connected")
        setupSelectionLayer()
    }

    private fun setupSelectionLayer() {
        if (selectionView != null) return

        selectionView = ComposeView(this)
        selectionLifecycle = ComposeLifecycleHelper(selectionView!!)
        selectionLifecycle?.onCreate()
        selectionLifecycle?.onStart()

        selectionView?.setContent {
            val isSelectMode by _isSelectMode
            val selectionRect by _selectionRect

            OverlayCanvas(
                isSelectMode = isSelectMode,
                selectionRect = selectionRect,
                onTap = { _, _ -> },
                onDragSelection = { rect ->
                    Log.d(TAG, "Reporting Selection: $rect")
                    val intent = Intent("com.hereliesaz.ideaz.SELECTION_MADE").apply {
                        putExtra("RECT", rect)
                        setPackage(packageName)
                    }
                    sendBroadcast(intent)
                    _isSelectMode.value = false
                }
            )

            LaunchedEffect(isSelectMode) {
                updateSelectionWindowFlags(isSelectMode)
            }
        }

        selectionParams = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                    WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        )
        selectionParams?.gravity = Gravity.TOP or Gravity.START

        windowManager?.addView(selectionView, selectionParams)
    }

    private fun updateSelectionWindowFlags(isSelectMode: Boolean) {
        selectionParams?.let { params ->
            if (isSelectMode) {
                params.flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
            } else {
                params.flags = WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                        WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
            }
            try {
                windowManager?.updateViewLayout(selectionView, params)
            } catch (e: Exception) {
                Log.e(TAG, "Error updating selection flags", e)
            }
        }
    }

    private fun registerBroadcastReceivers() {
        val filter = IntentFilter().apply {
            addAction("com.hereliesaz.ideaz.TOGGLE_SELECT_MODE")
            addAction("com.hereliesaz.ideaz.HIGHLIGHT_RECT")
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(commandReceiver, filter, Context.RECEIVER_EXPORTED)
        } else {
            ContextCompat.registerReceiver(this, commandReceiver, filter, ContextCompat.RECEIVER_EXPORTED)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        selectionLifecycle?.onDestroy()
        if (selectionView != null) {
            try { windowManager?.removeView(selectionView) } catch (e: Exception) {}
        }
        try { unregisterReceiver(commandReceiver) } catch (e: Exception) {}
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {}
    override fun onInterrupt() {}
}