package com.hereliesaz.ideaz.buildlogic

import com.hereliesaz.ideaz.IBuildCallback
import com.hereliesaz.ideaz.utils.ProcessExecutor
import com.hereliesaz.ideaz.utils.ToolInfo
import java.io.File

class KotlincCompile(
    private val kotlincInfo: ToolInfo,
    private val androidJarPath: String,
    private val srcDir: String,
    private val outputDir: File,
    private val classpath: String
    // --- REMOVED javaPath ---
) : BuildStep {

    override fun execute(callback: IBuildCallback?): BuildResult {
        if (!outputDir.exists()) {
            outputDir.mkdirs()
        }

        val sourceFiles = File(srcDir).walk().filter { it.isFile && it.extension == "kt" }.map { it.absolutePath }.toList()

        val fullClasspath = "$androidJarPath${File.pathSeparator}$classpath".trim(File.pathSeparatorChar)

        val command = mutableListOf<String>()
        if (kotlincInfo.type == com.hereliesaz.ideaz.utils.ToolType.ASSET) {
            command.add("java")
            command.add("-jar")
        }
        command.add(kotlincInfo.path)
        command.addAll(listOf(
            "-d", outputDir.absolutePath,
            "-no-reflect",
            "-no-stdlib",
            "-Xuse-old-backend"
        ))

        if (fullClasspath.isNotEmpty()) {
            command.add("-cp")
            command.add(fullClasspath)
        }

        command.addAll(sourceFiles)

        val processResult = ProcessExecutor.execute(command)
        return BuildResult(processResult.exitCode == 0, processResult.output)
    }
}