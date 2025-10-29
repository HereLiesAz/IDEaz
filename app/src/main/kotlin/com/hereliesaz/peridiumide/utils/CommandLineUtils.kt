package com.hereliesaz.peridiumide.utils

import java.io.File
import java.util.concurrent.TimeUnit

object CommandLineUtils {

    fun execute(command: List<String>, workingDir: File): Boolean {
        val process = ProcessBuilder(command)
            .directory(workingDir)
            .redirectOutput(ProcessBuilder.Redirect.PIPE)
            .redirectError(ProcessBuilder.Redirect.PIPE)
            .start()

        val finished = process.waitFor(60, TimeUnit.SECONDS)
        val output = process.inputStream.bufferedReader().readText()
        val error = process.errorStream.bufferedReader().readText()

        if (finished && process.exitValue() == 0) {
            if (output.isNotEmpty()) {
                println(output)
            }
            println("Command executed successfully: ${command.joinToString(" ")}")
            return true
        } else {
            if (output.isNotEmpty()) {
                println("Output: $output")
            }
            if (error.isNotEmpty()) {
                println("Error: $error")
            }
            println("Command failed: ${command.joinToString(" ")}")
            return false
        }
    }
}
