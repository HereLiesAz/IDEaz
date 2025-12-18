package com.hereliesaz.ideaz.ui.delegates

import android.app.Application
import com.hereliesaz.ideaz.ui.SettingsViewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

class UpdateDelegate(
    private val application: Application,
    private val settings: SettingsViewModel,
    private val scope: CoroutineScope,
    private val onLog: (String) -> Unit
) {
    val updateStatus = MutableStateFlow<String?>(null)
    val showUpdateWarning = MutableStateFlow(false)
    val updateMessage = MutableStateFlow("")
    val updateVersion = MutableStateFlow("1.0.0")

    fun checkForExperimentalUpdates() {}
    fun confirmUpdate() {}
    fun dismissUpdateWarning() {}
}