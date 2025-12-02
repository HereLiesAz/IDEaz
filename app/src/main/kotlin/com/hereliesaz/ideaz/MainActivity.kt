package com.hereliesaz.ideaz

import android.app.Activity
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat
import androidx.preference.PreferenceManager
import com.hereliesaz.ideaz.api.AuthInterceptor
import com.hereliesaz.ideaz.ui.MainScreen
import com.hereliesaz.ideaz.ui.MainViewModel
import com.hereliesaz.ideaz.ui.MainViewModelFactory
import com.hereliesaz.ideaz.ui.SettingsViewModel
import com.hereliesaz.ideaz.ui.theme.IDEazTheme

class MainActivity : ComponentActivity() {

    private val viewModel: MainViewModel by viewModels {
        MainViewModelFactory(
            application,
            SettingsViewModel(application)
        )
    }
    private var mediaProjectionManager: MediaProjectionManager? = null

    companion object {
        private const val TAG = "MainActivity"
    }

    // Receiver to Auto-Launch App after Install
    private val packageInstallReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            val action = intent?.action
            if (action == Intent.ACTION_PACKAGE_ADDED || action == Intent.ACTION_PACKAGE_REPLACED) {
                val data = intent.data
                val installedPackageName = data?.encodedSchemeSpecificPart
                val targetPackage = viewModel.settingsViewModel.getTargetPackageName()

                if (installedPackageName != null && installedPackageName == targetPackage) {
                    Log.d(TAG, "Target package $installedPackageName installed. Launching...")
                    try {
                        val launchIntent = packageManager.getLaunchIntentForPackage(installedPackageName)
                        if (launchIntent != null) {
                            launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                            startActivity(launchIntent)
                            Toast.makeText(context, "Launching $installedPackageName...", Toast.LENGTH_SHORT).show()
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "Failed to auto-launch app", e)
                    }
                }
            }
        }
    }

    private val screenCaptureLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == Activity.RESULT_OK) {
                result.data?.let {
                    viewModel.setScreenCapturePermission(result.resultCode, it)
                }
            } else {
                viewModel.setScreenCapturePermission(Activity.RESULT_CANCELED, null)
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Log.d(TAG, "onCreate: Start")

        viewModel.bindBuildService(this)
        viewModel.loadLastProject(this)

        mediaProjectionManager = getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager

        enableEdgeToEdge()
        setContent {
            var trigger by remember { mutableStateOf(true) }

            val useDarkTheme = when (viewModel.settingsViewModel.getThemeMode()) {
                SettingsViewModel.THEME_LIGHT -> false
                SettingsViewModel.THEME_DARK -> true
                SettingsViewModel.THEME_SYSTEM -> isSystemInDarkTheme()
                SettingsViewModel.THEME_AUTO -> isSystemInDarkTheme()
                else -> isSystemInDarkTheme()
            }

            IDEazTheme(darkTheme = useDarkTheme) {
                MainScreen(
                    viewModel = viewModel,
                    onRequestScreenCapture = {
                        mediaProjectionManager?.createScreenCaptureIntent()
                            ?.let { screenCaptureLauncher.launch(it) }
                    },
                    onThemeToggle = { trigger = !trigger },
                    onLaunchOverlay = {
                        // Trigger the Service to show the Bubble Notification
                        // Note: The Accessibility Service must be enabled in settings for this to work fully.
                        val intent = Intent("com.hereliesaz.ideaz.START_INSPECTION")
                        intent.setPackage(packageName)
                        sendBroadcast(intent)

                        // Minimize Dashboard
                        moveTaskToBack(true)
                    }
                )
            }
        }
    }

    override fun onStart() {
        super.onStart()
        val sharedPreferences = PreferenceManager.getDefaultSharedPreferences(this)
        AuthInterceptor.apiKey = sharedPreferences.getString("api_key", null)

        val packageFilter = IntentFilter().apply {
            addAction(Intent.ACTION_PACKAGE_ADDED)
            addAction(Intent.ACTION_PACKAGE_REPLACED)
            addDataScheme("package")
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(packageInstallReceiver, packageFilter, RECEIVER_NOT_EXPORTED)
        } else {
            registerReceiver(packageInstallReceiver, packageFilter)
        }
    }

    override fun onStop() {
        super.onStop()
        try {
            unregisterReceiver(packageInstallReceiver)
        } catch (e: Exception) {}
    }

    override fun onDestroy() {
        super.onDestroy()
    }
}