package com.hereliesaz.ideaz.buildlogic

import com.hereliesaz.ideaz.IBuildCallback
import com.hereliesaz.ideaz.utils.ProcessExecutor
import java.io.File

class D8Compile(
    private val d8Path: String,
    private val javaPath: String,
    private val androidJarPath: String,
    private val classesDir: String,
    private val outputDir: String,
    private val classpath: String
) : BuildStep {

    override fun execute(callback: IBuildCallback?): BuildResult {
        val outputDirFile = File(outputDir)
        if (!outputDirFile.exists()) {
            outputDirFile.mkdirs()
        }

        val command = mutableListOf(
            javaPath,
            "-jar",
            d8Path,
            "--output",
            outputDir
        )

        command.add("--lib")
        command.add(androidJarPath)
        if (classpath.isNotEmpty()) {
            classpath.split(File.pathSeparator).forEach {
                command.add("--lib")
                command.add(it)
            }
        }

        command.add(classesDir)

        val processResult = ProcessExecutor.execute(command)
        return BuildResult(processResult.exitCode == 0, processResult.output)
    }
}