package com.hereliesaz.ideaz.ui.inspection

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow

object InspectionEvents {
    private val _events = MutableSharedFlow<String>()
    val events = _events.asSharedFlow()

    suspend fun emit(event: String) {
        _events.emit(event)
    }
}
