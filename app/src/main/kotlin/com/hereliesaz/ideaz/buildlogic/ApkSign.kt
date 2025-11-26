package com.hereliesaz.ideaz.buildlogic

import com.hereliesaz.ideaz.IBuildCallback
import com.hereliesaz.ideaz.utils.ProcessExecutor

class ApkSign(
    private val apkSignerPath: String,
    private val javaPath: String,
    private val keystorePath: String,
    private val keystorePass: String,
    private val keyAlias: String,
    private val apkPath: String
) : BuildStep {

    override suspend fun execute(callback: IBuildCallback?): BuildResult {
        val command = listOf(
            javaPath,
            "-jar",
            apkSignerPath,
            "sign",
            "--ks", keystorePath,
            "--ks-pass", "pass:$keystorePass",
            "--key-pass", "pass:$keystorePass",
            "--ks-key-alias", keyAlias,
            apkPath
        )
        val processResult = ProcessExecutor.executeAndStreamSync(command) { line ->
            callback?.onLog(line)
        }
        return BuildResult(processResult.exitCode == 0, processResult.output)
    }
}