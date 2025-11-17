package com.hereliesaz.ideaz.utils

import java.io.BufferedReader
import java.io.InputStreamReader

data class ProcessResult(val exitCode: Int, val output: String)

object ProcessExecutor {
    fun execute(command: List<String>): ProcessResult {
        try {
            val finalCommand: List<String>

            if (command.firstOrNull() != "java") {
                finalCommand = listOf("/system/bin/sh", "-c", command.joinToString(" "))
            } else {
                finalCommand = command
            }

            val process = ProcessBuilder(finalCommand)
                .redirectErrorStream(true)
                .start()

            val reader = BufferedReader(InputStreamReader(process.inputStream))
            val output = StringBuilder()
            var line: String?
            while (reader.readLine().also { line = it } != null) {
                output.append(line).append(System.lineSeparator())
            }

            val exitCode = process.waitFor()

            return ProcessResult(exitCode, output.toString())
        } catch (e: Exception) {
            e.printStackTrace()
            return ProcessResult(-1, e.message ?: "Unknown error")
        }
    }

    fun executeAndStream(
        command: List<String>,
        onOutputLine: (String) -> Unit,
        onCompletion: (Int) -> Unit
    ) {
        try {
            val finalCommand: List<String>
            if (command.firstOrNull() != "java") {
                finalCommand = listOf("/system/bin/sh", "-c", command.joinToString(" "))
            } else {
                finalCommand = command
            }

            val process = ProcessBuilder(finalCommand)
                .redirectErrorStream(true)
                .start()

            val reader = BufferedReader(InputStreamReader(process.inputStream))

            var line: String?
            while (reader.readLine().also { line = it } != null) {
                line?.let { onOutputLine(it) }
            }

            val exitCode = process.waitFor()
            onCompletion(exitCode)

        } catch (e: Exception) {
            e.printStackTrace()
            onOutputLine("Error: ${e.message ?: "Unknown error"}")
            onCompletion(-1)
        }
    }
}
