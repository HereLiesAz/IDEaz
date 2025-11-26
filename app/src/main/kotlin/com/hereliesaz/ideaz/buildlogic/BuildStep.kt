package com.hereliesaz.ideaz.buildlogic

import com.hereliesaz.ideaz.IBuildCallback

data class BuildResult(val success: Boolean, val output: String)

interface BuildStep {
    suspend fun execute(callback: IBuildCallback? = null): BuildResult
}
