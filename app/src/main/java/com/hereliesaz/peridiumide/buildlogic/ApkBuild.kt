package com.hereliesaz.peridiumide.buildlogic

import com.hereliesaz.peridiumide.utils.CommandLineUtils
import java.io.File

class ApkBuild(
    private val outputApkPath: String,
    private val resourcesApkPath: String,
    private val classesDexDir: String
) : BuildStep {
    override fun execute(): Boolean {
        println("Executing ApkBuild")
        val classesDexPath = File(classesDexDir, "classes.dex").absolutePath
        if (!File(classesDexPath).exists()) {
            println("classes.dex not found in $classesDexDir")
            return false
        }
        val command = listOf("cp", resourcesApkPath, outputApkPath)
        if (!CommandLineUtils.execute(command, File("."))) {
            return false
        }
        val command2 = listOf("zip", "-j", outputApkPath, classesDexPath)
        return CommandLineUtils.execute(command2, File("."))
    }
}
