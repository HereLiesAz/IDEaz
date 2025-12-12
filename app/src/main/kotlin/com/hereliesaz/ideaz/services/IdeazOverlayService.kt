package com.hereliesaz.ideaz.services

import android.app.Application
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.graphics.PixelFormat
import android.os.Build
import android.os.IBinder
import android.view.Gravity
import android.view.WindowManager
import android.widget.Toast
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.app.NotificationCompat
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.lifecycle.setViewTreeViewModelStoreOwner
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.rememberNavController
import com.composables.core.SheetDetent
import com.composables.core.rememberBottomSheetState
import com.hereliesaz.ideaz.R
import com.hereliesaz.ideaz.ui.AlmostHidden
import com.hereliesaz.ideaz.ui.ContextualChatOverlay
import com.hereliesaz.ideaz.ui.Halfway
import com.hereliesaz.ideaz.ui.IdeBottomSheet
import com.hereliesaz.ideaz.ui.IdeNavRail
import com.hereliesaz.ideaz.ui.MainViewModel
import com.hereliesaz.ideaz.ui.MainViewModelFactory
import com.hereliesaz.ideaz.ui.Peek
import com.hereliesaz.ideaz.ui.SelectionOverlay
import com.hereliesaz.ideaz.ui.SettingsViewModel
import com.hereliesaz.ideaz.ui.theme.IDEazTheme
import com.hereliesaz.ideaz.utils.ComposeLifecycleHelper

class IdeazOverlayService : Service(), ViewModelStoreOwner {

    private val windowManager by lazy { getSystemService(WindowManager::class.java) }
    private var overlayView: ComposeView? = null
    private var lifecycleHelper: ComposeLifecycleHelper? = null
    private val store = ViewModelStore()
    private lateinit var layoutParams: WindowManager.LayoutParams

    override fun onBind(intent: Intent?): IBinder? = null

    override val viewModelStore: ViewModelStore
        get() = store

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        startForeground(NOTIFICATION_ID, getNotification())
        setupOverlay()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        return START_STICKY
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "IDEaz Overlay",
                NotificationManager.IMPORTANCE_LOW
            )
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }
    }

    private fun getNotification(): Notification {
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("IDEaz Overlay")
            .setContentText("Tap to open navigation")
            .setSmallIcon(R.mipmap.ic_launcher)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    private fun setupOverlay() {
        overlayView = ComposeView(this).apply {
            setViewTreeViewModelStoreOwner(this@IdeazOverlayService)
            setContent {
                OverlayContent()
            }
        }

        lifecycleHelper = ComposeLifecycleHelper(overlayView!!)
        lifecycleHelper?.onCreate()
        lifecycleHelper?.onStart()

        // Initial Layout: Wrap content (Rail only), Non-Focusable so you can use the target app
        layoutParams = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
        }

        windowManager.addView(overlayView, layoutParams)
    }

    private fun updateWindowLayout(isExpandedMode: Boolean, isSelectMode: Boolean) {
        if (overlayView == null) return

        if (isSelectMode) {
            // Select Mode: Full Screen, consume touches to allow dragging
            layoutParams.width = WindowManager.LayoutParams.MATCH_PARENT
            layoutParams.height = WindowManager.LayoutParams.MATCH_PARENT
            // Ensure we can receive touch events (default behavior without FLAG_NOT_TOUCHABLE)
            // We remove FLAG_NOT_FOCUSABLE so we can potentially capture keys if needed,
            // or just to ensure standard behavior.
            layoutParams.flags = WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                    WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH
        } else if (isExpandedMode) {
            // Expanded Mode (Chat or Console Open):
            // 1. MATCH_PARENT to allow the bottom sheet and chat bubble to render anywhere.
            // 2. FLAG_NOT_TOUCH_MODAL allow touches to status bar/nav bar to pass through.
            // 3. REMOVE FLAG_NOT_FOCUSABLE so the user can type in the chat box.
            layoutParams.width = WindowManager.LayoutParams.MATCH_PARENT
            layoutParams.height = WindowManager.LayoutParams.MATCH_PARENT
            layoutParams.flags = WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                    WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH
        } else {
            // Docked Mode (Rail Only):
            // 1. WRAP_CONTENT to shrink the window to just the rail.
            // 2. FLAG_NOT_FOCUSABLE so all touches outside the rail go to the Target App.
            layoutParams.width = WindowManager.LayoutParams.WRAP_CONTENT
            layoutParams.height = WindowManager.LayoutParams.MATCH_PARENT
            layoutParams.flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
        }

        try {
            windowManager.updateViewLayout(overlayView, layoutParams)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    // Manual dragging support
    private fun updatePosition(x: Float, y: Float) {
        val view = overlayView ?: return

        val metrics = windowManager.currentWindowMetrics
        val bounds = metrics.bounds
        val screenWidth = bounds.width()
        val screenHeight = bounds.height()

        val viewWidth = if (view.width > 0) view.width else 0
        val viewHeight = if (view.height > 0) view.height else 0

        // Calculate new position
        val newX = layoutParams.x + x.toInt()
        val newY = layoutParams.y + y.toInt()

        // Clamp to screen bounds
        val maxX = (screenWidth - viewWidth).coerceAtLeast(0)
        val maxY = (screenHeight - viewHeight).coerceAtLeast(0)

        layoutParams.x = newX.coerceIn(0, maxX)
        layoutParams.y = newY.coerceIn(0, maxY)

        try {
            windowManager.updateViewLayout(view, layoutParams)
        } catch (e: Exception) {
            android.util.Log.e("IdeazOverlayService", "Failed to update overlay position", e)
        }
    }

    @Composable
    fun OverlayContent() {
        val context = LocalContext.current
        val navController = rememberNavController()
        val scope = rememberCoroutineScope()
        val config = LocalConfiguration.current
        val screenHeight = config.screenHeightDp.dp

        // Setup Bottom Sheet State
        val supportedDetents = remember { listOf(AlmostHidden, Peek, Halfway) }
        val sheetState = rememberBottomSheetState(
            detents = supportedDetents,
            initialDetent = AlmostHidden
        )

        val app = applicationContext as Application
        val settingsViewModel = remember { SettingsViewModel(app) }
        val viewModel: MainViewModel = viewModel(factory = MainViewModelFactory(app, settingsViewModel))

        val isContextualChatVisible by viewModel.isContextualChatVisible.collectAsState()
        val activeSelectionRect by viewModel.activeSelectionRect.collectAsState()

        // This is the CRITICAL part: Get the USER'S PROJECT package name
        val targetPackage by settingsViewModel.targetPackageName.collectAsState()
        val isSelectMode by viewModel.isSelectMode.collectAsState()

        // --- AUTO-LAUNCH LOGIC ---
        // Automatically launch the TARGET APP (not IDEaz) when the overlay starts
        LaunchedEffect(Unit) {
            if (!targetPackage.isNullOrBlank()) {
                // If we are "dogfooding" (IDEaz building IDEaz), this is effectively a no-op
                // as we are already foreground. But for any other app, this brings it to front.
                if (targetPackage != context.packageName) {
                    try {
                        val launchIntent = context.packageManager.getLaunchIntentForPackage(targetPackage!!)
                        if (launchIntent != null) {
                            launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                            context.startActivity(launchIntent)
                        } else {
                            Toast.makeText(context, "Project app not installed yet: $targetPackage", Toast.LENGTH_LONG).show()
                        }
                    } catch (e: Exception) {
                        Toast.makeText(context, "Error launching project: ${e.message}", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }

        // --- WINDOW RESIZING LOGIC ---
        val isSheetOpen = sheetState.currentDetent == Peek || sheetState.currentDetent == Halfway
        val isExpandedMode = isContextualChatVisible || isSheetOpen

        LaunchedEffect(isExpandedMode, isSelectMode) {
            updateWindowLayout(isExpandedMode, isSelectMode)
        }

        IDEazTheme {
            Box(modifier = Modifier.fillMaxSize()) {

                // 1. Navigation Rail (Docked)
                IdeNavRail(
                    navController = navController,
                    viewModel = viewModel,
                    context = context,
                    onShowPromptPopup = { },
                    handleActionClick = { action -> action() },
                    isIdeVisible = true, // Force visible in overlay
                    // Wire up the toggle button to actually switch modes
                    onLaunchOverlay = { viewModel.toggleSelectMode(!isSelectMode) },
                    sheetState = sheetState,
                    scope = scope,
                    initiallyExpanded = false,
                    onUndock = { stopSelf() },
                    // NEW: Pass manual drag handler for overlay movement
                    onOverlayDrag = { x, y -> updatePosition(x, y) },
                    enableRailDraggingOverride = true,
                    isLocalBuildEnabled = false,
                    // Navigate BACK to the IDE app for settings/management
                    onNavigateToMainApp = { route ->
                        val intent = context.packageManager.getLaunchIntentForPackage(context.packageName)
                        intent?.putExtra("route", route)
                        intent?.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        context.startActivity(intent)
                        stopSelf()
                    }
                )

                // 2. Selection Overlay (When in Select Mode)
                if (isSelectMode) {
                    SelectionOverlay(
                        modifier = Modifier.fillMaxSize(),
                        onTap = { x, y ->
                            // Optional: Single tap selection
                        },
                        onDragEnd = { rect ->
                            viewModel.overlayDelegate.onSelectionMade(android.graphics.Rect(rect.left, rect.top, rect.right, rect.bottom))
                        }
                    )
                }

                // 3. Contextual Chat Overlay
                if (isContextualChatVisible && activeSelectionRect != null) {
                    ContextualChatOverlay(
                        rect = activeSelectionRect!!,
                        viewModel = viewModel,
                        onClose = { viewModel.closeContextualChat() }
                    )
                }

                // 4. Bottom Sheet (Console)
                IdeBottomSheet(
                    sheetState = sheetState,
                    viewModel = viewModel,
                    peekDetent = Peek,
                    halfwayDetent = Halfway,
                    screenHeight = screenHeight,
                    onSendPrompt = { viewModel.sendPrompt(it) }
                )
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        lifecycleHelper?.onDestroy()
        if (overlayView != null) {
            windowManager.removeView(overlayView)
        }
        store.clear()
    }

    companion object {
        private const val NOTIFICATION_ID = 2002
        private const val CHANNEL_ID = "ideaz_overlay_channel"
    }
}
