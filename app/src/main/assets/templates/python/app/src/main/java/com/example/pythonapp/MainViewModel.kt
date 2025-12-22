package com.example.pythonapp

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

class MainViewModel : ViewModel() {
    private val _uiState = MutableStateFlow<JSONObject?>(null)
    val uiState: StateFlow<JSONObject?> = _uiState.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    init {
        startPolling()
    }

    private fun startPolling() {
        viewModelScope.launch(Dispatchers.IO) {
            while (true) {
                try {
                    val json = fetchUi()
                    _uiState.value = json
                    _error.value = null
                } catch (e: Exception) {
                    _error.value = "Connection error: ${e.message}"
                    Log.e("MainViewModel", "Polling failed", e)
                }
                delay(500) // Poll every 500ms
            }
        }
    }

    fun sendAction(action: String) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val url = URL("http://127.0.0.1:5000/action")
                val conn = url.openConnection() as HttpURLConnection
                conn.requestMethod = "POST"
                conn.doOutput = true
                conn.setRequestProperty("Content-Type", "application/json")

                val payload = JSONObject().put("action", action).toString()
                conn.outputStream.write(payload.toByteArray())

                // Read response (new UI state) directly to avoid wait
                if (conn.responseCode == 200) {
                     val response = conn.inputStream.bufferedReader().use { it.readText() }
                     _uiState.value = JSONObject(response)
                }
            } catch (e: Exception) {
                Log.e("MainViewModel", "Action failed", e)
            }
        }
    }

    private fun fetchUi(): JSONObject {
        val url = URL("http://127.0.0.1:5000/ui")
        val conn = url.openConnection() as HttpURLConnection
        conn.connectTimeout = 1000
        conn.readTimeout = 1000

        val text = conn.inputStream.bufferedReader().use { it.readText() }
        return JSONObject(text)
    }
}
