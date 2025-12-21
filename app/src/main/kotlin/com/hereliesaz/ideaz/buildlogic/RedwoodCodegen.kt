package com.hereliesaz.ideaz.buildlogic

import com.hereliesaz.ideaz.IBuildCallback
import com.hereliesaz.ideaz.utils.HybridToolchainManager
import com.hereliesaz.ideaz.utils.ProcessExecutor
import java.io.File

class RedwoodCodegen(
    private val javaPath: String,
    private val schemaType: String,
    private val outputDir: File,
    private val isHost: Boolean,
    private val filesDir: File
) : BuildStep {

    fun constructCommand(classpath: List<File>): List<String> {
        val cpString = classpath.joinToString(File.pathSeparator) { it.absolutePath }
        val command = mutableListOf(
            javaPath,
            "-cp", cpString,
            "app.cash.redwood.tooling.codegen.Main",
            "--schema", schemaType,
            "--out", outputDir.absolutePath
        )
        if (isHost) {
            command.add("--protocol-host")
            command.add("--widget")
        } else {
            command.add("--protocol-guest")
            command.add("--compose")
        }
        return command
    }

    override fun execute(callback: IBuildCallback?): BuildResult {
        callback?.onLog("[Redwood] Starting ${if(isHost) "Host" else "Guest"} code generation...")

        try {
            val classpath = HybridToolchainManager.getCodegenClasspath(filesDir, callback)
            if (classpath.isEmpty()) {
                // In a real run, this is an error.
                // However, HybridToolchainManager tries to download if missing.
                // If it fails, it returns empty list (based on resolveList logic? No, resolveList returns empty if input is empty, or throws).
                // resolveList throws. So we catch exception below.
                // If it returns empty, it means no dependencies requested.
                // But we requested redwood-codegen.
                // If resolving fails, it throws.
                // So this check is just sanity.
                return BuildResult(false, "Failed to resolve Redwood codegen classpath.")
            }

            outputDir.mkdirs()
            val command = constructCommand(classpath)

            val result = ProcessExecutor.executeAndStreamSync(command) { line ->
                 callback?.onLog("[Redwood] $line")
            }

            if (result.exitCode != 0) {
                return BuildResult(false, "Redwood codegen failed with exit code ${result.exitCode}")
            }

            return BuildResult(true, "Redwood codegen completed.")

        } catch (e: Exception) {
            e.printStackTrace()
            return BuildResult(false, "Redwood codegen exception: ${e.message}")
        }
    }
}
