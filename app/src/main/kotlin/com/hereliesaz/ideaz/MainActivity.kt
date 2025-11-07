package com.hereliesaz.ideaz

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

        viewModel.bindService(this)
        viewModel.listSessions()
    }

    override fun onStop() {
        super.onStop()
        viewModel.unbindService(this)
    }
}