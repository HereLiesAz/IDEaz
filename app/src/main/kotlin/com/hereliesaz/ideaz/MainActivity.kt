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

        // Register for Screen Capture Permission (Modern Approach)
        screenCaptureLauncher = registerForActivityResult(
            ActivityResultContracts.StartActivityForResult()
        ) { result ->
            if (result.resultCode == Activity.RESULT_OK && result.data != null) {
                viewModel.setScreenCapturePermission(result.resultCode, result.data)
            }
        }

        // Handle initial deep link or intent
        handleIntent(intent)

        setContent {
            IDEazTheme {
                MainScreen(
                    viewModel = viewModel,
                    settingsViewModel = viewModel.settingsViewModel,
                    onRequestScreenCapture = {
                        val mediaProjectionManager = getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
                        screenCaptureLauncher.launch(mediaProjectionManager.createScreenCaptureIntent())
                    },
                    onThemeToggle = { isDark ->
                        // Matches SettingsViewModel.setThemeMode(String)
                        viewModel.settingsViewModel.setThemeMode(if (isDark) "dark" else "light")
                    },
                    onLaunchOverlay = {
                        val intent = Intent(this, IdeazOverlayService::class.java)
                        startService(intent)
                    },
                    // Passing the container launcher to the UI
                    onLaunchPreview = { apkPath ->
                        launchAppPreview(apkPath)
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

    // Legacy support if specific requests still use onActivityResult directly
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        // 1001 is often used for MediaProjection in older examples/libraries
        if (requestCode == 1001 && resultCode == Activity.RESULT_OK && data != null) {
            val app = application as MainApplication
            app.mainViewModel.setScreenCapturePermission(resultCode, data)
        }
    }

    /**
     * Triggers the "contained" app preview.
     * Launches ContainerActivity with the path to the built APK.
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