package com.hereliesaz.peridiumide.buildlogic

import com.hereliesaz.peridiumide.utils.CommandLineUtils
import java.io.File

class D8Compile(
    private val d8Path: String,
    private val libPath: String,
    private val outputDir: String,
    private val inputDir: String
) : BuildStep {
    override fun execute(): Boolean {
        println("Executing D8Compile")
        val classFiles = File(inputDir).walk().filter { it.isFile && it.name.endsWith(".class") }.map { it.absolutePath }.toList()
        if (classFiles.isEmpty()) {
            println("No class files found in $inputDir")
            return false
        }
        val command = mutableListOf(d8Path, "--lib", libPath, "--output", outputDir)
        command.addAll(classFiles)
        return CommandLineUtils.execute(command, File("."))
    }
}
