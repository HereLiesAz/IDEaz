package com.hereliesaz.ideaz.services

import android.app.Service
import android.content.Intent
import android.graphics.PixelFormat
import android.os.IBinder
import android.view.Gravity
import android.view.WindowManager
import androidx.compose.ui.platform.ComposeView
import androidx.lifecycle.*
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import com.hereliesaz.ideaz.MainApplication
import com.hereliesaz.ideaz.ui.ContextualChatOverlay
import com.hereliesaz.ideaz.ui.theme.IDEazTheme

class IdeazOverlayService : Service(), ViewModelStoreOwner, SavedStateRegistryOwner {

    private val windowManager by lazy { getSystemService(WINDOW_SERVICE) as WindowManager }

    // Explicit override to satisfy interface
    override val viewModelStore = ViewModelStore()

    private val savedStateRegistryController = SavedStateRegistryController.create(this)
    private val lifecycleRegistry = LifecycleRegistry(this)

    override fun onCreate() {
        super.onCreate()
        savedStateRegistryController.performRestore(null)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_CREATE)

        val app = application as MainApplication
        val mainViewModel = app.mainViewModel

        val composeView = ComposeView(this).apply {
            setViewTreeLifecycleOwner(this@IdeazOverlayService)
            setViewTreeViewModelStoreOwner(this@IdeazOverlayService)
            setViewTreeSavedStateRegistryOwner(this@IdeazOverlayService)

            setContent {
                IDEazTheme {
                    val rectState = mainViewModel.activeSelectionRect.value
                    // Fix: Handle nullable Rect by providing a default
                    val safeRect = rectState ?: android.graphics.Rect(0,0,0,0)

                    ContextualChatOverlay(
                        rect = safeRect,
                        viewModel = mainViewModel,
                        onClose = { stopSelf() }
                    )
                }
            }
        }

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        )
        params.gravity = Gravity.TOP or Gravity.START

        windowManager.addView(composeView, params)
    }

    override fun onDestroy() {
        super.onDestroy()
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_DESTROY)
        viewModelStore.clear()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override val lifecycle: Lifecycle get() = lifecycleRegistry
    override val savedStateRegistry: SavedStateRegistry get() = savedStateRegistryController.savedStateRegistry
}