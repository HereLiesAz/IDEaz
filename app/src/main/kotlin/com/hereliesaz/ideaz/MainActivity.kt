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
import androidx.compose.animation.AnimatedVisibility
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
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.ui.unit.dp
import androidx.compose.ui.graphics.Color
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.launch
import androidx.compose.foundation.layout.offset
import androidx.compose.ui.unit.IntOffset
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.foundation.background
import com.composables.core.BottomSheet
import com.composables.core.SheetDetent
import com.composables.core.rememberBottomSheetState
import com.hereliesaz.ideaz.ui.ContextlessChatInput
import com.hereliesaz.ideaz.ui.ProjectSettingsScreen
import androidx.compose.foundation.layout.height

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

// 1. Define custom detents
private val AlmostHidden by lazy { SheetDetent("almost_hidden") { _, _ -> 1.dp } }
private val Peek = SheetDetent("peek") { containerHeight, _ -> containerHeight * 0.2f }
private val Halfway = SheetDetent("halfway") { containerHeight, _ -> containerHeight * 0.5f }


@OptIn(ExperimentalMaterial3Api::class)
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
    var showPromptPopup by remember{ mutableStateOf(false) }
    var isInspecting by remember { mutableStateOf(false) }
    val navController = rememberNavController()
    val scope = rememberCoroutineScope()

    // --- Bottom Sheet State ---
    val configuration = LocalConfiguration.current
    val screenHeight = configuration.screenHeightDp.dp

    val sheetState = rememberBottomSheetState(
        initialDetent = AlmostHidden,
        detents = listOf(AlmostHidden, Peek, Halfway)
    )
    val isIdeVisible = sheetState.currentDetent != AlmostHidden
    // --- End Bottom Sheet State ---

    // Set background color based on sheet state
    val containerColor = if (isIdeVisible) {
        MaterialTheme.colorScheme.background
    } else {
        Color.Transparent
    }

    // Helper function to navigate away from settings when an action is taken
    val handleActionClick = { action: () -> Unit ->
        if (navController.currentBackStackEntry?.destination?.route == "settings" ||
            navController.currentBackStackEntry?.destination?.route == "project_settings") {
            navController.navigate("main")
        }
        action()
    }

    if (showPromptPopup) {
        PromptPopup(
            onDismiss = { showPromptPopup = false },
            onSubmit = { prompt ->
                viewModel.sendPrompt(prompt)
                showPromptPopup = false
            }
        )
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        containerColor = containerColor,
    ) { innerPadding ->

        // Use a Box to layer the IDE content and the BottomSheet
        Box(modifier = Modifier.padding(innerPadding).fillMaxSize()) {

            // This is the main screen content, which we make visible/invisible
            AnimatedVisibility(visible = isIdeVisible) {
                Row(
                    modifier = Modifier
                        .fillMaxSize()
                        // This background makes the IDE opaque when visible
                        .background(MaterialTheme.colorScheme.background)
                ) {
                    AzNavRail(navController = navController) {

                        azRailItem(id = "build", text = "Build", onClick = { handleActionClick { viewModel.startBuild(context) } })
                        azRailItem(id = "prompt", text = "Prompt", onClick = { handleActionClick { showPromptPopup = true } })

                        azRailToggle(
                            id = "inspect",
                            isChecked = isInspecting,
                            toggleOnText = "Stop Inspecting",
                            toggleOffText = "Start Inspecting",
                            onClick = {
                                handleActionClick {
                                    isInspecting = !isInspecting
                                    if (isInspecting) {
                                        viewModel.startInspection(context)
                                    } else {
                                        viewModel.stopInspection(context)
                                    }
                                }
                            }
                        )
                        azRailItem(id = "refresh", text = "Refresh Sessions", onClick = { handleActionClick { viewModel.listSessions() } })

                        azRailItem(id = "main", text = "Status", onClick = { handleActionClick { navController.navigate("main") } })
                        azRailItem(id = "project_settings", text = "Project", onClick = { navController.navigate("project_settings") })
                        azRailItem(id = "settings", text = "Settings", onClick = { navController.navigate("settings") })
                    }
                    Column(modifier = Modifier.weight(1f)) {
                        // NavHost now contains the swappable screen content
                        NavHost(
                            navController = navController,
                            startDestination = "main",
                            modifier = Modifier.weight(1.0f) // Upper area
                        ) {
                            composable("main") {
                                // This column now ONLY contains status information
                                // Re-added the top 20% spacer
                                Column {
                                    Spacer(modifier = Modifier.weight(0.2f))
                                    Column(
                                        modifier = Modifier.padding(all = 8.dp).weight(0.8f)
                                            .verticalScroll(rememberScrollState())
                                    ) {
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
                                    }
                                }
                            }
                            composable("settings") {
                                SettingsScreen()
                            }
                            composable("project_settings") {
                                ProjectSettingsScreen(viewModel = viewModel)
                            }
                        }
                    }
                }
            }

            // --- compose-unstyled BottomSheet Implementation ---
            BottomSheet(
                state = sheetState,
                modifier = Modifier.fillMaxSize()
            ) {
                // This is the sheet content
                Column(modifier = Modifier.fillMaxSize()) {

                    val logModifier = if (sheetState.currentDetent == Peek) {
                        Modifier.weight(0.5f)
                    } else {
                        Modifier.weight(1f)
                    }

                    LiveOutputBottomCard(
                        logStream = viewModel.buildLog,
                        modifier = logModifier
                    )

                    // Show chat input only when in Peek or Halfway state
                    AnimatedVisibility(visible = sheetState.currentDetent == Peek || sheetState.currentDetent == Halfway) {

                        val chatModifier = if (sheetState.currentDetent == Peek) {
                            Modifier.weight(0.5f)
                        } else {
                            // 10% of screen height, as requested
                            Modifier.height(screenHeight * 0.1f)
                        }

                        ContextlessChatInput(
                            modifier = chatModifier,
                            onSend = { viewModel.sendPrompt(it) }
                        )
                    }
                }
            }
        }
    }
}