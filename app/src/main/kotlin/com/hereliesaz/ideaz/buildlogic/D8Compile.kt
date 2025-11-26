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

    override suspend fun execute(callback: IBuildCallback?): BuildResult {
        val outputDirFile = File(outputDir)

        val classFiles = File(classesDir).walk().filter { it.isFile }.toList()
        val classpathFiles = classpath.split(File.pathSeparator).filter { it.isNotEmpty() }.map { File(it) }
        val allInputs = classFiles + classpathFiles + File(androidJarPath)

        if (BuildCacheManager.shouldSkip("d8", allInputs, outputDirFile)) {
            callback?.onLog("Skipping D8Compile: Up-to-date.")
            return BuildResult(true, "Up-to-date")
        }

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

        val processResult = ProcessExecutor.executeAndStreamSync(command) { line ->
            callback?.onLog(line)
        }
        if (processResult.exitCode == 0) {
            BuildCacheManager.updateSnapshot("d8", allInputs, outputDirFile)
        }
        return BuildResult(processResult.exitCode == 0, processResult.output)
    }
}