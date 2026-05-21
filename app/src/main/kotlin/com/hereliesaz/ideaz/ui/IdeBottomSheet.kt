package com.hereliesaz.ideaz.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Save
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.hereliesaz.aznavrail.bottomsheet.AzSheetController
import com.hereliesaz.aznavrail.model.AzSheetDetent
import kotlinx.coroutines.launch

@Composable
fun IdeBottomSheet(
    controller: AzSheetController,
    viewModel: MainViewModel,
    screenHeight: Dp,
    onSendPrompt: (String) -> Unit
) {
    val detent by controller.detentFlow.collectAsState()
    if (detent == AzSheetDetent.HIDDEN) return

    // Collect specific log streams directly to avoid filtering on every recomposition.
    val logMessages by viewModel.filteredLog.collectAsState(initial = emptyList()) // "All"
    val gitLog by viewModel.stateDelegate.gitLog.collectAsState()
    val aiLog by viewModel.stateDelegate.aiLog.collectAsState()
    val pureBuildLog by viewModel.stateDelegate.pureBuildLog.collectAsState()
    val systemLogMessages by viewModel.stateDelegate.systemLog.collectAsState()
    val chatMessages by viewModel.stateDelegate.chatMessages.collectAsState()
    val isChatLoading by viewModel.stateDelegate.isChatLoading.collectAsState()

    var selectedTab by rememberSaveable { mutableIntStateOf(0) }
    val tabs = listOf("All", "Build", "Git", "AI", "System", "Chat")

    val baseMessages = when (selectedTab) {
        1 -> pureBuildLog
        2 -> gitLog
        3 -> aiLog
        4 -> systemLogMessages
        else -> logMessages
    }

    // Theming
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

    MaterialTheme(colorScheme = customColorScheme) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
        ) {
            when (detent) {
                AzSheetDetent.PEEK -> PeekTicker(baseMessages)
                AzSheetDetent.HALF, AzSheetDetent.FULL -> ExpandedContent(
                    selectedTab = selectedTab,
                    onSelectTab = { selectedTab = it },
                    tabs = tabs,
                    baseMessages = baseMessages,
                    chatMessages = chatMessages,
                    isChatLoading = isChatLoading,
                    onClearLog = { viewModel.clearLog() },
                    onSendPrompt = onSendPrompt,
                    viewModel = viewModel,
                    screenHeight = screenHeight,
                )
                AzSheetDetent.HIDDEN -> Unit
            }
        }
    }
}

@Composable
private fun PeekTicker(messages: List<String>) {
    val latest = messages.lastOrNull().orEmpty()
    Text(
        text = latest,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurface,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp)
    )
}

@Composable
private fun ExpandedContent(
    selectedTab: Int,
    onSelectTab: (Int) -> Unit,
    tabs: List<String>,
    baseMessages: List<String>,
    chatMessages: List<com.hereliesaz.ideaz.ai.ChatMessage>,
    isChatLoading: Boolean,
    onClearLog: () -> Unit,
    onSendPrompt: (String) -> Unit,
    viewModel: MainViewModel,
    screenHeight: Dp
) {
    val clipboard = LocalClipboard.current
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val listState = rememberLazyListState()

    // LogKitty UX: per-tab filter, reset when tab changes.
    var filterQuery by remember(selectedTab) { mutableStateOf("") }

    // Multi-select: indices into filteredMessages. Reset on tab/filter change.
    var selectedIndices by remember(selectedTab) { mutableStateOf<Set<Int>>(emptySet()) }
    LaunchedEffect(filterQuery) { selectedIndices = emptySet() }
    val selectionMode = selectedIndices.isNotEmpty()

    // LogKitty UX: pause/resume. While paused, render the snapshot taken at pause-time.
    var paused by remember(selectedTab) { mutableStateOf(false) }
    var pausedSnapshot by remember(selectedTab) { mutableStateOf<List<String>?>(null) }

    LaunchedEffect(paused, selectedTab) {
        pausedSnapshot = if (paused) baseMessages else null
    }

    val visibleSource = pausedSnapshot ?: baseMessages
    val filteredMessages = remember(visibleSource, filterQuery) {
        if (filterQuery.isBlank()) visibleSource
        else visibleSource.filter { it.contains(filterQuery, ignoreCase = true) }
    }

    // LogKitty UX: save current tab to file via SAF.
    val tabName = tabs.getOrNull(selectedTab) ?: "log"
    val saveLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("text/plain")
    ) { uri ->
        if (uri != null) {
            coroutineScope.launch {
                runCatching {
                    context.contentResolver.openOutputStream(uri)?.bufferedWriter()?.use { w ->
                        w.write(filteredMessages.joinToString("\n"))
                    }
                }
            }
        }
    }

    // Optimized auto-scroll: only when at bottom; instant scroll, no animation.
    var autoScrollEnabled by remember { mutableStateOf(true) }
    LaunchedEffect(listState.isScrollInProgress) {
        if (listState.isScrollInProgress) {
            val layoutInfo = listState.layoutInfo
            val lastVisibleItem = layoutInfo.visibleItemsInfo.lastOrNull()
            autoScrollEnabled = lastVisibleItem != null &&
                lastVisibleItem.index >= layoutInfo.totalItemsCount - 2
        }
    }
    LaunchedEffect(filteredMessages.size) {
        if (!paused && autoScrollEnabled && filteredMessages.isNotEmpty()) {
            listState.scrollToItem(filteredMessages.size - 1)
        }
    }

    val bottomBufferHeight = screenHeight * 0.075f

    Column(modifier = Modifier.fillMaxSize()) {
        SecondaryTabRow(
            selectedTabIndex = selectedTab,
            containerColor = MaterialTheme.colorScheme.surface,
            contentColor = MaterialTheme.colorScheme.onSurface,
            divider = {},
            indicator = {
                TabRowDefaults.SecondaryIndicator(
                    modifier = Modifier.tabIndicatorOffset(
                        selectedTabIndex = selectedTab,
                        matchContentSize = false
                    ),
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
        ) {
            tabs.forEachIndexed { index, title ->
                Tab(
                    selected = selectedTab == index,
                    onClick = { onSelectTab(index) },
                    text = { Text(title, color = MaterialTheme.colorScheme.onSurface) }
                )
            }
        }

        if (selectedTab == 5) {
            AiChatTab(
                messages = chatMessages,
                isLoading = isChatLoading,
                viewModel = viewModel,
                modifier = Modifier.weight(1f)
            )
            Spacer(modifier = Modifier.height(bottomBufferHeight))
            return@Column
        }

        // Filter + action row for log tabs.
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            OutlinedTextField(
                value = filterQuery,
                onValueChange = { filterQuery = it },
                placeholder = { Text("Filter…") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                modifier = Modifier.weight(1f)
            )
            IconButton(onClick = {
                val payload = if (selectionMode) {
                    selectedIndices.sorted()
                        .mapNotNull { filteredMessages.getOrNull(it) }
                        .joinToString("\n")
                } else {
                    filteredMessages.joinToString("\n")
                }
                coroutineScope.launch {
                    clipboard.setClipEntry(
                        androidx.compose.ui.platform.ClipEntry(
                            android.content.ClipData.newPlainText("Log", payload)
                        )
                    )
                }
                if (selectionMode) selectedIndices = emptySet()
            }) {
                Icon(
                    imageVector = Icons.Default.ContentCopy,
                    contentDescription = if (selectionMode) {
                        "Copy ${selectedIndices.size} selected"
                    } else "Copy Log",
                    tint = MaterialTheme.colorScheme.onSurface
                )
            }
            if (selectionMode) {
                IconButton(onClick = { selectedIndices = emptySet() }) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Clear selection",
                        tint = MaterialTheme.colorScheme.onSurface
                    )
                }
            }
            IconButton(onClick = {
                saveLauncher.launch("ideaz-${tabName.lowercase()}-${System.currentTimeMillis()}.log")
            }) {
                Icon(
                    imageVector = Icons.Default.Save,
                    contentDescription = "Save Log",
                    tint = MaterialTheme.colorScheme.onSurface
                )
            }
            IconButton(onClick = { paused = !paused }) {
                Icon(
                    imageVector = if (paused) Icons.Default.PlayArrow else Icons.Default.Pause,
                    contentDescription = if (paused) "Resume Streaming" else "Pause Streaming",
                    tint = MaterialTheme.colorScheme.onSurface
                )
            }
            IconButton(onClick = onClearLog) {
                Icon(
                    imageVector = Icons.Default.Clear,
                    contentDescription = "Clear Log",
                    tint = MaterialTheme.colorScheme.onSurface
                )
            }
        }

        if (paused) {
            val newSinceSnapshot = (baseMessages.size - (pausedSnapshot?.size ?: baseMessages.size))
                .coerceAtLeast(0)
            Text(
                text = "Paused — $newSinceSnapshot new line${if (newSinceSnapshot == 1) "" else "s"}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 2.dp)
            )
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
                        text = when {
                            filterQuery.isNotBlank() -> "No matches for \"$filterQuery\""
                            selectedTab == 1 -> "No build logs yet"
                            selectedTab == 2 -> "No git activity recorded"
                            selectedTab == 3 -> "No AI interactions yet"
                            selectedTab == 4 -> "No system events"
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
                    key = { index, _ -> index }
                ) { index, message ->
                    LogRow(
                        text = message,
                        selected = index in selectedIndices,
                        selectionMode = selectionMode,
                        onToggle = {
                            selectedIndices = if (index in selectedIndices) {
                                selectedIndices - index
                            } else {
                                selectedIndices + index
                            }
                        }
                    )
                }
            }
        }

        ContextlessChatInput(
            modifier = Modifier.fillMaxWidth(),
            viewModel = viewModel,
        )

        Spacer(modifier = Modifier.height(bottomBufferHeight))
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun LogRow(
    text: String,
    selected: Boolean,
    selectionMode: Boolean,
    onToggle: () -> Unit
) {
    val highlight = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.18f)
    Text(
        text = text,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurface,
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(
                onClick = { if (selectionMode) onToggle() },
                onLongClick = onToggle
            )
            .then(if (selected) Modifier.background(highlight) else Modifier)
            .padding(vertical = 2.dp)
    )
}

