package com.hereliesaz.peridiumide.buildlogic

import com.hereliesaz.peridiumide.utils.CommandLineUtils
import java.io.File

class KotlincCompile(
    private val kotlincPath: String,
    private val classpath: String,
    private val sourceDir: String,
    private val outputDir: String
) : BuildStep {
    override fun execute(): Boolean {
        println("Executing KotlincCompile")
        val outputDirFile = File(outputDir)
        if (!outputDirFile.exists()) {
            outputDirFile.mkdirs()
        }
        val command = listOf(kotlincPath, "-classpath", classpath, "-d", outputDir, sourceDir)
        return CommandLineUtils.execute(command, File("."))
    }
}
