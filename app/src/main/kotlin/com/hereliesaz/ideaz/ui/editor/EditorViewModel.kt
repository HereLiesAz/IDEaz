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

    private suspend fun compileCode(sourceCode: String) {
        val dir = projectDir
        if (dir == null) return

        withContext(Dispatchers.IO) {
            // Note: This relies on the file being saved to disk.
            // Ideally, we should save the 'sourceCode' to the appropriate file before compiling.
            // But since we don't know *which* file is being edited here (EditorViewModel is generic),
            // we assume the user has saved or we rely on auto-save elsewhere.
            // If this is a single-file scratchpad, we might need a temp file approach, but JsCompilerService requires a project dir.
            // For now, we trigger project compilation.

            val result = compilerService.compileProject(dir)
            _compilationResult.value = result
            if (result.success) {
                _hotReloadEvent.emit(System.currentTimeMillis())
            }
        }
    }
}
