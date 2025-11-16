package com.hereliesaz.ideaz.utils

import java.io.BufferedReader
import java.io.InputStreamReader

data class ProcessResult(val exitCode: Int, val output: String)

object ProcessExecutor {
    fun execute(command: List<String>): ProcessResult {
        try {
            val finalCommand: List<String>

            // If the command is not a java -jar command, it's a native binary.
            // We must execute it via the shell (sh -c) to bypass noexec flags.
            if (command.none { it.contains("java") }) {
                // Wrap the command: sh -c "/path/to/aapt2 compile --dir /res -o /out"
                finalCommand = listOf("/system/bin/sh", "-c", command.joinToString(" "))
            } else {
                // This is a java -jar command, execute it directly
                finalCommand = command
            }

            val process = ProcessBuilder(finalCommand)
                .redirectErrorStream(true)
                .start()

            val reader = BufferedReader(InputStreamReader(process.inputStream))
            val output = StringBuilder()
            var line: String?
            while (reader.readLine().also { line = it } != null) {
                output.append(line).append("\n")
            }

            val exitCode = process.waitFor()

            return ProcessResult(exitCode, output.toString())
        } catch (e: Exception) {
            e.printStackTrace()
            return ProcessResult(-1, e.message ?: "Unknown error")
        }
    }
}