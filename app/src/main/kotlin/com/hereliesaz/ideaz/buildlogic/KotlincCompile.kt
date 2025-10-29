package com.hereliesaz.ideaz.buildlogic

import com.hereliesaz.ideaz.utils.ProcessExecutor
import java.io.File

class KotlincCompile(
    private val kotlincPath: String,
    private val androidJarPath: String,
    private val javaDir: String,
    private val classesDir: String
) : BuildStep {

    override fun execute(): Boolean {
        val classesDirFile = File(classesDir)
        if (!classesDirFile.exists()) {
            classesDirFile.mkdirs()
        }

        val command = listOf(
            "java",
            "-jar",
            kotlincPath,
            "-classpath",
            androidJarPath,
            "-d",
            classesDir,
            javaDir
        )

        return ProcessExecutor.execute(command)
    }
}
