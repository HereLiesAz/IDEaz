package com.hereliesaz.ideaz

import android.content.Context
import android.content.Intent
import android.media.projection.MediaProjectionManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import com.hereliesaz.ideaz.ui.MainScreen
import com.hereliesaz.ideaz.ui.SettingsViewModel
import com.hereliesaz.ideaz.ui.theme.IDEazTheme

class MainActivity : ComponentActivity() {

    private val screenCaptureLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        val app = application as MainApplication
        app.mainViewModel.setScreenCapturePermission(result.resultCode, result.data)
        app.mainViewModel.screenCaptureRequestHandled()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        handleIntent(intent)

        val app = application as MainApplication
        app.mainViewModel.bindBuildService(this)

        setContent {
            val themeMode by app.mainViewModel.settingsViewModel.themeMode.collectAsState()
            val systemDark = isSystemInDarkTheme()
            val darkTheme = when (themeMode) {
                SettingsViewModel.THEME_DARK -> true
                SettingsViewModel.THEME_LIGHT -> false
                else -> systemDark
            }

            // Bridge OverlayDelegate.requestScreenCapture (StateFlow) → MediaProjection
            // permission intent. The launcher result feeds back into the delegate so
            // ScreenshotService can use it.
            val pendingScreenCapture by app.mainViewModel.requestScreenCapture.collectAsState()
            LaunchedEffect(pendingScreenCapture) {
                if (pendingScreenCapture) {
                    val mpm = getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
                    screenCaptureLauncher.launch(mpm.createScreenCaptureIntent())
                }
            }

            IDEazTheme(darkTheme = darkTheme) {
                MainScreen(
                    viewModel = app.mainViewModel,
                    onRequestScreenCapture = { app.mainViewModel.requestScreenCapturePermission() },
                    onThemeToggle = { /* themeMode StateFlow drives root recomposition */ }
                )
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        if (isFinishing) {
             val app = application as MainApplication
             app.mainViewModel.unbindBuildService(this)
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIntent(intent)
    }

    private fun handleIntent(intent: Intent?) {
        intent?.getStringExtra("route")?.let { route ->
            val app = application as MainApplication
            app.mainViewModel.setPendingRoute(route)
        }
    }
}
