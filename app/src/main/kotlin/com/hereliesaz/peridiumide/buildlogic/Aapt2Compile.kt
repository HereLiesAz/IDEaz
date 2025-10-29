package com.hereliesaz.peridiumide.buildlogic

import com.hereliesaz.peridiumide.utils.ProcessExecutor
import java.io.File

class Aapt2Compile(
    private val aapt2Path: String,
    private val resDir: String,
    private val compiledResDir: String
) : BuildStep {

    override fun execute(): Boolean {
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

        return ProcessExecutor.execute(command)
    }
}
