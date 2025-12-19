package com.hereliesaz.ideaz.ui.delegates

import com.hereliesaz.ideaz.ui.SettingsViewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

class AIDelegate(
    private val settings: SettingsViewModel,
    private val scope: CoroutineScope,
    private val onLog: (String) -> Unit,
    private val patchApplier: (String) -> Unit
) {
    val sessions = MutableStateFlow<List<String>>(emptyList())

    fun startContextualAITask(prompt: String) {}
    fun resumeSession(id: String) {}
    fun fetchSessionsForRepo(repo: String) {}
    fun addDependencyViaAI(dep: String) {
        startContextualAITask("Please add dependency: $dep")
    }
}