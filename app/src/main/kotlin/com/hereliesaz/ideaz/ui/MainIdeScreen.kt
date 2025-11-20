// --- NEW FILE ---
// Placeholder for the main IDE screen

package com.hereliesaz.ideaz.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.foundation.background
import androidx.compose.ui.graphics.Color

@Composable
fun MainIdeScreen(viewModel: MainViewModel) {
    Box(
        modifier = Modifier.fillMaxSize().background(Color.Transparent),
        contentAlignment = Alignment.Center
    ) {
        Text("Main IDE Screen")
    }
}