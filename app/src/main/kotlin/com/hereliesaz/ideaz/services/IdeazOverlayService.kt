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
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import androidx.core.app.NotificationCompat
import androidx.lifecycle.*
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.rememberNavController
import com.composables.core.rememberBottomSheetState
import com.hereliesaz.ideaz.R
import com.hereliesaz.ideaz.ui.*
import com.hereliesaz.ideaz.ui.theme.IDEazTheme
import com.hereliesaz.ideaz.ui.web.WebProjectHost
import com.hereliesaz.ideaz.utils.ComposeLifecycleHelper
import kotlinx.coroutines.launch

class IdeazOverlayService : Service(), ViewModelStoreOwner {

    private val windowManager by lazy { getSystemService(WindowManager::class.java) }
    private var overlayView: ComposeView? = null
    private var lifecycleHelper: ComposeLifecycleHelper? = null
    private val store = ViewModelStore()
    private lateinit var layoutParams: WindowManager.LayoutParams

    private var lastX = 0
    private var lastY = 0
    private var wasExpanded = false

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
        if (intent?.action == "STOP_SERVICE") {
            stopSelf()
            return START_NOT_STICKY
        }
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
        val stopIntent = Intent(this, IdeazOverlayService::class.java).apply {
            action = "STOP_SERVICE"
        }
        val stopPendingIntent = android.app.PendingIntent.getService(
            this, 0, stopIntent, android.app.PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("IDEaz Overlay")
            .setContentText("Tap to open navigation")
            .setSmallIcon(R.mipmap.ic_launcher)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .addAction(android.R.drawable.ic_menu_close_clear_cancel, "Stop Overlay", stopPendingIntent)
            .build()
    }

    private fun updateNotification(messages: List<String>) {
        val stopIntent = Intent(this, IdeazOverlayService::class.java).apply {
            action = "STOP_SERVICE"
        }
        val stopPendingIntent = android.app.PendingIntent.getService(
            this, 0, stopIntent, android.app.PendingIntent.FLAG_IMMUTABLE
        )

        val collapsed = messages.takeLast(3).joinToString("\n")
        val expanded = messages.takeLast(10).joinToString("\n")

        val contentText = if (collapsed.isBlank()) "IDEaz Active" else collapsed

        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("IDEaz")
            .setContentText(contentText)
            .setStyle(NotificationCompat.BigTextStyle().bigText(expanded))
            .setSmallIcon(R.mipmap.ic_launcher)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .addAction(android.R.drawable.ic_menu_close_clear_cancel, "Stop Overlay", stopPendingIntent)
            .build()

        val manager = getSystemService(NotificationManager::class.java)
        manager.notify(NOTIFICATION_ID, notification)
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

        // Initial Layout
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

    private fun updateWindowLayout(shouldBeFullscreen: Boolean) {
        if (overlayView == null) return

        if (shouldBeFullscreen) {
            if (!wasExpanded) {
                lastX = layoutParams.x
                lastY = layoutParams.y
                wasExpanded = true
            }
            layoutParams.x = 0
            layoutParams.y = 0
            layoutParams.width = WindowManager.LayoutParams.MATCH_PARENT
            layoutParams.height = WindowManager.LayoutParams.MATCH_PARENT

            layoutParams.flags = WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                    WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH
        } else {
            if (wasExpanded) {
                layoutParams.x = lastX
                layoutParams.y = lastY
                wasExpanded = false
            }
            layoutParams.width = WindowManager.LayoutParams.WRAP_CONTENT
            layoutParams.height = WindowManager.LayoutParams.WRAP_CONTENT
            layoutParams.flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
        }

        try {
            windowManager.updateViewLayout(overlayView, layoutParams)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun updatePosition(x: Float, y: Float) {
        val view = overlayView ?: return
        val metrics = windowManager.currentWindowMetrics
        val bounds = metrics.bounds
        val screenWidth = bounds.width()
        val screenHeight = bounds.height()
        val viewWidth = if (view.width > 0) view.width else 0
        val viewHeight = if (view.height > 0) view.height else 0

        val newX = layoutParams.x + x.toInt()
        val newY = layoutParams.y + y.toInt()
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

        val supportedDetents = remember { listOf(AlmostHidden, Peek, Halfway) }
        val sheetState = rememberBottomSheetState(
            detents = supportedDetents,
            initialDetent = AlmostHidden
        )

        val app = applicationContext as Application
        val settingsViewModel = remember { SettingsViewModel(app) }
        val viewModel: MainViewModel = viewModel(factory = MainViewModelFactory(app, settingsViewModel))

        val isTargetAppVisible by viewModel.isTargetAppVisible.collectAsState()
        val currentWebUrl by viewModel.currentWebUrl.collectAsState()
        val isContextualChatVisible by viewModel.isContextualChatVisible.collectAsState()
        val activeSelectionRect by viewModel.activeSelectionRect.collectAsState()
        val isSelectMode by viewModel.isSelectMode.collectAsState()
        val pendingRoute by viewModel.pendingRoute.collectAsState()
        val logMessages by viewModel.filteredLog.collectAsState(initial = emptyList())

        var isPromptPopupVisible by remember { mutableStateOf(false) }

        // Navigation Handling
        LaunchedEffect(pendingRoute) {
            pendingRoute?.let { route ->
                navController.navigate(route) {
                    launchSingleTop = true
                    restoreState = true
                }
                viewModel.setPendingRoute(null)
            }
        }

        // Notification Updates
        LaunchedEffect(logMessages) {
            updateNotification(logMessages)
        }

        // Auto-Launch Target Logic
        val targetPackage by settingsViewModel.targetPackageName.collectAsState()
        LaunchedEffect(Unit) {
            if (!targetPackage.isNullOrBlank() && targetPackage != context.packageName) {
                try {
                    val launchIntent = context.packageManager.getLaunchIntentForPackage(targetPackage!!)
                    if (launchIntent != null) {
                        launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        context.startActivity(launchIntent)
                    }
                } catch (e: Exception) { /* ignore */ }
            }
            navController.navigate("project_settings")
        }

        // Window Layout Logic
        val isSheetOpen = sheetState.currentDetent == Peek || sheetState.currentDetent == Halfway
        val isWebMode = currentWebUrl != null && isTargetAppVisible
        val shouldBeFullscreen = !isTargetAppVisible || isWebMode || isSelectMode || isContextualChatVisible || isSheetOpen || isPromptPopupVisible

        // Requirement #7 Logic: Title visible only in Select/Interact, disabled in Overlay/Build/Popup
        // isTargetAppVisible covers Interact. isSelectMode covers Select.
        // !isPromptPopupVisible && !isSheetOpen (Build/Logs) ensures it's hidden when those are active.
        val isTitleVisible = (isTargetAppVisible || isSelectMode) && !isPromptPopupVisible && !isSheetOpen

        LaunchedEffect(shouldBeFullscreen) {
            updateWindowLayout(shouldBeFullscreen)
        }

        IDEazTheme {
            val backgroundColor = if (!isTargetAppVisible) MaterialTheme.colorScheme.background else Color.Transparent

            Box(modifier = Modifier
                .fillMaxSize()
                .background(backgroundColor)
            ) {

                // LAYER 0: Web View (if Web Mode)
                if (isWebMode) {
                    currentWebUrl?.let { url ->
                        Box(modifier = Modifier.fillMaxSize().zIndex(0f)) {
                            WebProjectHost(url = url, modifier = Modifier.fillMaxSize())
                        }
                    }
                }

                // LAYER 1: IDE Content (Settings, Project)
                if (!isTargetAppVisible) {
                    Box(modifier = Modifier
                        .fillMaxSize()
                        .padding(start = 80.dp)
                        .zIndex(1f)
                    ) {
                        IdeNavHost(
                            modifier = Modifier.fillMaxSize(),
                            navController = navController,
                            viewModel = viewModel,
                            settingsViewModel = settingsViewModel,
                            onThemeToggle = { /* handled by state */ }
                        )
                    }
                }

                // LAYER 2: Rail
                Box(modifier = Modifier.zIndex(100f)) {
                    IdeNavRail(
                        navController = navController,
                        viewModel = viewModel,
                        context = context,
                        onShowPromptPopup = { isPromptPopupVisible = true },
                        handleActionClick = { it() },
                        isIdeVisible = isTargetAppVisible,
                        isTitleVisible = isTitleVisible,
                        onLaunchOverlay = { viewModel.toggleSelectMode(!isSelectMode) },
                        sheetState = sheetState,
                        scope = scope,
                        initiallyExpanded = false,
                        onUndock = { },
                        onOverlayDrag = { x, y -> updatePosition(x, y) },
                        enableRailDraggingOverride = !shouldBeFullscreen,
                        isLocalBuildEnabled = settingsViewModel.isLocalBuildEnabled(),
                        onNavigateToMainApp = { route ->
                            viewModel.clearSelection()
                            if (currentWebUrl != null) {
                                viewModel.stateDelegate.setTargetAppVisible(false)
                            }
                            if (isTargetAppVisible) {
                                viewModel.stateDelegate.setTargetAppVisible(false)
                            }
                            navController.navigate(route) {
                                launchSingleTop = true
                                restoreState = true
                            }
                        }
                    )
                }

                // LAYER 3: Selection Overlay
                if (isSelectMode) {
                    Box(modifier = Modifier.fillMaxSize().zIndex(200f)) {
                        SelectionOverlay(
                            modifier = Modifier.fillMaxSize(),
                            onTap = { x, y -> },
                            onDragEnd = { rect ->
                                viewModel.overlayDelegate.onSelectionMade(android.graphics.Rect(rect.left, rect.top, rect.right, rect.bottom))
                            }
                        )
                    }
                }

                // LAYER 4: Contextual Chat
                if (isContextualChatVisible && activeSelectionRect != null) {
                    Box(modifier = Modifier.fillMaxSize().zIndex(300f)) {
                        ContextualChatOverlay(
                            rect = activeSelectionRect!!,
                            viewModel = viewModel,
                            onClose = { viewModel.closeContextualChat() }
                        )
                    }
                }

                // LAYER 5: Bottom Sheet
                IdeBottomSheet(
                    sheetState = sheetState,
                    viewModel = viewModel,
                    peekDetent = Peek,
                    halfwayDetent = Halfway,
                    screenHeight = screenHeight,
                    onSendPrompt = { viewModel.sendPrompt(it) }
                )

                // LAYER 6: Prompt Popup
                if (isPromptPopupVisible) {
                     PromptPopup(
                        onDismiss = { isPromptPopupVisible = false },
                        onSubmit = { prompt ->
                            viewModel.sendPrompt(prompt)
                            isPromptPopupVisible = false
                        }
                    )
                }
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
