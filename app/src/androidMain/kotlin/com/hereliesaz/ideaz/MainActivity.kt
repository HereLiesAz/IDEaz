package com.hereliesaz.ideaz

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import com.hereliesaz.ideaz.ui.MainScreen
import com.hereliesaz.ideaz.ui.SettingsViewModel
import com.hereliesaz.ideaz.ui.theme.IDEazTheme

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

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
