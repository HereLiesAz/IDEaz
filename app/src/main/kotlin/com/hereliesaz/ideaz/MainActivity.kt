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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
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

        screenCaptureLauncher = registerForActivityResult(
            ActivityResultContracts.StartActivityForResult()
        ) { result ->
            if (result.resultCode == Activity.RESULT_OK && result.data != null) {
                viewModel.setScreenCapturePermission(result.resultCode, result.data!!)
            }
        }

        setContent {
            IDEazTheme {
                val autoLaunchApk by viewModel.autoLaunchApk.collectAsState()

                LaunchedEffect(autoLaunchApk) {
                    autoLaunchApk?.let {
                        launchAppPreview(it)
                        viewModel.onPreviewLaunched()
                    }
                }

                MainScreen(
                    viewModel = viewModel,
                    settingsViewModel = viewModel.settingsViewModel, // Explicitly passed
                    onRequestScreenCapture = {
                        val mediaProjectionManager = getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
                        screenCaptureLauncher.launch(mediaProjectionManager.createScreenCaptureIntent())
                    },
                    onThemeToggle = { isDark ->
                        viewModel.settingsViewModel.setThemeMode(if (isDark) "dark" else "light")
                    },
                    onLaunchOverlay = {
                        val intent = Intent(this, IdeazOverlayService::class.java)
                        startService(intent)
                    },
                    onLaunchPreview = { apkPath ->
                        launchAppPreview(apkPath)
                    }
                )
            }
        }
    }

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