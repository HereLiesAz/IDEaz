package com.hereliesaz.ideaz.buildlogic

import com.hereliesaz.ideaz.IBuildCallback
import com.hereliesaz.ideaz.utils.ProcessExecutor
import java.io.File

class Aapt2Compile(
    private val aapt2Path: String,
    private val resDir: String,
    private val compiledResDir: String
) : BuildStep {

    override fun execute(callback: IBuildCallback?): BuildResult {
        val compiledResDirFile = File(compiledResDir)
        if (!compiledResDirFile.exists()) {
            compiledResDirFile.mkdirs()
        }

        val command = listOf(
            aapt2Path,
            "compile",
            "--dir",
            resDir,
            "-o",
            compiledResDir
        )

        val processResult = ProcessExecutor.execute(command)
        return BuildResult(processResult.exitCode == 0, processResult.output)
    }
}
