package com.hereliesaz.ideaz

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.tooling.preview.Preview
import android.content.Intent
import android.net.Uri
import com.hereliesaz.ideaz.ui.theme.IDEazTheme
import androidx.core.content.FileProvider
import java.io.File

import androidx.activity.viewModels
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import com.hereliesaz.ideaz.ui.MainViewModel
import com.hereliesaz.ideaz.ui.PromptPopup
import com.hereliesaz.ideaz.ui.EnhancedCodeEditor
import androidx.compose.foundation.layout.Row
import androidx.navigation.compose.rememberNavController
import com.hereliesaz.aznavrail.AzNavRail
import com.hereliesaz.ideaz.ui.LiveOutputBottomCard
import com.hereliesaz.ideaz.ui.SettingsScreen
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable

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
        viewModel.listSessions()
    }

    override fun onStop() {
        super.onStop()
        viewModel.unbindService(this)
    }
}

@Composable
fun MainScreen(viewModel: MainViewModel) {
    val buildLog by viewModel.buildLog.collectAsState()
    val codeContent by viewModel.codeContent.collectAsState()
    val buildStatus by viewModel.buildStatus.collectAsState()
    val aiStatus by viewModel.aiStatus.collectAsState()
    val session by viewModel.session.collectAsState()
    val sessions by viewModel.sessions.collectAsState()
    val activities by viewModel.activities.collectAsState()
    val context = LocalContext.current
    var showPromptPopup by remember { mutableStateOf(false) }
    var isInspecting by remember { mutableStateOf(false) }
    val navController = rememberNavController()

    if (showPromptPopup) {
        PromptPopup(
            onDismiss = { showPromptPopup = false },
            onSubmit = { prompt ->
                viewModel.sendPrompt(prompt)
                showPromptPopup = false
            }
        )
    }

    Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
        Row(
            modifier = Modifier
                .padding(innerPadding)
                .fillMaxSize()
        ) {
            AzNavRail(navController = navController) {
                azRailItem(id = "build", text = "Build", onClick = { viewModel.startBuild(context) })
                azRailItem(id = "prompt", text = "Prompt", onClick = { showPromptPopup = true })
                    azRailItem(id = "patch", text = "Patch", onClick = { viewModel.applyPatch(context) }, disabled = activities.lastOrNull()?.artifacts?.firstOrNull()?.changeSet?.gitPatch == null)
                    if (buildStatus == "Build Failed") {
                        azRailItem(id = "debug", text = "Debug", onClick = { viewModel.debugBuild() })
                    }
                    azRailToggle(
                        id = "inspect",
                        isChecked = isInspecting,
                        toggleOnText = "Stop Inspecting",
                        toggleOffText = "Start Inspecting",
                        onClick = {
                            isInspecting = !isInspecting
                            if (isInspecting) {
                                viewModel.startInspection(context)
                            } else {
                                viewModel.stopInspection(context)
                            }
                        }
                    )
                    azRailItem(id = "refresh", text = "Refresh Sessions", onClick = { viewModel.listSessions() })
                    azRailItem(id = "settings", text = "Settings", onClick = { navController.navigate("settings") })
                }
                Column(modifier = Modifier.weight(1f)) {
                    Spacer(modifier = Modifier.weight(0.2f))
                    NavHost(
                        navController = navController,
                        startDestination = "main",
                        modifier = Modifier.weight(0.8f)
                    ) {
                        composable("main") {
                            Column {
                                Text(text = "Build Status: $buildStatus")
                                Text(text = "AI Status: $aiStatus")
                            session?.let {
                                Text(text = "Session: ${it.name}")
                                it.outputs.firstOrNull()?.pullRequest?.let { pr ->
                                    Text(text = "Pull Request: ${pr.title}")
                                }
                            }
                            Text(text = "Sessions:")
                            sessions.forEach {
                                Text(text = it.name)
                            }
                            Text(text = "Activities:")
                            activities.forEach {
                                Text(text = it.description)
                            }
                            LiveOutputBottomCard(
                                logStream = viewModel.buildLog,
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                    composable("settings") {
                        SettingsScreen()
                    }
                }
            }
        }
    }
}
