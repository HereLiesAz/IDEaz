package com.hereliesaz.peridiumide.buildlogic

import com.hereliesaz.peridiumide.utils.CommandLineUtils
import java.io.File

class ApkSign(
    private val apkSignerPath: String,
    private val keystorePath: String,
    private val keystorePass: String,
    private val keyAlias: String,
    private val apkPath: String
) : BuildStep {
    override fun execute(): Boolean {
        println("Executing ApkSign")
        val command = listOf(
            apkSignerPath, "sign",
            "--ks", keystorePath,
            "--ks-pass", "pass:$keystorePass",
            "--key-pass", "pass:$keystorePass",
            "--ks-key-alias", keyAlias,
            apkPath
        )
        return CommandLineUtils.execute(command, File("."))
    }
}
