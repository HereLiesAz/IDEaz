package com.hereliesaz.ideaz.ui

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.navigation.compose.rememberNavController
import com.composables.core.rememberBottomSheetState
import com.hereliesaz.aznavrail.AzNavRail
import com.hereliesaz.aznavrail.model.AzButtonShape
import com.hereliesaz.aznavrail.model.AzHeaderIconShape
import com.hereliesaz.ideaz.ui.theme.IDEazTheme
import kotlinx.coroutines.launch

class BubbleActivity : ComponentActivity() {

    private val viewModel: MainViewModel by viewModels {
        MainViewModelFactory(application, SettingsViewModel(application))
    }

    private val bubbleReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            when (intent?.action) {
                "com.hereliesaz.ideaz.PROMPT_SUBMITTED_RECT",
                "com.hereliesaz.ideaz.PROMPT_SUBMITTED_NODE" -> {
                    // Handle selection return logic if needed
                }
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val filter = IntentFilter().apply {
            addAction("com.hereliesaz.ideaz.PROMPT_SUBMITTED_RECT")
            addAction("com.hereliesaz.ideaz.PROMPT_SUBMITTED_NODE")
        }
        ContextCompat.registerReceiver(this, bubbleReceiver, filter, ContextCompat.RECEIVER_NOT_EXPORTED)

        setContent {
            IDEazTheme(darkTheme = true) {
                val navController = rememberNavController()
                val scope = rememberCoroutineScope()

                val sheetState = rememberBottomSheetState(
                    initialDetent = Peek,
                    detents = listOf(AlmostHidden, Peek, Halfway)
                )

                val configuration = LocalConfiguration.current
                val screenHeight = configuration.screenHeightDp.dp

                Scaffold(
                    containerColor = Color.Transparent
                ) { innerPadding ->
                    Box(modifier = Modifier.padding(innerPadding).fillMaxSize()) {

                        // 1. Nav Rail
                        AzNavRail(navController = navController) {
                            azSettings(
                                packRailButtons = true,
                                defaultShape = AzButtonShape.RECTANGLE,
                                enableRailDragging = false,
                                headerIconShape = AzHeaderIconShape.NONE,
                                onUndock = {
                                    // Minimizes the bubble
                                    moveTaskToBack(true)
                                }
                            )

                            azRailItem(
                                id = "mode_toggle",
                                text = "Select",
                                onClick = {
                                    val intent = Intent("com.hereliesaz.ideaz.START_INSPECTION")
                                    sendBroadcast(intent)
                                    moveTaskToBack(true)
                                }
                            )

                            azRailItem(
                                id = "build",
                                text = "Build",
                                onClick = {
                                    val appName = viewModel.settingsViewModel.getAppName()
                                    if (appName != null) {
                                        val projectDir = filesDir.resolve(appName)
                                        viewModel.startBuild(this@BubbleActivity, projectDir)
                                        scope.launch { sheetState.animateTo(Halfway) }
                                    }
                                }
                            )

                            azRailItem(id = "settings", text = "Settings", onClick = { })
                        }

                        // 2. Bottom Sheet
                        IdeBottomSheet(
                            sheetState = sheetState,
                            viewModel = viewModel,
                            peekDetent = Peek,
                            halfwayDetent = Halfway,
                            screenHeight = screenHeight,
                            onSendPrompt = { viewModel.sendPrompt(it) }
                        )
                    }
                }
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        try {
            unregisterReceiver(bubbleReceiver)
        } catch (e: Exception) {
            // Ignore if not registered
        }
    }
}