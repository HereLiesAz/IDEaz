package com.hereliesaz.ideaz.ui.delegates

import com.hereliesaz.ideaz.ui.SettingsViewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

class GitDelegate(
    private val settings: SettingsViewModel,
    private val scope: CoroutineScope,
    private val onLog: (String) -> Unit,
    private val onProgress: (Int?) -> Unit
) {
    val gitStatus = MutableStateFlow("Clean")
    val commitHistory = MutableStateFlow<List<String>>(emptyList())
    val branches = MutableStateFlow<List<String>>(emptyList())

    fun refreshGitData() {}
    fun fetch() {}
    fun pull() {}
    fun push() {}
    fun stash(msg: String) {}
    fun unstash() {}
    fun switchBranch(branch: String) {}
    fun applyUnidiffPatch(patch: String) {}
}