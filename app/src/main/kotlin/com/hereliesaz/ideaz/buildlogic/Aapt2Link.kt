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
    private val targetSdk: Int,
    private val dependencyResources: List<String> = emptyList(),
    private val packageName: String? = null,
    private val assetsDir: String? = null
) : BuildStep {

    override fun execute(callback: IBuildCallback?): BuildResult {
        // --- Validation Logic ---
        val androidJar = File(androidJarPath)
        if (!androidJar.exists() || androidJar.length() == 0L) {
            val msg = "Aapt2Link Failed: android.jar at $androidJarPath is missing or empty."
            callback?.onLog(msg)
            return BuildResult(false, msg)
        }
        // ------------------------

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
            "--min-sdk-version", minSdk.toString(),
            "--target-sdk-version", targetSdk.toString()
        )

        if (!packageName.isNullOrBlank()) {
            command.add("--rename-manifest-package")
            command.add(packageName)
        }

        if (!assetsDir.isNullOrBlank()) {
            val assetsFile = File(assetsDir)
            if (assetsFile.exists() && assetsFile.isDirectory) {
                command.add("-A")
                command.add(assetsDir)
            }
        }

        command.addAll(dependencyResources)
        command.addAll(compiledFiles)

        val processResult = ProcessExecutor.executeAndStreamSync(command) { line ->
            callback?.onLog(line)
        }
        return BuildResult(processResult.exitCode == 0, processResult.output)
    }
}