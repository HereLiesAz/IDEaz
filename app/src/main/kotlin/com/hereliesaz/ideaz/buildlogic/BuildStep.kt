package com.hereliesaz.ideaz.buildlogic

data class BuildResult(val success: Boolean, val output: String)

import com.hereliesaz.ideaz.IBuildCallback

interface BuildStep {
    fun execute(callback: IBuildCallback? = null): BuildResult
}
