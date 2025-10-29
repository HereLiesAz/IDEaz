package com.hereliesaz.ideaz.buildlogic

import com.hereliesaz.ideaz.utils.ProcessExecutor
import java.io.File

class Aapt2Link(
    private val aapt2Path: String,
    private val compiledResDir: String,
    private val androidJarPath: String,
    private val manifestPath: String,
    private val outputApkPath: String,
    private val outputJavaPath: String
) : BuildStep {

    override fun execute(): Boolean {
        val outputJavaDir = File(outputJavaPath)
        if (!outputJavaDir.exists()) {
            outputJavaDir.mkdirs()
        }

        val compiledResFiles = File(compiledResDir).listFiles()?.map { it.absolutePath } ?: emptyList()

        val command = mutableListOf(
            aapt2Path,
            "link",
            "-o",
            outputApkPath,
            "-I",
            androidJarPath,
            "--manifest",
            manifestPath,
            "--java",
            outputJavaPath
        )
        command.addAll(compiledResFiles)


        return ProcessExecutor.execute(command)
    }
}
