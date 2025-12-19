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
import com.hereliesaz.ideaz.services.IdeazOverlayService
import androidx.core.content.ContextCompat

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Handle route from intent if any
        handleIntent(intent)

        if (!Settings.canDrawOverlays(this)) {
            setContent {
                IDEazTheme {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text("Please grant overlay permission.")
                    }
                }
            }
            Toast.makeText(this, "Please grant overlay permission to use IDEaz", Toast.LENGTH_LONG).show()
            val intent = Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName"))
            startActivityForResult(intent, 1001)
        } else {
            // Render the Main UI
            val app = application as MainApplication
            setContent {
                IDEazTheme {
                    MainScreen(
                        viewModel = app.mainViewModel,
                        onRequestScreenCapture = { /* TODO */ },
                        onThemeToggle = { /* recreate()? Or handled by state */ },
                        onLaunchOverlay = { launchOverlay() }
                    )
                }
            }
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

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == 1001) {
            if (Settings.canDrawOverlays(this)) {
                // Restart to load UI
                recreate()
            } else {
                Toast.makeText(this, "Permission denied. IDEaz cannot run.", Toast.LENGTH_SHORT).show()
                finish()
            }
        }
    }

    private fun launchOverlay() {
        val intent = Intent(this, IdeazOverlayService::class.java)
        ContextCompat.startForegroundService(this, intent)
    }
}
