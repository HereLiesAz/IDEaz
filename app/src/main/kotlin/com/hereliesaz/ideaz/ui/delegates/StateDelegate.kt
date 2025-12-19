package com.hereliesaz.ideaz.ui.delegates

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn

/**
 * Delegate responsible for holding and managing shared UI state.
 * Centralizes state flows for logs, progress, visibility, and navigation.
 */
class StateDelegate {
    private val _loadingProgress = MutableStateFlow<Int?>(null)
    /** Current loading progress (0-100), or null if not loading. */
    val loadingProgress = _loadingProgress.asStateFlow()

    private val _isTargetAppVisible = MutableStateFlow(false)
    /** Whether the target application (or WebView) is currently visible. */
    val isTargetAppVisible = _isTargetAppVisible.asStateFlow()

    // Bolt Optimization: Use List<String> to avoid O(N^2) string concatenation
    private val _buildLog = MutableStateFlow<List<String>>(emptyList())
    /** The main build/system log. */
    val buildLog = _buildLog.asStateFlow()

    private val _pendingRoute = MutableStateFlow<String?>(null)
    /** Pending navigation route to be consumed by the UI. */
    val pendingRoute = _pendingRoute.asStateFlow()

    private val _currentWebUrl = MutableStateFlow<String?>(null)
    /** The URL currently loaded in the WebView (for Web projects). */
    val currentWebUrl = _currentWebUrl.asStateFlow()

    // Derived
    /** Combined stream of log lines for UI display. */
    val filteredLog = _buildLog.asStateFlow()

    /** Appends a message to the build log. */
    fun appendBuildLog(msg: String) {
        val lines = msg.split('\n').filter { it.isNotBlank() }
        if (lines.isNotEmpty()) {
            _buildLog.value = _buildLog.value + lines
        }
    }

    /** Appends an AI message to the log (prefixed with [AI]). */
    fun appendAiLog(msg: String) {
        val lines = msg.split('\n').filter { it.isNotBlank() }
        if (lines.isNotEmpty()) {
            val prefixed = lines.map { "[AI] $it" }
            _buildLog.value = _buildLog.value + prefixed
        }
    }

    /** Sets the loading progress. Pass null to hide the indicator. */
    fun setLoadingProgress(p: Int?) { _loadingProgress.value = p }

    /** Sets the visibility of the target app/WebView. */
    fun setTargetAppVisible(v: Boolean) { _isTargetAppVisible.value = v }

    /** Sets the pending navigation route. */
    fun setPendingRoute(r: String?) { _pendingRoute.value = r }

    /** Sets the current Web URL. */
    fun setCurrentWebUrl(url: String?) { _currentWebUrl.value = url }

    /** Clears all logs. */
    fun clearLog() { _buildLog.value = emptyList() }
}
