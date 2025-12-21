package com.hereliesaz.ideaz.ui.delegates

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/**
 * Delegate responsible for holding and managing shared UI state.
 * Centralizes state flows for logs, progress, visibility, and navigation.
 */
class StateDelegate {
    companion object {
        private const val MAX_LOG_SIZE = 1000
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
        val lines = msg.split('\n').filter { it.isNotBlank() }
        appendBuildLogLines(lines)
    }

    /** Appends multiple lines to the build log with a size cap. */
    fun appendBuildLogLines(lines: List<String>) {
        if (lines.isEmpty()) return
        _buildLog.update { current ->
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
        val lines = msg.split('\n').filter { it.isNotBlank() }
        if (lines.isNotEmpty()) {
            val prefixed = lines.map { "[AI] $it" }
            appendBuildLogLines(prefixed)
        }
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
    fun clearLog() { _buildLog.value = emptyList() }
}
