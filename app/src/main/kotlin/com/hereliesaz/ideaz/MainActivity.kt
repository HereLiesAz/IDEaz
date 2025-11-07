package com.hereliesaz.ideaz

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Rect
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import com.hereliesaz.ideaz.api.AuthInterceptor
import com.hereliesaz.ideaz.ui.MainScreen
import com.hereliesaz.ideaz.ui.MainViewModel
import com.hereliesaz.ideaz.ui.theme.IDEazTheme
import androidx.preference.PreferenceManager

class MainActivity : ComponentActivity() {

    private val viewModel: MainViewModel by viewModels()

    // --- Broadcast Receiver for events from UIInspectionService ---
    private val inspectionReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            when (intent?.action) {

                "com.hereliesaz.ideaz.PROMPT_SUBMITTED_NODE" -> {
                    val resourceId = intent.getStringExtra("RESOURCE_ID")
                    val prompt = intent.getStringExtra("PROMPT")
                    if (resourceId != null && prompt != null) {
                        viewModel.onNodePromptSubmitted(resourceId, prompt)
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
            }
        }
    }
    // --- End Broadcast Receiver ---

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            IDEazTheme {
                MainScreen(viewModel)
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
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(inspectionReceiver, filter, Context.RECEIVER_EXPORTED)
        } else {
            registerReceiver(inspectionReceiver, filter)
        }
    }

    override fun onStop() {
        super.onStop()
        viewModel.unbindBuildService(this)
        unregisterReceiver(inspectionReceiver) // Unregister
    }
}