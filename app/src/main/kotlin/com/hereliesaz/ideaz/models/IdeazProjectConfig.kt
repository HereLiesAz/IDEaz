package com.hereliesaz.ideaz.models

import kotlinx.serialization.Serializable

@Serializable
data class IdeazProjectConfig(
    val projectType: String = ProjectType.UNKNOWN.name,
    val packageName: String? = null,
    val branch: String = "main",
    val lastUpdated: Long = System.currentTimeMillis()
)