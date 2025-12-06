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
import android.view.accessibility.AccessibilityNodeInfo
import androidx.compose.runtime.*
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import androidx.navigation.compose.rememberNavController
import com.composables.core.SheetDetent
import com.composables.core.rememberBottomSheetState
import com.hereliesaz.ideaz.ui.Halfway
import com.hereliesaz.ideaz.ui.IdeBottomSheet
import com.hereliesaz.ideaz.ui.IdeNavRail
import com.hereliesaz.ideaz.ui.MainViewModel
import com.hereliesaz.ideaz.ui.MainViewModelFactory
import com.hereliesaz.ideaz.ui.Peek
import com.hereliesaz.ideaz.ui.SettingsViewModel
import com.hereliesaz.ideaz.ui.inspection.OverlayCanvas
import com.hereliesaz.ideaz.ui.theme.IDEazTheme
import com.hereliesaz.ideaz.utils.ComposeLifecycleHelper
import kotlin.math.roundToInt

class UIInspectionService : AccessibilityService() {

    private val TAG = "UIInspectionService"
    private var windowManager: WindowManager? = null

    // Three separate windows to allow "passthrough" touches in the empty spaces
    private var selectionView: ComposeView? = null
    private var railView: ComposeView? = null
    private var sheetView: ComposeView? = null

    private var selectionParams: WindowManager.LayoutParams? = null
    private var railParams: WindowManager.LayoutParams? = null
    private var sheetParams: WindowManager.LayoutParams? = null

    private var selectionLifecycle: ComposeLifecycleHelper? = null
    private var railLifecycle: ComposeLifecycleHelper? = null
    private var sheetLifecycle: ComposeLifecycleHelper? = null

    private lateinit var viewModel: MainViewModel

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
        setupWindows()
    }

    private fun setupWindows() {
        if (selectionView != null) return

        // 1. Setup Selection Layer (Backmost)
        setupSelectionLayer()

        // 2. Setup Rail Layer (Left Strip)
        setupRailLayer()

        // 3. Setup Sheet Layer (Bottom Strip)
        setupSheetLayer()
    }

    private fun setupSelectionLayer() {
        selectionView = ComposeView(this)
        selectionLifecycle = ComposeLifecycleHelper(selectionView!!)
        selectionLifecycle?.onCreate()
        selectionLifecycle?.onStart()

        selectionView?.setContent {
            // We do NOT use IDEazTheme here to avoid drawing backgrounds
            val isSelectMode by viewModel.isSelectMode.collectAsState()
            val selectionRect by viewModel.activeSelectionRect.collectAsState()

            // Pass-through logic handled by updating WindowFlags below
            OverlayCanvas(
                isSelectMode = isSelectMode,
                selectionRect = selectionRect,
                onTap = { _, _ -> /* Tapping does nothing. User must drag to select. */ },
                onDragSelection = { rect ->
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
        // This guarantees we don't block system touches.
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
                // Enable touching (remove NOT_TOUCHABLE)
                params.flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
            } else {
                // Disable touching (Pass-through to app)
                params.flags = WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                        WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
            }
            try {
                windowManager?.updateViewLayout(selectionView, params)
            } catch (e: Exception) { Log.e(TAG, "Error updating selection flags", e) }
        }
    }

    private fun setupRailLayer() {
        railView = ComposeView(this)
        railLifecycle = ComposeLifecycleHelper(railView!!)
        railLifecycle?.onCreate()
        railLifecycle?.onStart()

        railView?.setContent {
            IDEazTheme(darkTheme = true) {
                val navController = rememberNavController()
                val scope = rememberCoroutineScope()
                val sheetState = rememberBottomSheetState(
                    initialDetent = Peek,
                    detents = listOf(Peek, Halfway)
                )

                IdeNavRail(
                    navController = navController,
                    viewModel = viewModel,
                    context = this@UIInspectionService,
                    onShowPromptPopup = {},
                    handleActionClick = { it() },
                    isIdeVisible = true,
                    onLaunchOverlay = { viewModel.toggleSelectMode(!viewModel.isSelectMode.value) },
                    sheetState = sheetState,
                    scope = scope,
                    onUndock = { /* Dragging not fully supported in split-window mode yet */ },
                    isBubbleMode = true,
                    enableRailDraggingOverride = false, // Disable FAB dragging to prevent clipping
                    isLocalBuildEnabled = viewModel.settingsViewModel.isLocalBuildEnabled()
                )
            }
        }

        railParams = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN, // Rail can go behind bars visually
            PixelFormat.TRANSLUCENT
        )
        railParams?.gravity = Gravity.TOP or Gravity.START

        windowManager?.addView(railView, railParams)
    }

    private fun setupSheetLayer() {
        sheetView = ComposeView(this)
        sheetLifecycle = ComposeLifecycleHelper(sheetView!!)
        sheetLifecycle?.onCreate()
        sheetLifecycle?.onStart()

        val screenHeightPixels = resources.displayMetrics.heightPixels
        val density = resources.displayMetrics.density
        val screenHeightDp = (screenHeightPixels / density).toInt()

        sheetView?.setContent {
            IDEazTheme(darkTheme = true) {
                val sheetState = rememberBottomSheetState(
                    initialDetent = Peek,
                    detents = listOf(Peek, Halfway)
                )

                // Monitor detent to resize window
                val currentDetent = sheetState.currentDetent
                LaunchedEffect(currentDetent) {
                    updateSheetWindowHeight(currentDetent, screenHeightPixels)
                }

                IdeBottomSheet(
                    sheetState = sheetState,
                    viewModel = viewModel,
                    peekDetent = Peek,
                    halfwayDetent = Halfway,
                    screenHeight = screenHeightDp.dp,
                    onSendPrompt = { viewModel.sendPrompt(it) }
                )
            }
        }

        // Start with Peek height approximation (20% of screen)
        val initialHeight = (screenHeightPixels * 0.25).toInt()

        sheetParams = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            initialHeight,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE, // Allow touches
            PixelFormat.TRANSLUCENT
        )
        sheetParams?.gravity = Gravity.BOTTOM

        windowManager?.addView(sheetView, sheetParams)
    }

    private fun updateSheetWindowHeight(detent: SheetDetent, screenHeight: Int) {
        sheetParams?.let { params ->
            // Calculate height based on logic in IdeBottomSheet (Peek=0.25, Halfway=0.5)
            // Ideally we'd get the exact px from the composable, but mapping detent ID is cleaner here.
            val newHeight = when(detent.identifier) {
                "peek" -> (screenHeight * 0.25).toInt()
                "halfway" -> (screenHeight * 0.55).toInt() // slightly more to fit shadows/handles
                else -> WindowManager.LayoutParams.WRAP_CONTENT
            }

            if (params.height != newHeight) {
                params.height = newHeight
                try {
                    windowManager?.updateViewLayout(sheetView, params)
                } catch (e: Exception) { Log.e(TAG, "Error resizing sheet", e) }
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        selectionLifecycle?.onDestroy()
        railLifecycle?.onDestroy()
        sheetLifecycle?.onDestroy()

        if (selectionView != null) try { windowManager?.removeView(selectionView) } catch(e:Exception){}
        if (railView != null) try { windowManager?.removeView(railView) } catch(e:Exception){}
        if (sheetView != null) try { windowManager?.removeView(sheetView) } catch(e:Exception){}

        try { unregisterReceiver(commandReceiver) } catch (e: Exception) {}
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {}
    override fun onInterrupt() {}

    private val commandReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) { }
    }

    private fun registerBroadcastReceivers() {
        // Keep for legacy
    }

    // Removed inspectNodeAt and findNodeAt as interaction is now strictly drag-to-select.
}
