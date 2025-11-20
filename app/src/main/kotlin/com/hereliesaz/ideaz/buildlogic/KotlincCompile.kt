package com.hereliesaz.ideaz.buildlogic

import com.hereliesaz.ideaz.IBuildCallback
import com.hereliesaz.ideaz.utils.ProcessExecutor
import java.io.File

class KotlincCompile(
    private val kotlincJarPath: String,
    private val androidJarPath: String,
    private val srcDir: String,
    private val outputDir: File,
    private val classpath: String,
    private val javaBinaryPath: String
) : BuildStep {

    override fun execute(callback: IBuildCallback?): BuildResult {
        if (!outputDir.exists()) outputDir.mkdirs()
        val sourceFiles = File(srcDir).walk().filter { it.isFile && it.extension == "kt" }.map { it.absolutePath }.toList()
        val fullClasspath = "$androidJarPath${File.pathSeparator}$classpath".trim(File.pathSeparatorChar)

        val command = mutableListOf(
            javaBinaryPath,
            "-jar",
            kotlincJarPath,
            "-d", outputDir.absolutePath,
            "-no-reflect",
            "-no-stdlib",
            "-Xuse-old-backend"
        )
        if (fullClasspath.isNotEmpty()) {
            command.add("-cp")
            command.add(fullClasspath)
        }
        command.addAll(sourceFiles)

        val processResult = ProcessExecutor.execute(command)
        return BuildResult(processResult.exitCode == 0, processResult.output)
    }
}