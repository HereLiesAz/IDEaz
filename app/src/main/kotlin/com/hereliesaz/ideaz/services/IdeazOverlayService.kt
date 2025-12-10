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
import androidx.compose.ui.platform.LocalContext
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
import com.hereliesaz.ideaz.ui.ContextualChatOverlay
import com.hereliesaz.ideaz.ui.IdeNavRail
import com.hereliesaz.ideaz.ui.MainViewModel
import com.hereliesaz.ideaz.ui.MainViewModelFactory
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

        layoutParams = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
        }

        windowManager.addView(overlayView, layoutParams)
    }

    private fun updateWindowLayout(isChatVisible: Boolean) {
        if (overlayView == null) return

        if (isChatVisible) {
            // Expand to full screen to allow chat bubble rendering anywhere
            layoutParams.width = WindowManager.LayoutParams.MATCH_PARENT
            layoutParams.height = WindowManager.LayoutParams.MATCH_PARENT
            // Use FLAG_NOT_TOUCH_MODAL to allow outside touches to pass through if needed,
            // but for chat input we typically want focus.
            // We remove FLAG_NOT_FOCUSABLE to allow keyboard input.
            layoutParams.flags = WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL
        } else {
            // Collapse back to just the rail
            layoutParams.width = WindowManager.LayoutParams.WRAP_CONTENT
            layoutParams.height = WindowManager.LayoutParams.WRAP_CONTENT
            // Add FLAG_NOT_FOCUSABLE back so the overlay doesn't steal input from the underlying app
            layoutParams.flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
        }

        try {
            windowManager.updateViewLayout(overlayView, layoutParams)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    @Composable
    fun OverlayContent() {
        val context = LocalContext.current
        val navController = rememberNavController()
        val sheetState = rememberBottomSheetState(initialDetent = SheetDetent.FullyExpanded)
        val scope = rememberCoroutineScope()

        val app = applicationContext as Application
        val settingsViewModel = remember { SettingsViewModel(app) }
        val viewModel: MainViewModel = viewModel(factory = MainViewModelFactory(app, settingsViewModel))

        val isContextualChatVisible by viewModel.isContextualChatVisible.collectAsState()
        val activeSelectionRect by viewModel.activeSelectionRect.collectAsState()

        // --- AUTO-LAUNCH LOGIC ---
        val targetPackage by settingsViewModel.targetPackageName.collectAsState()

        LaunchedEffect(Unit) {
            if (!targetPackage.isNullOrBlank()) {
                try {
                    val launchIntent = context.packageManager.getLaunchIntentForPackage(targetPackage!!)
                    if (launchIntent != null) {
                        launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        context.startActivity(launchIntent)
                    } else {
                        Toast.makeText(context, "App not installed: $targetPackage", Toast.LENGTH_LONG).show()
                    }
                } catch (e: Exception) {
                    Toast.makeText(context, "Failed to launch app: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
        }
        // -------------------------

        // Reactively update window size based on chat visibility
        LaunchedEffect(isContextualChatVisible) {
            updateWindowLayout(isContextualChatVisible)
        }

        IDEazTheme {
            Box(modifier = Modifier.fillMaxSize()) {
                // 1. Navigation Rail (Always visible, docked)
                IdeNavRail(
                    navController = navController,
                    viewModel = viewModel,
                    context = context,
                    onShowPromptPopup = { },
                    handleActionClick = { action -> action() },
                    isIdeVisible = true,
                    onLaunchOverlay = { },
                    sheetState = sheetState,
                    scope = scope,
                    initiallyExpanded = false,
                    onUndock = { stopSelf() },
                    enableRailDraggingOverride = true,
                    isLocalBuildEnabled = false,
                    onNavigateToMainApp = { route ->
                        val intent = context.packageManager.getLaunchIntentForPackage(context.packageName)
                        intent?.putExtra("route", route)
                        intent?.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        context.startActivity(intent)
                        stopSelf()
                    }
                )

                // 2. Contextual Chat (Visible only when active)
                if (isContextualChatVisible && activeSelectionRect != null) {
                    ContextualChatOverlay(
                        rect = activeSelectionRect!!,
                        viewModel = viewModel,
                        onClose = { viewModel.closeContextualChat() }
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