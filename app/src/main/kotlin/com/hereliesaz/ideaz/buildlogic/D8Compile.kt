package com.hereliesaz.ideaz.buildlogic

import com.hereliesaz.ideaz.IBuildCallback
import com.hereliesaz.ideaz.utils.ProcessExecutor
import java.io.File

class D8Compile(
    private val javaPath: String, // FIX: Add javaPath
    private val d8Path: String,
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

        val classFiles = File(classesDir).walk().filter { it.isFile && it.extension == "class" }.map { it.absolutePath }.toList()

        val command = mutableListOf(
            javaPath, // FIX: Use javaPath instead of "java"
            "-jar",
            d8Path,
            "--output",
            outputDir
        )

        // Add android.jar and all resolved dependencies to the classpath
        command.add("--lib")
        command.add(androidJarPath)
        if (classpath.isNotEmpty()) {
            classpath.split(File.pathSeparator).forEach {
                command.add("--lib")
                command.add(it)
            }
        }

        command.addAll(classFiles)

        val processResult = ProcessExecutor.execute(command)
        return BuildResult(processResult.exitCode == 0, processResult.output)
    }
}