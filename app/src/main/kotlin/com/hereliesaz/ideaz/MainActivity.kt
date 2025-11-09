package com.hereliesaz.ideaz

import android.app.Activity
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Rect
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.hereliesaz.ideaz.api.AuthInterceptor
import com.hereliesaz.ideaz.ui.MainScreen
import com.hereliesaz.ideaz.ui.MainViewModel
import com.hereliesaz.ideaz.ui.SettingsViewModel
import com.hereliesaz.ideaz.ui.theme.IDEazTheme
import androidx.preference.PreferenceManager

class MainActivity : ComponentActivity() {

    private val viewModel: MainViewModel by viewModels()
    private val settingsViewModel: SettingsViewModel by viewModels()
    private var mediaProjectionManager: MediaProjectionManager? = null

    // --- NEW: ActivityResultLauncher for MediaProjection ---
    private val screenCaptureLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == Activity.RESULT_OK) {
                // Permission granted. Pass the data to the ViewModel.
                // FIX: Check for null on result.data before passing
                result.data?.let {
                    viewModel.setScreenCapturePermission(result.resultCode, it)
                }
            } else {
                // Permission denied.
                viewModel.setScreenCapturePermission(Activity.RESULT_CANCELED, null)
            }
        }
    // --- END NEW ---

    // --- Broadcast Receiver for events from UIInspectionService ---
    private val inspectionReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            when (intent?.action) {

                "com.hereliesaz.ideaz.PROMPT_SUBMITTED_NODE" -> {
                    val resourceId = intent.getStringExtra("RESOURCE_ID")
                    val prompt = intent.getStringExtra("PROMPT")
                    // NEW: Pass bounds rect for screenshot
                    val bounds = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        intent.getParcelableExtra("BOUNDS", Rect::class.java)
                    } else {
                        @Suppress("DEPRECATION")
                        intent.getParcelableExtra("BOUNDS")
                    }
                    if (resourceId != null && prompt != null && bounds != null) {
                        viewModel.onNodePromptSubmitted(resourceId, prompt, bounds)
                    }
                }

                "com.hereliesaz.ideaz.PROMPT_SUBMITTED_RECT" -> {
                    val rect = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        intent.getParcelableExtra("RECT", Rect::class.java)
                    } else {
                        @Suppress("DEPRECATION")
                        intent.getParcelableExtra("RECT")
                    }
                    val prompt = intent.getStringExtra("PROMPT")

                    if (rect != null && prompt != null) {
                        viewModel.onRectPromptSubmitted(rect, prompt)
                    }
                }

                "com.hereliesaz.ideaz.CANCEL_TASK_REQUESTED" -> {
                    viewModel.requestCancelTask()
                }

                // NEW: Listen for screenshot
                "com.hereliesaz.ideaz.SCREENSHOT_TAKEN" -> {
                    val base64 = intent.getStringExtra("BASE64_SCREENSHOT")
                    if (base64 != null) {
                        viewModel.onScreenshotTaken(base64)
                    }
                }
            }
        }
    }
    // --- End Broadcast Receiver ---

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        mediaProjectionManager = getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager

        enableEdgeToEdge()
        setContent {
            var isDarkMode by remember { mutableStateOf(settingsViewModel.isDarkMode(this)) }
            IDEazTheme(darkTheme = isDarkMode) {
                MainScreen(
                    viewModel = viewModel,
                    onRequestScreenCapture = {
                        // Launch the permission dialog
                        mediaProjectionManager?.createScreenCaptureIntent()
                            ?.let { screenCaptureLauncher.launch(it) }
                    },
                    settingsViewModel = settingsViewModel,
                    onThemeToggle = { isDarkMode = it }
                )
            }
        }
    }

    override fun onStart() {
        super.onStart()

        // Load the API key and provide it to the interceptor
        val sharedPreferences = PreferenceManager.getDefaultSharedPreferences(this)
        AuthInterceptor.apiKey = sharedPreferences.getString("api_key", null)

        viewModel.bindBuildService(this)
        viewModel.listSessions()

        // Register the inspection receiver
        val filter = IntentFilter().apply {
            addAction("com.hereliesaz.ideaz.PROMPT_SUBMITTED_NODE")
            addAction("com.hereliesaz.ideaz.PROMPT_SUBMITTED_RECT")
            addAction("com.hereliesaz.ideaz.CANCEL_TASK_REQUESTED")
            addAction("com.hereliesaz.ideaz.SCREENSHOT_TAKEN") // Add new action
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(inspectionReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            registerReceiver(inspectionReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
        }
    }

    override fun onStop() {
        super.onStop()
        viewModel.unbindBuildService(this)
        unregisterReceiver(inspectionReceiver) // Unregister
    }
}