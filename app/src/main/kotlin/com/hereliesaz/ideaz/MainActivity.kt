package com.hereliesaz.ideaz

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.hereliesaz.ideaz.ui.MainScreen
import com.hereliesaz.ideaz.ui.theme.IDEazTheme
import androidx.core.content.ContextCompat
import com.hereliesaz.ideaz.utils.AssetExtractor

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        AssetExtractor.extractStdLib(this)

        // Handle route from intent if any
        handleIntent(intent)

        // Render the Main UI
        val app = application as MainApplication

        // Bind Build Service
        app.mainViewModel.bindBuildService(this)

        setContent {
            IDEazTheme {
                MainScreen(
                    viewModel = app.mainViewModel,
                    onRequestScreenCapture = { /* TODO */ },
                    onThemeToggle = { /* recreate()? Or handled by state */ }
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
