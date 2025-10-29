package com.hereliesaz.peridiumide.buildlogic

import com.hereliesaz.peridiumide.utils.CommandLineUtils
import java.io.File

class Aapt2Compile(
    private val aapt2Path: String,
    private val resDir: String,
    private val compiledResDir: String
) : BuildStep {
    override fun execute(): Boolean {
        println("Executing Aapt2Compile")
        val compiledResDirFile = File(compiledResDir)
        if (!compiledResDirFile.exists()) {
            compiledResDirFile.mkdirs()
        }
        val command = listOf(aapt2Path, "compile", "--dir", resDir, "-o", compiledResDir)
        return CommandLineUtils.execute(command, File("."))
    }
}
