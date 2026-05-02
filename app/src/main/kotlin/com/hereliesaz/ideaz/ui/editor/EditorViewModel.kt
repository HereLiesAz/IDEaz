package com.hereliesaz.ideaz.ui.editor

import androidx.lifecycle.ViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

class EditorViewModel : ViewModel() {

    private val _code = MutableStateFlow("")
    val code = _code.asStateFlow()

    fun onCodeChange(newCode: String) {
        _code.value = newCode
    }

    private var projectDir: java.io.File? = null

    fun setProjectDir(dir: java.io.File) {
        projectDir = dir
    }

    private var currentFile: java.io.File? = null

    fun setCurrentFile(file: java.io.File) {
        currentFile = file
    }
}
