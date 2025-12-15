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
import androidx.activity.compose.LocalOnBackPressedDispatcherOwner
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
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
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.lifecycle.setViewTreeViewModelStoreOwner
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.rememberNavController
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
import com.hereliesaz.ideaz.ui.SettingsViewModel
import com.hereliesaz.ideaz.ui.theme.IDEazTheme
import com.hereliesaz.ideaz.utils.ComposeLifecycleHelper
import com.hereliesaz.ideaz.utils.ManualBackPressedDispatcherOwner

class IdeazOverlayService : Service(), ViewModelStoreOwner {

    private val windowManager by lazy { getSystemService(WindowManager::class.java) }

    // Two separate views for "L-shaped" non-blocking layout
    private var railView: ComposeView? = null
    private var consoleView: ComposeView? = null

    private var lifecycleHelperRail: ComposeLifecycleHelper? = null
    private var lifecycleHelperConsole: ComposeLifecycleHelper? = null

    private val store = ViewModelStore()

    private lateinit var railParams: WindowManager.LayoutParams
    private lateinit var consoleParams: WindowManager.LayoutParams

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
        // --- 1. Setup Rail View (Left, Vertical) ---
        railView = ComposeView(this).apply {
            setViewTreeViewModelStoreOwner(this@IdeazOverlayService)
            setContent { RailContent() }
        }
        lifecycleHelperRail = ComposeLifecycleHelper(railView!!)
        lifecycleHelperRail?.onCreate()
        lifecycleHelperRail?.onStart()

        railParams = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE, // Always pass touches through
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
        }
        windowManager.addView(railView, railParams)

        // --- 2. Setup Console View (Bottom, Horizontal) ---
        consoleView = ComposeView(this).apply {
            setViewTreeViewModelStoreOwner(this@IdeazOverlayService)
            setContent { ConsoleContent() }
        }
        lifecycleHelperConsole = ComposeLifecycleHelper(consoleView!!)
        lifecycleHelperConsole?.onCreate()
        lifecycleHelperConsole?.onStart()

        consoleParams = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.WRAP_CONTENT, // Starts small (Peek)
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.BOTTOM
        }
        windowManager.addView(consoleView, consoleParams)
    }

    private fun updateConsoleLayout(isExpandedMode: Boolean, peekHeightPx: Int) {
        if (consoleView == null) return

        if (isExpandedMode) {
            // Full Screen Mode (Chat or Expanded Sheet)
            consoleParams.height = WindowManager.LayoutParams.MATCH_PARENT
            consoleParams.gravity = Gravity.TOP // Fill from top
            // Allow focus for typing
            consoleParams.flags = WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                    WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH
        } else {
            // Docked Mode (Sheet Peeking/Hidden)
            // Explicitly set height to the peek height so touches above it pass to the app
            consoleParams.height = peekHeightPx
            consoleParams.gravity = Gravity.BOTTOM
            consoleParams.flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
        }

        try {
            windowManager.updateViewLayout(consoleView, consoleParams)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    // --- COMPOSABLE: RAIL ---
    @Composable
    fun RailContent() {
        val context = LocalContext.current
        val lifecycleOwner = LocalLifecycleOwner.current
        val onBackPressedDispatcherOwner = remember { ManualBackPressedDispatcherOwner(lifecycleOwner.lifecycle) }

        CompositionLocalProvider(LocalOnBackPressedDispatcherOwner provides onBackPressedDispatcherOwner) {
            val navController = rememberNavController()
            val scope = rememberCoroutineScope()

            // We need a dummy sheet state here just for the rail API,
            // but the actual sheet is in ConsoleContent.
            // We use a separate state here that mimics the real one's minimal requirements.
            val dummySheetState = rememberBottomSheetState(
                initialDetent = AlmostHidden,
                detents = remember { listOf(AlmostHidden, Peek, Halfway) }
            )

            val app = applicationContext as Application
            val settingsViewModel = remember { SettingsViewModel(app) }
            val viewModel: MainViewModel = viewModel(factory = MainViewModelFactory(app, settingsViewModel))
            val isSelectMode by viewModel.isSelectMode.collectAsState()

            IDEazTheme {
                // Rail is its own root
                IdeNavRail(
                    navController = navController,
                    viewModel = viewModel,
                    context = context,
                    onShowPromptPopup = { },
                    handleActionClick = { action -> action() },
                    isIdeVisible = true,
                    onLaunchOverlay = { viewModel.toggleSelectMode(!isSelectMode) },
                    sheetState = dummySheetState,
                    scope = scope,
                    initiallyExpanded = false,
                    onUndock = { },
                    enableRailDraggingOverride = true,
                    isLocalBuildEnabled = false,
                    onNavigateToMainApp = { route ->
                        val intent = context.packageManager.getLaunchIntentForPackage(context.packageName)
                        intent?.putExtra("route", route)
                        intent?.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        context.startActivity(intent)
                    }
                )
            }
        }
    }

    // --- COMPOSABLE: CONSOLE + CHAT ---
    @Composable
    fun ConsoleContent() {
        val lifecycleOwner = LocalLifecycleOwner.current
        val onBackPressedDispatcherOwner = remember { ManualBackPressedDispatcherOwner(lifecycleOwner.lifecycle) }

        CompositionLocalProvider(LocalOnBackPressedDispatcherOwner provides onBackPressedDispatcherOwner) {
            val config = LocalConfiguration.current
            val density = LocalContext.current.resources.displayMetrics.density
            val screenHeightDp = config.screenHeightDp.dp
            val screenHeightPx = (config.screenHeightDp * density).toInt()

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
            val targetPackage by settingsViewModel.targetPackageName.collectAsState()
            val context = LocalContext.current

            // --- AUTO-LAUNCH LOGIC ---
            LaunchedEffect(Unit) {
                if (!targetPackage.isNullOrBlank() && targetPackage != context.packageName) {
                    try {
                        val launchIntent = context.packageManager.getLaunchIntentForPackage(targetPackage!!)
                        if (launchIntent != null) {
                            launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                            context.startActivity(launchIntent)
                        }
                    } catch (e: Exception) {
                        Toast.makeText(context, "Error launching project", Toast.LENGTH_SHORT).show()
                    }
                }
            }

            // --- WINDOW SIZING LOGIC ---
            // Calculate the physical height needed for the current detent
            // Peek is 0.2f, AlmostHidden is 0.01f (or minimal).
            // We use a slightly larger buffer for "AlmostHidden" to ensure the handle is touchable.
            val currentDetent = sheetState.currentDetent
            val isSheetOpen = currentDetent == Peek || currentDetent == Halfway
            val isExpandedMode = isContextualChatVisible || currentDetent == Halfway // Full screen if Halfway or Chat

            // Calculate Pixel Height for Docked Mode
            val dockedHeightPx = if (currentDetent == Peek) {
                (screenHeightPx * 0.25f).toInt() // Give a bit more than 0.2 for safety
            } else {
                // AlmostHidden: Needs enough height for the "handle" or tip of the sheet
                (50 * density).toInt()
            }

            LaunchedEffect(isExpandedMode, currentDetent) {
                updateConsoleLayout(isExpandedMode, dockedHeightPx)
            }

            IDEazTheme {
                Box(modifier = Modifier.fillMaxSize()) {
                    // 1. Contextual Chat Overlay (High Z-Index)
                    if (isContextualChatVisible && activeSelectionRect != null) {
                        ContextualChatOverlay(
                            rect = activeSelectionRect!!,
                            viewModel = viewModel,
                            onClose = { viewModel.closeContextualChat() }
                        )
                    }

                    // 2. Bottom Sheet
                    IdeBottomSheet(
                        sheetState = sheetState,
                        viewModel = viewModel,
                        peekDetent = Peek,
                        halfwayDetent = Halfway,
                        screenHeight = screenHeightDp,
                        onSendPrompt = { viewModel.sendPrompt(it) }
                    )
                }
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        lifecycleHelperRail?.onDestroy()
        lifecycleHelperConsole?.onDestroy()
        if (railView != null) windowManager.removeView(railView)
        if (consoleView != null) windowManager.removeView(consoleView)
        store.clear()
    }

    companion object {
        private const val NOTIFICATION_ID = 2002
        private const val CHANNEL_ID = "ideaz_overlay_channel"
    }
}
