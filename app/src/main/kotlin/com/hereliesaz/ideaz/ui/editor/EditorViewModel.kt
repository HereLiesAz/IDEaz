package com.hereliesaz.ideaz.ui.editor

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hereliesaz.ideaz.services.JsCompilerService
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class EditorViewModel(
    private val compilerService: JsCompilerService
) : ViewModel() {

    private val _code = MutableStateFlow("")
    val code = _code.asStateFlow()

    private val _compilationResult = MutableStateFlow<JsCompilerService.Result?>(null)
    val compilationResult = _compilationResult.asStateFlow()

    private val _hotReloadEvent = MutableSharedFlow<Long>()
    val hotReloadEvent = _hotReloadEvent.asSharedFlow()

    // Using a separate flow for debouncing to avoid circular updates if onCodeChange updates state
    private val codeChangeFlow = MutableSharedFlow<String>()

    init {
        @OptIn(FlowPreview::class)
        viewModelScope.launch {
            codeChangeFlow
                .debounce(500L)
                .collect { sourceCode ->
                    compileCode(sourceCode)
                }
        }
    }

    fun onCodeChange(newCode: String) {
        _code.value = newCode
        viewModelScope.launch {
            codeChangeFlow.emit(newCode)
        }
    }

    private var projectDir: java.io.File? = null

    fun setProjectDir(dir: java.io.File) {
        projectDir = dir
    }

    private var currentFile: java.io.File? = null

    fun setCurrentFile(file: java.io.File) {
        currentFile = file
    }

    private suspend fun compileCode(sourceCode: String) {
        val dir = projectDir
        if (dir == null) return

        withContext(Dispatchers.IO) {
            // Save current content before compiling
            val file = currentFile
            if (file != null) {
                try {
                    file.writeText(sourceCode)
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }

            val result = compilerService.compileProject(dir)
            _compilationResult.value = result
            if (result.success) {
                _hotReloadEvent.emit(System.currentTimeMillis())
            }
        }
    }
}
