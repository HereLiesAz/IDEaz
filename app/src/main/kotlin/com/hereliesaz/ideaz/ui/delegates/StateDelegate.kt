package com.hereliesaz.ideaz.ui.delegates

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

class StateDelegate {
    val loadingProgress = MutableStateFlow<Int?>(null)
    val isTargetAppVisible = MutableStateFlow(false)
    val currentWebUrl = MutableStateFlow<String?>(null)
    val buildLog = MutableStateFlow("")
    val filteredLog = MutableStateFlow("")
    val pendingRoute = MutableStateFlow<String?>(null)

    fun appendBuildLog(msg: String) {}
    fun appendAiLog(msg: String) {}
    fun setLoadingProgress(p: Int?) {}
    fun setCurrentWebUrl(url: String?) {}
    fun setTargetAppVisible(visible: Boolean) {}
    fun setPendingRoute(route: String?) {}
}