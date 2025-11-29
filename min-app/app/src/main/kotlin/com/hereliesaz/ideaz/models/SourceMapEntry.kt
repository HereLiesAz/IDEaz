package com.hereliesaz.ideaz.models

import kotlinx.serialization.Serializable

@Serializable
data class SourceMapEntry(
    val id: String,
    val file: String,
    val line: Int
)
