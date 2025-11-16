package com.hereliesaz.ideaz.buildlogic

import com.hereliesaz.ideaz.IBuildCallback
import com.hereliesaz.ideaz.utils.ProcessExecutor
import java.io.File

class Aapt2Compile(
    private val aapt2Path: String,
    private val resDir: String,
    private val compiledResDir: String,
    // --- We accept these from BuildService but WILL NOT use them ---
    private val minSdk: Int,
    private val targetSdk: Int
) : BuildStep {

    override fun execute(callback: IBuildCallback?): BuildResult {
        val compiledResDirFile = File(compiledResDir)
        if (!compiledResDirFile.exists()) {
            compiledResDirFile.mkdirs()
        }

        // --- FIX: Removed all flags not supported by the aapt2 compile help output ---
        val command = listOf(
            aapt2Path,
            "compile",
            "--dir", resDir,
            "-o", compiledResDir
        )
        // --- END FIX ---

        val processResult = ProcessExecutor.execute(command)
        return BuildResult(processResult.exitCode == 0, processResult.output)
    }
}