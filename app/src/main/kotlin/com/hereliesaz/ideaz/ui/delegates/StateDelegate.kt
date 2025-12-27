package com.hereliesaz.ideaz.ui.delegates

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Delegate responsible for holding and managing shared UI state.
 * Centralizes state flows for logs, progress, visibility, and navigation.
 *
 * Performance Note:
 * Implements log batching via a Channel to prevent O(N^2) list copying and excessive UI recompositions
 * when logs are streaming in rapidly (e.g., from BuildService or AI).
 */
class StateDelegate(
    scope: CoroutineScope
) {
    companion object {
        private const val MAX_LOG_SIZE = 1000
        private const val BATCH_INTERVAL_MS = 100L
    }

    private sealed interface LogEvent {
        data class Build(val msg: String) : LogEvent
        data class Ai(val msg: String) : LogEvent
        data class System(val msg: String) : LogEvent
        data class BatchLines(val lines: List<String>) : LogEvent
    }

    private val logChannel = Channel<LogEvent>(Channel.UNLIMITED)

    init {
        scope.launch {
            val buffer = mutableListOf<LogEvent>()
            while (true) {
                // Wait for the first item
                val first = logChannel.receive()
                buffer.add(first)

                // Wait a bit to collect more items (debouncing/batching)
                delay(BATCH_INTERVAL_MS)

                // Drain the channel of currently available items
                var result = logChannel.tryReceive()
                while (result.isSuccess) {
                    buffer.add(result.getOrThrow())
                    result = logChannel.tryReceive()
                }

                // Process the batch
                if (buffer.isNotEmpty()) {
                    processLogBatch(buffer)
                    buffer.clear()
                }
            }
        }
    }

    private fun processLogBatch(events: List<LogEvent>) {
        val allLines = ArrayList<String>(events.size)
        val systemLines = ArrayList<String>()

        // Single pass to categorize logs
        for (event in events) {
            when (event) {
                is LogEvent.Build -> {
                    event.msg.split('\n').filterTo(allLines) { it.isNotBlank() }
                }
                is LogEvent.Ai -> {
                    val lines = event.msg.split('\n').filter { it.isNotBlank() }
                    lines.mapTo(allLines) { "[AI] $it" }
                }
                is LogEvent.System -> {
                    event.msg.split('\n').filterTo(systemLines) { it.isNotBlank() }
                }
                is LogEvent.BatchLines -> {
                    allLines.addAll(event.lines)
                }
            }
        }

        if (allLines.isNotEmpty()) {
            appendBuildLogLinesInternal(allLines)
        }

        if (systemLines.isNotEmpty()) {
            _systemLog.appendCapped(systemLines)
        }
    }

    private val _loadingProgress = MutableStateFlow<Int?>(null)
    /** Current loading progress (0-100), or null if not loading. */
    val loadingProgress = _loadingProgress.asStateFlow()

    private val _isTargetAppVisible = MutableStateFlow(false)
    /** Whether the target application (or WebView) is currently visible. */
    val isTargetAppVisible = _isTargetAppVisible.asStateFlow()

    private val _buildLog = MutableStateFlow<List<String>>(emptyList())
    /** The main build/system log. Capped at [MAX_LOG_SIZE] lines. */
    val buildLog = _buildLog.asStateFlow()

    private val _gitLog = MutableStateFlow<List<String>>(emptyList())
    /** Log containing only Git-related messages. */
    val gitLog = _gitLog.asStateFlow()

    private val _aiLog = MutableStateFlow<List<String>>(emptyList())
    /** Log containing only AI-related messages. */
    val aiLog = _aiLog.asStateFlow()

    private val _pureBuildLog = MutableStateFlow<List<String>>(emptyList())
    /** Log containing only build messages (excluding Git and AI). */
    val pureBuildLog = _pureBuildLog.asStateFlow()

    private val _systemLog = MutableStateFlow<List<String>>(emptyList())
    /** The system logcat stream. Capped at [MAX_LOG_SIZE] lines. */
    val systemLog = _systemLog.asStateFlow()

    private val _pendingRoute = MutableStateFlow<String?>(null)
    /** Pending navigation route to be consumed by the UI. */
    val pendingRoute = _pendingRoute.asStateFlow()

    private val _currentWebUrl = MutableStateFlow<String?>(null)
    /** The URL currently loaded in the WebView (for Web projects). */
    val currentWebUrl = _currentWebUrl.asStateFlow()

    /** Combined stream of log lines for UI display. */
    val filteredLog = _buildLog.asStateFlow()

    /** Appends a message to the build log. Splits by newline if necessary. */
    fun appendBuildLog(msg: String) {
        logChannel.trySend(LogEvent.Build(msg))
    }

    /** Appends multiple lines to the build log with a size cap. */
    fun appendBuildLogLines(lines: List<String>) {
        if (lines.isEmpty()) return
        logChannel.trySend(LogEvent.BatchLines(lines))
    }

    /** Internal method to actually update the StateFlows. Called by the batch processor. */
    private fun appendBuildLogLinesInternal(lines: List<String>) {
        if (lines.isEmpty()) return

        // Append to main log
        _buildLog.appendCapped(lines)

        // Distribute to specific logs
        val gitLines = lines.filter { it.contains("[GIT]") }
        if (gitLines.isNotEmpty()) _gitLog.appendCapped(gitLines)

        val aiLines = lines.filter { it.contains("[AI]") }
        if (aiLines.isNotEmpty()) _aiLog.appendCapped(aiLines)

        val pureLines = lines.filter { !it.contains("[GIT]") && !it.contains("[AI]") }
        if (pureLines.isNotEmpty()) _pureBuildLog.appendCapped(pureLines)
    }

    /** Helper to append lines with a cap, reusing list creation logic. */
    private fun MutableStateFlow<List<String>>.appendCapped(lines: List<String>) {
         this.update { current ->
            val totalSize = current.size + lines.size
            if (totalSize <= MAX_LOG_SIZE) {
                current + lines
            } else {
                // Optimization: Avoid creating an intermediate list of size (current + lines)
                // just to slice it. Instead, build the result directly.
                val keepFromCurrent = MAX_LOG_SIZE - lines.size
                if (keepFromCurrent <= 0) {
                    lines.takeLast(MAX_LOG_SIZE)
                } else {
                    val result = java.util.ArrayList<String>(MAX_LOG_SIZE)
                    // We assume 'current' is RandomAccess (ArrayList) for O(1) access
                    val start = current.size - keepFromCurrent
                    for (i in start until current.size) {
                        result.add(current[i])
                    }
                    result.addAll(lines)
                    result
                }
            }
        }
    }

    /** Appends an AI message to the log (prefixed with [AI]) with a size cap. */
    fun appendAiLog(msg: String) {
        logChannel.trySend(LogEvent.Ai(msg))
    }

    fun appendSystemLog(msg: String) {
        logChannel.trySend(LogEvent.System(msg))
    }

    /** Sets the loading progress. Pass null to hide the indicator. */
    fun setLoadingProgress(p: Int?) { _loadingProgress.value = p }

    /** Sets the visibility of the target app/WebView. */
    fun setTargetAppVisible(v: Boolean) { _isTargetAppVisible.value = v }

    private val _bottomSheetState = MutableStateFlow<com.composables.core.SheetDetent>(com.composables.core.SheetDetent.Hidden)
    val bottomSheetState = _bottomSheetState.asStateFlow()
    fun setBottomSheetState(s: com.composables.core.SheetDetent) { _bottomSheetState.value = s }

    /** Sets the pending navigation route. */
    fun setPendingRoute(r: String?) { _pendingRoute.value = r }

    /** Sets the current Web URL. */
    fun setCurrentWebUrl(url: String?) { _currentWebUrl.value = url }

    private val _webReloadTrigger = MutableStateFlow(0L)
    val webReloadTrigger = _webReloadTrigger.asStateFlow()
    fun triggerWebReload() { _webReloadTrigger.value = System.currentTimeMillis() }

    /** Clears all logs. */
    fun clearLog() {
        _buildLog.value = emptyList()
        _gitLog.value = emptyList()
        _aiLog.value = emptyList()
        _pureBuildLog.value = emptyList()
        _systemLog.value = emptyList()
    }
}
