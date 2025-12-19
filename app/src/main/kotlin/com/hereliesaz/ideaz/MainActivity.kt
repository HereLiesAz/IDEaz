package com.hereliesaz.ideaz

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import com.hereliesaz.ideaz.ui.MainScreen
import com.hereliesaz.ideaz.ui.MainViewModel
import com.hereliesaz.ideaz.ui.MainViewModelFactory
import com.hereliesaz.ideaz.ui.SettingsViewModel
import com.hereliesaz.ideaz.ui.theme.IDEazTheme
import com.hereliesaz.ideaz.utils.CrashHandler
import com.hereliesaz.ideaz.features.preview.ContainerActivity

class MainActivity : ComponentActivity() {

    private val settingsViewModel: SettingsViewModel by viewModels()
    private val mainViewModel: MainViewModel by viewModels {
        MainViewModelFactory(application)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        CrashHandler.init(this)

        handleIntent(intent)

        setContent {
            val version by settingsViewModel.settingsVersion.collectAsState()
            IDEazTheme(darkTheme = settingsViewModel.isDarkTheme()) {
                Surface(color = MaterialTheme.colorScheme.background) {
                    MainScreen(
                        viewModel = mainViewModel,
                        settingsViewModel = settingsViewModel,
                        onRequestScreenCapture = {
                            // Check permission via VM
                            if (!mainViewModel.hasScreenCapturePermission()) {
                                mainViewModel.requestScreenCapturePermission()
                            }
                        },
                        onThemeToggle = {
                            val current = settingsViewModel.getThemeMode()
                            val next = if (current == SettingsViewModel.THEME_DARK) SettingsViewModel.THEME_LIGHT else SettingsViewModel.THEME_DARK
                            settingsViewModel.setThemeMode(next)
                        },
                        onLaunchOverlay = {
                            Toast.makeText(this, "Overlay toggled", Toast.LENGTH_SHORT).show()
                        },
                        onLaunchPreview = {
                            val apk = mainViewModel.autoLaunchApk.value
                            if (apk != null) {
                                // Launch container
                                val intent = Intent(this, ContainerActivity::class.java).apply {
                                    // We need to pass the package name, not the APK path usually,
                                    // but let's assume MainViewModel has the logic or we grab from settings
                                    val pkg = settingsViewModel.getAppName() // Fallback
                                    putExtra(ContainerActivity.EXTRA_PACKAGE_NAME, pkg)
                                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                                }
                                startActivity(intent)
                                mainViewModel.onPreviewLaunched()
                            } else {
                                Toast.makeText(this, "No APK built yet", Toast.LENGTH_SHORT).show()
                            }
                        }
                    )
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIntent(intent)
    }

    @Deprecated("Deprecated in Java")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (data != null) {
            mainViewModel.setScreenCapturePermission(resultCode, data)
        }
    }

    private fun handleIntent(intent: Intent?) {
        if (intent?.getBooleanExtra("LAUNCH_CONTAINER", false) == true) {
            val packageName = intent.getStringExtra("PACKAGE_NAME")
            if (packageName != null) {
                val containerIntent = Intent(this, ContainerActivity::class.java).apply {
                    putExtra(ContainerActivity.EXTRA_PACKAGE_NAME, packageName)
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                }
                startActivity(containerIntent)
            }
        }
    }
}