package com.hereliesaz.ideaz.services

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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalContext
import androidx.core.app.NotificationCompat
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.setViewTreeViewModelStoreOwner
import androidx.navigation.compose.rememberNavController
import com.composables.core.SheetDetent
import com.composables.core.rememberBottomSheetState
import com.hereliesaz.ideaz.R
import com.hereliesaz.ideaz.ui.IdeNavRail
import com.hereliesaz.ideaz.ui.MainViewModel
import com.hereliesaz.ideaz.ui.MainViewModelFactory
import com.hereliesaz.ideaz.ui.SettingsViewModel
import com.hereliesaz.ideaz.ui.theme.IDEazTheme
import com.hereliesaz.ideaz.utils.ComposeLifecycleHelper
import android.app.Application

class IdeazOverlayService : Service(), ViewModelStoreOwner {

    private val windowManager by lazy { getSystemService(WindowManager::class.java) }
    private var overlayView: ComposeView? = null
    private var lifecycleHelper: ComposeLifecycleHelper? = null
    private val store = ViewModelStore()

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

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
        }

        windowManager.addView(overlayView, params)
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

        IDEazTheme {
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
