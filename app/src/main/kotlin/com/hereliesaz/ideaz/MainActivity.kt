package com.hereliesaz.ideaz

import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import com.hereliesaz.ideaz.ui.MainViewModel
import com.hereliesaz.ideaz.ui.PromptPopup
import com.hereliesaz.ideaz.ui.theme.IDEazTheme

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
        viewModel.bindService(this)
    }

    override fun onStop() {
        super.onStop()
        viewModel.unbindService(this)
    }
}

@Composable
fun MainScreen(viewModel: MainViewModel) {
    val buildLog by viewModel.buildLog.collectAsState()
    val buildStatus by viewModel.buildStatus.collectAsState()
    val aiStatus by viewModel.aiStatus.collectAsState()
    val patch by viewModel.patch.collectAsState()
    val showPromptPopup by viewModel.showPromptPopup.collectAsState()
    val context = LocalContext.current

    LaunchedEffect(Unit) {
        viewModel.listenForInspectionEvents()
    }

    if (showPromptPopup) {
        PromptPopup(
            onDismiss = { viewModel.dismissPopup() },
            onSubmit = { prompt ->
                viewModel.sendPrompt(prompt)
            }
        )
    }

    Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
        Column(
            modifier = Modifier
                .padding(innerPadding)
                .fillMaxSize()
        ) {
            Button(onClick = { viewModel.startBuild(context) }) {
                Text("Build Project")
            }
            Button(onClick = {
                val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
                context.startActivity(intent)
            }) {
                Text("Toggle Inspection Mode")
            }
            Button(
                onClick = { viewModel.applyPatch(context) },
                enabled = patch != null
            ) {
                Text("Apply Patch")
            }
            Text(text = "Build Status: $buildStatus")
            Text(text = "AI Status: $aiStatus")
            Text(
                text = buildLog,
                modifier = Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState())
            )
        }
    }
}
