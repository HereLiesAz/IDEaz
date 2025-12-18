package com.hereliesaz.ideaz.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue // FIXED
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.hereliesaz.ideaz.ui.MainViewModel

@Composable
fun IdeBottomSheet(viewModel: MainViewModel) {
    val buildLog by viewModel.buildLog.collectAsState()

    Column(modifier = Modifier.fillMaxWidth().height(300.dp).padding(16.dp)) {
        Text("Build Log", style = MaterialTheme.typography.titleMedium)
        Text(
            text = buildLog,
            modifier = Modifier.fillMaxSize()
        )
    }
}