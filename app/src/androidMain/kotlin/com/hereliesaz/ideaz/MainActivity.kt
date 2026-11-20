package com.hereliesaz.ideaz

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.enableEdgeToEdge
import androidx.activity.compose.setContent
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.hereliesaz.ideaz.ui.MainScreen
import com.hereliesaz.ideaz.ui.SettingsViewModel
import com.hereliesaz.ideaz.ui.theme.IDEazTheme

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        // Draw under the system bars, then hide them: the IDE owns the whole
        // display. AzNavRail's bottom sheet assumes an edge-to-edge window and
        // extends to the screen bottom itself; without this it floated above a
        // dead strip where the nav bar used to be. Bars reappear transiently on
        // an edge swipe.
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        hideSystemBars()

        handleIntent(intent)

        val app = application as MainApplication

        setContent {
            val themeMode by app.mainViewModel.settingsViewModel.themeMode.collectAsState()
            val systemDark = isSystemInDarkTheme()
            val darkTheme = when (themeMode) {
                SettingsViewModel.THEME_DARK -> true
                SettingsViewModel.THEME_LIGHT -> false
                else -> systemDark
            }

            IDEazTheme(darkTheme = darkTheme) {
                MainScreen(viewModel = app.mainViewModel)
            }
        }
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        // Dialogs, the IME and returning from another app can restore the bars.
        if (hasFocus) hideSystemBars()
    }

    private fun hideSystemBars() {
        WindowCompat.getInsetsController(window, window.decorView).apply {
            systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            hide(WindowInsetsCompat.Type.systemBars())
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIntent(intent)
    }

    private fun handleIntent(intent: Intent?) {
        // The activity is exported, so any app can send this extra. Only a fixed
        // set of top-level screens may be opened this way; in particular never
        // file_content/{path}, which would show an arbitrary file.
        intent?.getStringExtra("route")?.takeIf { isAllowedIntentRoute(it) }?.let { route ->
            val app = application as MainApplication
            app.mainViewModel.setPendingRoute(route)
        }
    }
}

/** Routes an external intent may open through the exported [MainActivity]. */
private val INTENT_ROUTES = setOf("settings", "project_settings", "file_explorer", "git")

internal fun isAllowedIntentRoute(route: String): Boolean = route in INTENT_ROUTES
