package com.hereliesaz.ideaz.buildlogic

import com.hereliesaz.ideaz.IBuildCallback

data class BuildResult(val success: Boolean, val output: String)

interface BuildStep {
    fun execute(callback: IBuildCallback? = null): BuildResult
}
