package com.hereliesaz.peridiumide.buildlogic

import com.hereliesaz.peridiumide.utils.ProcessExecutor

class ApkSign(
    private val apkSignerPath: String,
    private val keystorePath: String,
    private val keystorePass: String,
    private val keyAlias: String,
    private val apkPath: String
) : BuildStep {

    override fun execute(): Boolean {
        val command = listOf(
            "java",
            "-jar",
            apkSignerPath,
            "sign",
            "--ks",
            keystorePath,
            "--ks-pass",
            "pass:$keystorePass",
            "--ks-key-alias",
            keyAlias,
            apkPath
        )

        return ProcessExecutor.execute(command)
    }
}
