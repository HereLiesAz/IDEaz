package com.hereliesaz.ideaz.models

import kotlinx.serialization.Serializable

@Serializable
data class PromptHistory(
    val entries: List<PromptEntry> = emptyList()
)

@Serializable
data class PromptEntry(
    val timestamp: Long,
    val text: String,
    val screenshotFilename: String? = null // Filename in .ideaz/screenshots
)