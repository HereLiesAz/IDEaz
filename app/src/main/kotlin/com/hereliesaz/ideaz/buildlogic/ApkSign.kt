package com.hereliesaz.ideaz.buildlogic

import com.hereliesaz.ideaz.IBuildCallback
import com.hereliesaz.ideaz.utils.ProcessExecutor

class ApkSign(
    private val javaPath: String, // <-- FIX: Add this argument
    private val apkSignerPath: String,
    private val keystorePath: String,
    private val keystorePass: String,
    private val keyAlias: String,
    private val apkPath: String
) : BuildStep {

    override fun execute(callback: IBuildCallback?): BuildResult {
        val command = listOf(
            javaPath, // Use libjava.so
            "-jar", apkSignerPath, // To run the JAR file
            "sign",
            "--ks", keystorePath,
            "--ks-pass", "pass:$keystorePass",
            "--key-pass", "pass:$keystorePass",
            "--ks-key-alias", keyAlias,
            apkPath
        )
        val processResult = ProcessExecutor.execute(command)
        return BuildResult(processResult.exitCode == 0, processResult.output)
    }
}