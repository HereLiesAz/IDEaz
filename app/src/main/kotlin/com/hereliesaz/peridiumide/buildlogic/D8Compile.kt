package com.hereliesaz.peridiumide.buildlogic

import com.hereliesaz.peridiumide.utils.ProcessExecutor
import java.io.File

class D8Compile(
    private val d8Path: String,
    private val androidJarPath: String,
    private val classesDir: String,
    private val outputDir: String
) : BuildStep {

    override fun execute(): Boolean {
        val outputDirFile = File(outputDir)
        if (!outputDirFile.exists()) {
            outputDirFile.mkdirs()
        }

        val classFiles = File(classesDir).walk().filter { it.isFile && it.extension == "class" }.map { it.absolutePath }.toList()

        val command = mutableListOf(
            "java",
            "-jar",
            d8Path,
            "--lib",
            androidJarPath,
            "--output",
            outputDir,
        )
        command.addAll(classFiles)

        return ProcessExecutor.execute(command)
    }
}
