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
import android.util.Log
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
import com.hereliesaz.ideaz.ui.MainViewModelFactory
import com.hereliesaz.ideaz.ui.SettingsViewModel
import com.hereliesaz.ideaz.ui.theme.IDEazTheme
import androidx.preference.PreferenceManager

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
            Log.d(TAG, "onReceive: Received broadcast with action: ${intent?.action}")
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
                        Log.d(TAG, "Calling onNodePromptSubmitted from broadcast")
                        viewModel.onNodePromptSubmitted(resourceId, prompt, bounds)
                    } else {
                        Log.w(TAG, "Received PROMPT_SUBMITTED_NODE but some data was null")
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
                        Log.d(TAG, "Calling onRectPromptSubmitted from broadcast")
                        viewModel.onRectPromptSubmitted(rect, prompt)
                    } else {
                        Log.w(TAG, "Received PROMPT_SUBMITTED_RECT but some data was null")
                    }
                }

                "com.hereliesaz.ideaz.CANCEL_TASK_REQUESTED" -> {
                    Log.d(TAG, "Calling requestCancelTask from broadcast")
                    viewModel.requestCancelTask()
                }

                // NEW: Listen for screenshot
                "com.hereliesaz.ideaz.SCREENSHOT_TAKEN" -> {
                    val base64 = intent.getStringExtra("BASE64_SCREENSHOT")
                    if (base64 != null) {
                        Log.d(TAG, "Calling onScreenshotTaken from broadcast")
                        viewModel.onScreenshotTaken(base64)
                    } else {
                        Log.w(TAG, "Received SCREENSHOT_TAKEN but base64 was null")
                    }
                }
            }
        }
    }
    // --- End Broadcast Receiver ---

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Log.d(TAG, "onCreate: Start")
        Log.d(TAG, "onCreate: MainViewModel hash: ${viewModel.hashCode()}")
        mediaProjectionManager = getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager

        enableEdgeToEdge()
        setContent {
            var isDarkMode by remember { mutableStateOf(viewModel.settingsViewModel.isDarkMode()) }
            IDEazTheme(darkTheme = isDarkMode) {
                MainScreen(
                    viewModel = viewModel,
                    onRequestScreenCapture = {
                        // Launch the permission dialog
                        mediaProjectionManager?.createScreenCaptureIntent()
                            ?.let { screenCaptureLauncher.launch(it) }
                    },
                    onThemeToggle = { isDarkMode = it }
                )
            }
        }
        Log.d(TAG, "onCreate: End")
    }

    override fun onStart() {
        super.onStart()
        Log.d(TAG, "onStart: Start")

        // Load the API key and provide it to the interceptor
        val sharedPreferences = PreferenceManager.getDefaultSharedPreferences(this)
        AuthInterceptor.apiKey = sharedPreferences.getString("api_key", null)
        Log.d(TAG, "onStart: API key loaded")

        viewModel.listSessions()
        Log.d(TAG, "onStart: listSessions() called")

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
        Log.d(TAG, "onStart: BroadcastReceiver registered")
        Log.d(TAG, "onStart: End")
    }

    override fun onStop() {
        super.onStop()
        Log.d(TAG, "onStop: Start")
        unregisterReceiver(inspectionReceiver) // Unregister
        Log.d(TAG, "onStop: End")
    }
}