package com.hereliesaz.ideaz

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.media.projection.MediaProjectionManager
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import com.hereliesaz.ideaz.preview.ContainerActivity
import com.hereliesaz.ideaz.services.IdeazOverlayService
import com.hereliesaz.ideaz.ui.MainScreen
import com.hereliesaz.ideaz.ui.theme.IDEazTheme
import java.io.File

class MainActivity : ComponentActivity() {

    private lateinit var screenCaptureLauncher: ActivityResultLauncher<Intent>

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val app = application as MainApplication
        val viewModel = app.mainViewModel

        // Register for Screen Capture Permission
        screenCaptureLauncher = registerForActivityResult(
            ActivityResultContracts.StartActivityForResult()
        ) { result ->
            if (result.resultCode == Activity.RESULT_OK && result.data != null) {
                viewModel.setScreenCapturePermission(result.resultCode, result.data)
            }
        }

        // Handle initial intent
        handleIntent(intent)

        setContent {
            IDEazTheme {
                MainScreen(
                    viewModel = viewModel,
                    onRequestScreenCapture = {
                        val mediaProjectionManager = getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
                        screenCaptureLauncher.launch(mediaProjectionManager.createScreenCaptureIntent())
                    },
                    onThemeToggle = { isDark ->
                        // Fix: setThemeMode expects "dark" or "light" (String), not Int.
                        viewModel.settingsViewModel.setThemeMode(if (isDark) "dark" else "light")
                    },
                    onLaunchOverlay = {
                        val intent = Intent(this, IdeazOverlayService::class.java)
                        startService(intent)
                    }
                )
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

    /**
     * Triggers the "contained" app preview.
     * Call this after a successful build with the absolute path to the APK.
     */
    fun launchAppPreview(apkPath: String) {
        val file = File(apkPath)
        if (file.exists()) {
            val intent = Intent(this, ContainerActivity::class.java).apply {
                putExtra(ContainerActivity.EXTRA_APK_PATH, apkPath)
            }
            startActivity(intent)
        } else {
            Toast.makeText(this, "APK not found at $apkPath", Toast.LENGTH_SHORT).show()
        }
    }
}