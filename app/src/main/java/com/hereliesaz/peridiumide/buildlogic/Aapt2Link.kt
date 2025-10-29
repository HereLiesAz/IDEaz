package com.hereliesaz.peridiumide.buildlogic

import com.hereliesaz.peridiumide.utils.CommandLineUtils
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
        println("Executing Aapt2Link")
        val command = listOf(
            aapt2Path, "link",
            "-I", androidJarPath,
            "-R", "$compiledResDir/resources.zip",
            "--manifest", manifestPath,
            "-o", outputApkPath,
            "--java", outputJavaPath
        )
        return CommandLineUtils.execute(command, File("."))
    }
}
