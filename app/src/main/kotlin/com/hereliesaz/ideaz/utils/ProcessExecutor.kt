package com.hereliesaz.ideaz.utils

import java.io.BufferedReader
import java.io.InputStreamReader

data class ProcessResult(val exitCode: Int, val output: String)

object ProcessExecutor {
    fun execute(command: List<String>): ProcessResult {
        return try {
            val process = ProcessBuilder(command)
                .redirectErrorStream(true)
                .start()

            val output = BufferedReader(InputStreamReader(process.inputStream)).use { reader ->
                buildString {
                    reader.lineSequence().forEach { append(it).append(System.lineSeparator()) }
                }
            }

            val exitCode = process.waitFor()
            ProcessResult(exitCode, output)
        } catch (e: Exception) {
            e.printStackTrace()
            ProcessResult(-1, e.message ?: "Unknown error")
        }
    }

    fun executeAndStream(
        command: List<String>,
        onOutputLine: (String) -> Unit,
        onCompletion: (Int) -> Unit
    ) {
        try {
            val process = ProcessBuilder(command)
                .redirectErrorStream(true)
                .start()

            BufferedReader(InputStreamReader(process.inputStream)).use { reader ->
                reader.lineSequence().forEach(onOutputLine)
            }

            onCompletion(process.waitFor())
        } catch (e: Exception) {
            e.printStackTrace()
            onOutputLine("Error: ${e.message ?: "Unknown error"}")
            onCompletion(-1)
        }
    }

    fun executeAndStreamSync(
        command: List<String>,
        onOutputLine: (String) -> Unit
    ): ProcessResult {
        return try {
            val process = ProcessBuilder(command)
                .redirectErrorStream(true)
                .start()

            val fullOutput = BufferedReader(InputStreamReader(process.inputStream)).use { reader ->
                buildString {
                    reader.lineSequence().forEach { line ->
                        onOutputLine(line)
                        append(line).append(System.lineSeparator())
                    }
                }
            }

            val exitCode = process.waitFor()
            ProcessResult(exitCode, fullOutput)
        } catch (e: Exception) {
            e.printStackTrace()
            val msg = e.message ?: "Unknown error"
            onOutputLine("Error: $msg")
            ProcessResult(-1, msg)
        }
    }
}
