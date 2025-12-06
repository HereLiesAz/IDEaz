package com.hereliesaz.ideaz.services

import android.accessibilityservice.AccessibilityService
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
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import com.hereliesaz.ideaz.ui.MainViewModel
import com.hereliesaz.ideaz.ui.MainViewModelFactory
import com.hereliesaz.ideaz.ui.SettingsViewModel
import com.hereliesaz.ideaz.ui.inspection.OverlayCanvas
import com.hereliesaz.ideaz.utils.ComposeLifecycleHelper

class UIInspectionService : AccessibilityService() {

    private val TAG = "UIInspectionService"
    private var windowManager: WindowManager? = null

    // We only need ONE window now: The Selection Overlay.
    // The NavRail and BottomSheet are handled by BubbleActivity (or MainActivity).
    private var selectionView: ComposeView? = null
    private var selectionParams: WindowManager.LayoutParams? = null
    private var selectionLifecycle: ComposeLifecycleHelper? = null

    private lateinit var viewModel: MainViewModel

    // Receiver to sync state from the Main/Bubble Activity
    private val commandReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            when (intent?.action) {
                "com.hereliesaz.ideaz.TOGGLE_SELECT_MODE" -> {
                    val enable = intent.getBooleanExtra("ENABLE", false)
                    Log.d(TAG, "Received TOGGLE_SELECT_MODE: $enable")
                    // Force update the local service VM state to match Activity
                    viewModel.setSelectModeInternal(enable)
                }
                "com.hereliesaz.ideaz.HIGHLIGHT_RECT" -> {
                    // Optional: If we want to show a specific highlight triggered externally
                    // val rect = intent.getParcelableExtra<Rect>("RECT")
                    // We would update a state in VM here if we want to visualize it
                }
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "UIInspectionService Created")
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager

        val settingsViewModel = SettingsViewModel(application)
        val factory = MainViewModelFactory(application, settingsViewModel)
        viewModel = ViewModelProvider(ViewModelStore(), factory)[MainViewModel::class.java]

        registerBroadcastReceivers()
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
            // We do NOT use IDEazTheme here to avoid drawing background colors.
            // This view must remain transparent unless drawing the selection box.
            val isSelectMode by viewModel.isSelectMode.collectAsState()
            val selectionRect by viewModel.activeSelectionRect.collectAsState()

            // Pass-through logic handled by updating WindowFlags below via LaunchedEffect
            OverlayCanvas(
                isSelectMode = isSelectMode,
                selectionRect = selectionRect,
                onTap = { _, _ -> /* Tapping does nothing. User must drag to select. */ },
                onDragSelection = { rect ->
                    Log.d(TAG, "Selection Dragged: $rect")
                    viewModel.onSelectionMade(rect, "custom_selection")
                }
            )

            // Effect to toggle window flags based on mode
            LaunchedEffect(isSelectMode) {
                updateSelectionWindowFlags(isSelectMode)
            }
        }

        // IMPORTANT: We do NOT use FLAG_LAYOUT_IN_SCREEN here.
        // This ensures the window sits *between* the Status Bar and Nav Bar.
        // This guarantees we don't block system touches when passing through.
        selectionParams = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or // Default to pass-through
                    WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        )
        selectionParams?.gravity = Gravity.TOP or Gravity.START

        windowManager?.addView(selectionView, selectionParams)
    }

    private fun updateSelectionWindowFlags(isSelectMode: Boolean) {
        selectionParams?.let { params ->
            if (isSelectMode) {
                Log.d(TAG, "Window Mode: INTERCEPT (Select Mode)")
                // Enable touching (remove NOT_TOUCHABLE)
                // We keep NOT_FOCUSABLE so we don't steal key events (back button, volume)
                params.flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
            } else {
                Log.d(TAG, "Window Mode: PASS-THROUGH (Interact Mode)")
                // Disable touching (Pass-through to app)
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