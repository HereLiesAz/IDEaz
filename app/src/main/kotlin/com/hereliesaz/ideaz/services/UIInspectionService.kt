package com.hereliesaz.ideaz.services

import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.PixelFormat
import android.graphics.Rect
import android.os.Build
import android.os.IBinder
import android.util.Log
import android.view.Gravity
import android.view.WindowManager
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

class UIInspectionService : Service() {

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

    companion object {
        const val ACTION_START_INSPECTION = "com.hereliesaz.ideaz.action.START_INSPECTION"
        const val ACTION_STOP_INSPECTION = "com.hereliesaz.ideaz.action.STOP_INSPECTION"
    }

    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "UIInspectionService Created")
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager

        val settingsViewModel = SettingsViewModel(application)
        val factory = MainViewModelFactory(application, settingsViewModel)
        viewModel = ViewModelProvider(ViewModelStore(), factory)[MainViewModel::class.java]

        setupWindows()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d(TAG, "onStartCommand: ${intent?.action}")

        // Ensure we are a foreground service with a standard low-profile notification
        startForeground(1001, createNotification())

        when (intent?.action) {
            ACTION_START_INSPECTION -> {
                // Avoid calling toggleSelectMode directly to prevent loops if we are already in sync
                // but we need to ensure local view model reflects the intent.
                // Since MainViewModel prevents the loop, this is safe.
                viewModel.toggleSelectMode(true)
            }
            ACTION_STOP_INSPECTION -> {
                viewModel.toggleSelectMode(false)
                stopForeground(true)
                stopSelf()
            }
        }
        return START_NOT_STICKY
    }

    private fun createNotification(): android.app.Notification {
        val channelId = "ideaz_overlay_service"
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = android.app.NotificationChannel(
                channelId,
                "IDEaz Overlay",
                android.app.NotificationManager.IMPORTANCE_MIN
            )
            val manager = getSystemService(Context.NOTIFICATION_SERVICE) as android.app.NotificationManager
            manager.createNotificationChannel(channel)
        }

        return androidx.core.app.NotificationCompat.Builder(this, channelId)
            .setContentTitle("IDEaz Overlay Active")
            .setContentText("Tap to return to app")
            .setSmallIcon(com.hereliesaz.ideaz.R.mipmap.ic_launcher)
            .setPriority(androidx.core.app.NotificationCompat.PRIORITY_MIN)
            .build()
    }

    override fun onBind(intent: Intent?): IBinder? {
        return null
    }

    private fun setupWindows() {
        if (selectionView != null) return

        setupSelectionLayer()
        setupRailLayer()
        setupSheetLayer()
    }

    private fun setupSelectionLayer() {
        selectionView = ComposeView(this)
        selectionLifecycle = ComposeLifecycleHelper(selectionView!!)
        selectionLifecycle?.onCreate()
        selectionLifecycle?.onStart()

        selectionView?.setContent {
            val isSelectMode by viewModel.isSelectMode.collectAsState()
            val selectionRect by viewModel.activeSelectionRect.collectAsState()

            OverlayCanvas(
                isSelectMode = isSelectMode,
                selectionRect = selectionRect,
                onTap = { _, _ -> /* Tapping does nothing. User must drag to select. */ },
                onDragSelection = { rect ->
                    viewModel.onSelectionMade(rect, "custom_selection")
                }
            )

            LaunchedEffect(isSelectMode) {
                updateSelectionWindowFlags(isSelectMode)
            }
        }

        selectionParams = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY, // Standard Overlay
            WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                    WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        )
        selectionParams?.gravity = Gravity.TOP or Gravity.START

        try {
            windowManager?.addView(selectionView, selectionParams)
        } catch (e: Exception) { Log.e(TAG, "Error adding selection view", e) }
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
                    onLaunchOverlay = {
                        val newMode = !viewModel.isSelectMode.value
                        viewModel.toggleSelectMode(newMode)
                    },
                    sheetState = sheetState,
                    scope = scope,
                    onUndock = { },
                    isBubbleMode = true,
                    enableRailDraggingOverride = false,
                    isLocalBuildEnabled = viewModel.settingsViewModel.isLocalBuildEnabled()
                )
            }
        }

        railParams = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY, // Standard Overlay
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        )
        railParams?.gravity = Gravity.TOP or Gravity.START

        try {
            windowManager?.addView(railView, railParams)
        } catch (e: Exception) { Log.e(TAG, "Error adding rail view", e) }
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

        val initialHeight = (screenHeightPixels * 0.25).toInt()

        sheetParams = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            initialHeight,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY, // Standard Overlay
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        )
        sheetParams?.gravity = Gravity.BOTTOM

        try {
            windowManager?.addView(sheetView, sheetParams)
        } catch (e: Exception) { Log.e(TAG, "Error adding sheet view", e) }
    }

    private fun updateSheetWindowHeight(detent: SheetDetent, screenHeight: Int) {
        sheetParams?.let { params ->
            val newHeight = when(detent.identifier) {
                "peek" -> (screenHeight * 0.25).toInt()
                "halfway" -> (screenHeight * 0.55).toInt()
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

        if (selectionView != null) try { windowManager?.removeView(selectionView) } catch(e:Exception){ Log.w(TAG, "Failed to remove selectionView", e) }
        if (railView != null) try { windowManager?.removeView(railView) } catch(e:Exception){ Log.w(TAG, "Failed to remove railView", e) }
        if (sheetView != null) try { windowManager?.removeView(sheetView) } catch(e:Exception){ Log.w(TAG, "Failed to remove sheetView", e) }
    }
}
