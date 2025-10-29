package com.hereliesaz.peridiumide.ui

import android.util.Log
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import com.hereliesaz.peridiumide.api.GenerateCodeRequest
import com.hereliesaz.peridiumide.api.RetrofitClient
import com.hereliesaz.peridiumide.utils.ComposableRegistry
import kotlinx.coroutines.launch

@Composable
fun Overlay(modifier: Modifier = Modifier) {
    var showPopup by remember { mutableStateOf(false) }

    val scope = rememberCoroutineScope()

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Transparent)
            .pointerInput(Unit) {
                detectTapGestures { offset ->
                    val tappedComposable = ComposableRegistry.getAll().find {
                        it.bounds.contains(offset)
                    }
                    if (tappedComposable != null) {
                        showPopup = true
                        Log.d("Overlay", "Tapped on: ${tappedComposable.id}")
                    } else {
                        Log.d("Overlay", "Tapped on empty space")
                    }
                }
            }
    ) {
        if (showPopup) {
            PromptPopup(
                onDismiss = { showPopup = false },
                onSubmit = { prompt ->
                    showPopup = false
                    scope.launch {
                        try {
                            val request = GenerateCodeRequest(prompt, "TODO: Add context")
                            val response = RetrofitClient.instance.generateCode(request)
                            Log.d("Overlay", "Generated code: ${response.code}")
                        } catch (e: Exception) {
                            Log.e("Overlay", "Error generating code", e)
                        }
                    }
                }
            )
        }
    }
}
