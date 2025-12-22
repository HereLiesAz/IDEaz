package com.hereliesaz.ideaz.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.*
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.composables.core.BottomSheet
import com.composables.core.BottomSheetState
import com.composables.core.SheetDetent
import kotlinx.coroutines.launch

@Composable
fun IdeBottomSheet(
    sheetState: BottomSheetState,
    viewModel: MainViewModel,
    peekDetent: SheetDetent,
    halfwayDetent: SheetDetent,
    fullyExpandedDetent: SheetDetent,
    screenHeight: Dp,
    onSendPrompt: (String) -> Unit
) {
    val isHalfwayExpanded = sheetState.currentDetent == halfwayDetent || sheetState.currentDetent == fullyExpandedDetent
    val logMessages by viewModel.filteredLog.collectAsState(initial = emptyList())
    val clipboardManager = LocalClipboardManager.current
    val coroutineScope = rememberCoroutineScope()
    val listState = rememberLazyListState()

    // --- OPTIMIZED AUTO-SCROLL ---
    // Avoid animateScrollToItem which is heavy and causes high CPU/ANRs during fast updates.
    // Also only scroll if we were already at the bottom.
    var autoScrollEnabled by remember { mutableStateOf(true) }

    // Detect if user scrolled up manually
    LaunchedEffect(listState.isScrollInProgress) {
        if (listState.isScrollInProgress) {
            val layoutInfo = listState.layoutInfo
            val lastVisibleItem = layoutInfo.visibleItemsInfo.lastOrNull()
            autoScrollEnabled = lastVisibleItem != null && lastVisibleItem.index >= layoutInfo.totalItemsCount - 2
        }
    }

    LaunchedEffect(logMessages.size) {
        if (autoScrollEnabled && logMessages.isNotEmpty()) {
            // Use scrollToItem (instant) instead of animateScrollToItem (heavy)
            listState.scrollToItem(logMessages.size - 1)
        }
    }

    // Theming Logic
    val themeMode by viewModel.settingsViewModel.themeMode.collectAsState()
    val isSystemDark = isSystemInDarkTheme()
    val isDark = when (themeMode) {
        SettingsViewModel.THEME_DARK -> true
        SettingsViewModel.THEME_LIGHT -> false
        else -> isSystemDark
    }

    val customColorScheme = if (isDark) {
        darkColorScheme(
            surface = Color(0xFF1E1E1E),
            onSurface = Color.White,
            background = Color(0xFF1E1E1E),
            onBackground = Color.White
        )
    } else {
        lightColorScheme(
            surface = Color(0xFFEEEEEE),
            onSurface = Color.Black,
            background = Color(0xFFEEEEEE),
            onBackground = Color.Black
        )
    }

    val contentHeight = when (sheetState.currentDetent) {
        fullyExpandedDetent -> screenHeight * 0.8f
        halfwayDetent -> screenHeight * 0.5f
        peekDetent -> screenHeight * 0.25f
        else -> 0.dp
    }

    val bottomBufferHeight = screenHeight * 0.075f

    // Tabs
    var selectedTab by remember { mutableIntStateOf(0) }
    val tabs = listOf("All", "Build", "Git", "AI", "System")

    val systemLogMessages by viewModel.stateDelegate.systemLog.collectAsState()

    // Memoize filtered logs to avoid expensive re-filtering on every recomposition
    val filteredMessages = remember(logMessages, systemLogMessages, selectedTab) {
        when (selectedTab) {
            1 -> logMessages.filter { !it.contains("[AI]") && !it.contains("[GIT]") }
            2 -> logMessages.filter { it.contains("[GIT]") }
            3 -> logMessages.filter { it.contains("[AI]") }
            4 -> systemLogMessages
            else -> logMessages
        }
    }

    MaterialTheme(colorScheme = customColorScheme) {
        BottomSheet(
            state = sheetState,
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
        ) {
            Box(modifier = Modifier.fillMaxSize()) {

                if (contentHeight > 0.dp) {
                    Column(modifier = Modifier.height(contentHeight)) {

                        if (isHalfwayExpanded) {
                            TabRow(
                                selectedTabIndex = selectedTab,
                                containerColor = MaterialTheme.colorScheme.surface,
                                contentColor = MaterialTheme.colorScheme.onSurface,
                                divider = {},
                                indicator = { tabPositions ->
                                    if (selectedTab < tabPositions.size) {
                                        TabRowDefaults.Indicator(
                                            modifier = Modifier.tabIndicatorOffset(tabPositions[selectedTab]),
                                            color = MaterialTheme.colorScheme.onSurface
                                        )
                                    }
                                }
                            ) {
                                tabs.forEachIndexed { index, title ->
                                    Tab(
                                        selected = selectedTab == index,
                                        onClick = { selectedTab = index },
                                        text = { Text(title, color = MaterialTheme.colorScheme.onSurface) }
                                    )
                                }
                            }
                        }

                        if (filteredMessages.isEmpty()) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .weight(1f)
                                    .padding(horizontal = 16.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Icon(
                                        imageVector = Icons.Default.Info,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                                        modifier = Modifier.padding(bottom = 8.dp)
                                    )
                                    Text(
                                        text = when (selectedTab) {
                                            1 -> "No build logs yet"
                                            2 -> "No git activity recorded"
                                            3 -> "No AI interactions yet"
                                            4 -> "No system events"
                                            else -> "No logs available"
                                        },
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                                        textAlign = TextAlign.Center
                                    )
                                }
                            }
                        } else {
                            LazyColumn(
                                state = listState,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .weight(1f)
                                    .padding(horizontal = 16.dp, vertical = 8.dp)
                            ) {
                                itemsIndexed(
                                    items = filteredMessages,
                                    // Use stable keys if possible. For simple logs, index + content hash is okay-ish.
                                    key = { index, message -> "$index-${message.hashCode()}" }
                                ) { _, message ->
                                    Text(
                                        text = message,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                }
                            }
                        }

                        ContextlessChatInput(
                            modifier = Modifier.fillMaxWidth(),
                            onSend = onSendPrompt
                        )

                        Spacer(modifier = Modifier.height(bottomBufferHeight))
                    }
                }

                if (isHalfwayExpanded) {
                    Row(
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(top = 48.dp, end = 16.dp)
                    ) {
                        IconButton(onClick = {
                            coroutineScope.launch {
                                clipboardManager.setText(AnnotatedString(filteredMessages.joinToString("\n")))
                            }
                        }) {
                            Icon(
                                imageVector = Icons.Default.ContentCopy,
                                contentDescription = "Copy Log",
                                tint = MaterialTheme.colorScheme.onSurface
                            )
                        }
                        IconButton(onClick = { viewModel.clearLog() }) {
                            Icon(
                                imageVector = Icons.Default.Clear,
                                contentDescription = "Clear Log",
                                tint = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                }
            }
        }
    }
}
