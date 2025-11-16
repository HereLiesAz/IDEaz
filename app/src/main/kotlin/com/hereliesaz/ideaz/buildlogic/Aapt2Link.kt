package com.hereliesaz.ideaz.buildlogic

import com.hereliesaz.ideaz.IBuildCallback
import com.hereliesaz.ideaz.utils.ProcessExecutor
import java.io.File

class Aapt2Link(
    private val aapt2Path: String,
    private val compiledResDir: String,
    private val androidJarPath: String,
    private val manifestPath: String,
    private val outputApkPath: String,
    private val outputJavaPath: String,
    private val minSdk: Int,
    private val targetSdk: Int
) : BuildStep {

    override fun execute(callback: IBuildCallback?): BuildResult {
        File(outputApkPath).parentFile?.mkdirs()
        File(outputJavaPath).mkdirs()

        val compiledFiles = File(compiledResDir).walk()
            .filter { it.isFile && it.extension == "flat" }
            .map { it.absolutePath }
            .toList()

        if (compiledFiles.isEmpty()) {
            return BuildResult(false, "Aapt2Link: No .flat files found in $compiledResDir")
        }

        val command = mutableListOf(
            aapt2Path,
            "link",
            "-o", outputApkPath,
            "-I", androidJarPath,
            "--manifest", manifestPath,
            "--java", outputJavaPath,
            "--auto-add-overlay",
            // --- FIX: Corrected flag names as you specified ---
            "--minsdk", minSdk.toString(),
            "--targetsdk", targetSdk.toString()
        )
        command.addAll(compiledFiles)

        val processResult = ProcessExecutor.execute(command)
        return BuildResult(processResult.exitCode == 0, processResult.output)
    }
}