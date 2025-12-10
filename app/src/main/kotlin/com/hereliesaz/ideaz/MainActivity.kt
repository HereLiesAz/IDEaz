package com.hereliesaz.ideaz

import android.content.Intent
import android.content.res.Configuration
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.core.view.WindowCompat
import androidx.lifecycle.lifecycleScope
import com.hereliesaz.ideaz.ui.MainScreen
import com.hereliesaz.ideaz.ui.MainViewModel
import com.hereliesaz.ideaz.ui.MainViewModelFactory
import com.hereliesaz.ideaz.ui.SettingsViewModel
import com.hereliesaz.ideaz.ui.theme.IDEazTheme
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import com.hereliesaz.ideaz.services.IdeazOverlayService
import androidx.core.content.ContextCompat
import android.provider.Settings
import android.widget.Toast
import android.net.Uri

class MainActivity : ComponentActivity() {

    private val settingsViewModel: SettingsViewModel by viewModels()
    private val viewModel: MainViewModel by viewModels { MainViewModelFactory(application, settingsViewModel) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)

        setContent {
            val themeMode by settingsViewModel.themeMode.collectAsState()
            val useDarkTheme = when (themeMode) {
                SettingsViewModel.THEME_LIGHT -> false
                SettingsViewModel.THEME_DARK -> true
                SettingsViewModel.THEME_SYSTEM -> isSystemInDarkTheme()
                else -> {
                    val nightModeFlags = resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK
                    nightModeFlags == Configuration.UI_MODE_NIGHT_YES
                }
            }

            IDEazTheme(darkTheme = useDarkTheme) {
                MainScreen(
                    viewModel = viewModel,
                    onRequestScreenCapture = { viewModel.requestScreenCapturePermission() },
                    onThemeToggle = { recreate() },
                    onLaunchOverlay = { launchOverlay() }
                )
            }
        }

        lifecycleScope.launch {
            viewModel.requestScreenCapture.collect { shouldRequest ->
                if (shouldRequest) {
                    // Handle screen capture request
                    viewModel.screenCaptureRequestHandled()
                }
            }
        }
    }

    private fun launchOverlay() {
        if (Settings.canDrawOverlays(this)) {
            val intent = Intent(this, IdeazOverlayService::class.java)
            ContextCompat.startForegroundService(this, intent)
        } else {
             Toast.makeText(this, "Please grant overlay permission", Toast.LENGTH_SHORT).show()
             val intent = Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName"))
             startActivity(intent)
        }
    }
}