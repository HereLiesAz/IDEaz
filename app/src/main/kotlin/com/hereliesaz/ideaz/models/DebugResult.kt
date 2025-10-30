package com.hereliesaz.ideaz.models

import kotlinx.serialization.Serializable

@Serializable
data class DebugResult(
    val explanation: String,
    val suggestedFix: String
)
