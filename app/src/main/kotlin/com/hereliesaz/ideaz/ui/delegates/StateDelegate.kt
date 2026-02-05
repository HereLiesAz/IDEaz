package com.hereliesaz.ideaz.ui.delegates

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
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
 * **Architecture Note:**
 * This delegate acts as the "Single Source of Truth" for transient UI state that needs to be accessed
 * by multiple components (e.g., [MainViewModel], [BuildDelegate], [AIDelegate]).
 *
 * **Performance Critical:**
 * Implements log batching via a [Channel] to prevent O(N^2) list copying and excessive UI recompositions
 * when logs are streaming in rapidly (e.g., from BuildService or AI). Without this, the UI would freeze
 * during verbose build operations.
 *
 * @param scope The [CoroutineScope] in which state updates and batch processing run (typically ViewModel scope).
 * @param dispatcher The [CoroutineDispatcher] for processing log batches. Defaults to [Dispatchers.Default] to keep heavy list operations off the main thread.
 */
class StateDelegate(
    scope: CoroutineScope,
    dispatcher: CoroutineDispatcher = Dispatchers.Default
) {
    companion object {
        /** Maximum number of lines to keep in the in-memory log buffers. */
        private const val MAX_LOG_SIZE = 1000

        /**
         * Time window (in milliseconds) to buffer incoming logs before emitting a batch update.
         * 100ms provides a good balance between responsiveness and UI load.
         */
        private const val BATCH_INTERVAL_MS = 100L
    }

    /**
     * Sealed interface representing different types of log events that can be pushed to the channel.
     */
    private sealed interface LogEvent {
        /** A standard build log message (can be multiline). */
        data class Build(val msg: String) : LogEvent
        /** An AI response message (can be multiline). */
        data class Ai(val msg: String) : LogEvent
        /** A system log message (logcat). */
        data class System(val msg: String) : LogEvent
        /** A pre-batched list of lines (optimization). */
        data class BatchLines(val lines: List<String>) : LogEvent
    }

    // Unbounded channel to ensure producers (BuildService) never suspend/block when emitting logs.
    private val logChannel = Channel<LogEvent>(Channel.UNLIMITED)

    init {
        // Launch the log processing loop.
        scope.launch(dispatcher) {
            val buffer = mutableListOf<LogEvent>()
            while (true) {
                // Suspend until the first item arrives. This prevents the loop from spinning CPU when idle.
                val first = logChannel.receive()
                buffer.add(first)

                // Wait a short duration to collect subsequent items that arrive in "bursts".
                delay(BATCH_INTERVAL_MS)

                // Drain the channel of all currently available items without suspending.
                var result = logChannel.tryReceive()
                while (result.isSuccess) {
                    buffer.add(result.getOrThrow())
                    result = logChannel.tryReceive()
                }

                // Process the collected batch.
                if (buffer.isNotEmpty()) {
                    processLogBatch(buffer)
                    buffer.clear()
                }
            }
        }
    }

    /**
     * Processes a batch of [LogEvent]s, categorizing them and updating the respective StateFlows.
     * This runs on [dispatcher] (Default) to avoid blocking the Main thread.
     */
    private fun processLogBatch(events: List<LogEvent>) {
        val allLines = ArrayList<String>(events.size)
        val systemLines = ArrayList<String>()

        // Single pass to categorize logs and flatten multiline strings.
        for (event in events) {
            when (event) {
                is LogEvent.Build -> {
                    // Split by newline to ensure correct list display in UI.
                    event.msg.split('\n').filterTo(allLines) { it.isNotBlank() }
                }
                is LogEvent.Ai -> {
                    // Tag AI messages for easy filtering.
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

        // Update the StateFlows. Note: StateFlow.value assignment is thread-safe,
        // but observers will collect on their respective dispatchers.
        if (allLines.isNotEmpty()) {
            appendBuildLogLinesInternal(allLines)
        }

        if (systemLines.isNotEmpty()) {
            _systemLog.appendCapped(systemLines)
        }
    }

    // --- StateFlow Declarations ---

    private val _loadingProgress = MutableStateFlow<Int?>(null)
    /**
     * Current loading progress (0-100), or `null` if not loading.
     * Observed by the UI to show/hide progress bars.
     */
    val loadingProgress = _loadingProgress.asStateFlow()

    private val _isTargetAppVisible = MutableStateFlow(false)
    /**
     * Controls whether the target application (Android Virtual Display or WebView) is currently visible.
     * When `true`, the IDE UI (nav rail, etc.) might overlay the app.
     */
    val isTargetAppVisible = _isTargetAppVisible.asStateFlow()

    private val _buildLog = MutableStateFlow<List<String>>(emptyList())
    /**
     * The main build/system log. Contains all Build, Git, and AI messages.
     * Capped at [MAX_LOG_SIZE] lines to prevent memory issues.
     */
    val buildLog = _buildLog.asStateFlow()

    private val _gitLog = MutableStateFlow<List<String>>(emptyList())
    /** Filtered log containing only Git-related messages (tagged `[GIT]`). */
    val gitLog = _gitLog.asStateFlow()

    private val _aiLog = MutableStateFlow<List<String>>(emptyList())
    /** Filtered log containing only AI-related messages (tagged `[AI]`). */
    val aiLog = _aiLog.asStateFlow()

    private val _pureBuildLog = MutableStateFlow<List<String>>(emptyList())
    /** Filtered log containing only build messages (excluding Git and AI). */
    val pureBuildLog = _pureBuildLog.asStateFlow()

    private val _systemLog = MutableStateFlow<List<String>>(emptyList())
    /** The system logcat stream (e.g., from the device/emulator). */
    val systemLog = _systemLog.asStateFlow()

    private val _pendingRoute = MutableStateFlow<String?>(null)
    /**
     * A pending navigation route to be consumed by the [IdeNavHost].
     * Set by ViewModels when they need to trigger a screen transition.
     */
    val pendingRoute = _pendingRoute.asStateFlow()

    private val _currentWebUrl = MutableStateFlow<String?>(null)
    /** The URL currently loaded in the WebView (only relevant for Web projects). */
    val currentWebUrl = _currentWebUrl.asStateFlow()

    /** Combined stream of log lines for UI display (currently aliases `buildLog`). */
    val filteredLog = _buildLog.asStateFlow()

    private val _bottomSheetState = MutableStateFlow<com.composables.core.SheetDetent>(com.composables.core.SheetDetent.Hidden)
    /** Controls the expansion state of the global bottom sheet (Console). */
    val bottomSheetState = _bottomSheetState.asStateFlow()

    private val _webReloadTrigger = MutableStateFlow(0L)
    /**
     * Signal to reload the WebView.
     * Observers should check for value changes (using timestamp).
     */
    val webReloadTrigger = _webReloadTrigger.asStateFlow()

    // --- Public Mutators ---

    /**
     * Appends a message to the build log queue.
     * Thread-safe and non-blocking.
     */
    fun appendBuildLog(msg: String) {
        logChannel.trySend(LogEvent.Build(msg))
    }

    /**
     * Appends multiple lines to the build log queue.
     * More efficient for bulk updates.
     */
    fun appendBuildLogLines(lines: List<String>) {
        if (lines.isEmpty()) return
        logChannel.trySend(LogEvent.BatchLines(lines))
    }

    /**
     * Appends an AI message to the log queue (will be prefixed with `[AI]`).
     */
    fun appendAiLog(msg: String) {
        logChannel.trySend(LogEvent.Ai(msg))
    }

    /**
     * Appends a system log message to the log queue.
     */
    fun appendSystemLog(msg: String) {
        logChannel.trySend(LogEvent.System(msg))
    }

    /** Sets the loading progress. Pass `null` to hide the indicator. */
    fun setLoadingProgress(p: Int?) { _loadingProgress.value = p }

    /** Sets the visibility of the target app/WebView. */
    fun setTargetAppVisible(v: Boolean) { _isTargetAppVisible.value = v }

    /** Sets the bottom sheet state (Hidden, Peek, Expanded). */
    fun setBottomSheetState(s: com.composables.core.SheetDetent) { _bottomSheetState.value = s }

    /** Sets the pending navigation route. */
    fun setPendingRoute(r: String?) { _pendingRoute.value = r }

    /** Sets the current Web URL. */
    fun setCurrentWebUrl(url: String?) { _currentWebUrl.value = url }

    /** Triggers a WebView reload by updating the trigger timestamp. */
    fun triggerWebReload() { _webReloadTrigger.value = System.currentTimeMillis() }

    /** Clears all log StateFlows. */
    fun clearLog() {
        _buildLog.value = emptyList()
        _gitLog.value = emptyList()
        _aiLog.value = emptyList()
        _pureBuildLog.value = emptyList()
        _systemLog.value = emptyList()
    }

    // --- Internal Helpers ---

    /**
     * Internal method to actually update the StateFlows.
     * Called by the batch processor on the background thread.
     */
    private fun appendBuildLogLinesInternal(lines: List<String>) {
        if (lines.isEmpty()) return

        // Append to main log
        _buildLog.appendCapped(lines)

        // Distribute to specific logs (filtering here avoids filtering in UI Composable)
        val gitLines = lines.filter { it.contains("[GIT]") }
        if (gitLines.isNotEmpty()) _gitLog.appendCapped(gitLines)

        val aiLines = lines.filter { it.contains("[AI]") }
        if (aiLines.isNotEmpty()) _aiLog.appendCapped(aiLines)

        val pureLines = lines.filter { !it.contains("[GIT]") && !it.contains("[AI]") }
        if (pureLines.isNotEmpty()) _pureBuildLog.appendCapped(pureLines)
    }

    /**
     * Extension to append lines to a list StateFlow while maintaining a maximum size cap.
     * Handles list copying efficiently.
     */
    private fun MutableStateFlow<List<String>>.appendCapped(lines: List<String>) {
         this.update { current ->
            val totalSize = current.size + lines.size
            if (totalSize <= MAX_LOG_SIZE) {
                // If within limit, just concat
                current + lines
            } else {
                // Optimization: Avoid creating an intermediate list of size (current + lines)
                // just to immediately drop the head. Build the result list directly.
                val keepFromCurrent = MAX_LOG_SIZE - lines.size
                if (keepFromCurrent <= 0) {
                    // New lines exceed cap, just keep the last MAX_LOG_SIZE of new lines
                    lines.takeLast(MAX_LOG_SIZE)
                } else {
                    // Copy tail of current + all new lines
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
}
