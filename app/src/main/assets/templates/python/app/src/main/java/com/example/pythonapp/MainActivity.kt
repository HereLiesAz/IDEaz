package com.example.pythonapp

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier

class MainActivity : ComponentActivity() {

    private val viewModel: MainViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Start the Python Service
        val serviceIntent = Intent(this, PythonService::class.java)
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            startForegroundService(serviceIntent)
        } else {
            startService(serviceIntent)
        }

        setContent {
            MaterialTheme {
                Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    val uiState by viewModel.uiState.collectAsState()
                    val error by viewModel.error.collectAsState()

                    if (uiState != null) {
                        DynamicUiRenderer(component = uiState!!, onAction = { viewModel.sendAction(it) })
                    } else {
                        Box(contentAlignment = Alignment.Center) {
                            if (error != null) {
                                Text("Waiting for Python... ($error)")
                            } else {
                                CircularProgressIndicator()
                            }
                        }
                    }
                }
            }
        }
    }
}
