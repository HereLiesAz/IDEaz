package com.hereliesaz.ideaz.ui.delegates

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn

class StateDelegate {
    private val _loadingProgress = MutableStateFlow<Int?>(null)
    val loadingProgress = _loadingProgress.asStateFlow()

    private val _isTargetAppVisible = MutableStateFlow(false)
    val isTargetAppVisible = _isTargetAppVisible.asStateFlow()

    private val _buildLog = MutableStateFlow("")
    val buildLog = _buildLog.asStateFlow()

    private val _aiLog = MutableStateFlow("")
    val aiLog = _aiLog.asStateFlow()

    private val _pendingRoute = MutableStateFlow<String?>(null)
    val pendingRoute = _pendingRoute.asStateFlow()

    private val _currentWebUrl = MutableStateFlow<String?>(null)
    val currentWebUrl = _currentWebUrl.asStateFlow()

    // Derived
    val filteredLog = combine(_buildLog, _aiLog) { b, a ->
        (b.lines() + a.lines()).filter { it.isNotBlank() }
    }

    fun appendBuildLog(msg: String) { _buildLog.value += msg }
    fun appendAiLog(msg: String) { _buildLog.value += "[AI] $msg\n" } // Simplified combiner
    fun setLoadingProgress(p: Int?) { _loadingProgress.value = p }
    fun setTargetAppVisible(v: Boolean) { _isTargetAppVisible.value = v }
    fun setPendingRoute(r: String?) { _pendingRoute.value = r }
    fun setCurrentWebUrl(url: String?) { _currentWebUrl.value = url }
    fun clearLog() { _buildLog.value = ""; _aiLog.value = "" }
}