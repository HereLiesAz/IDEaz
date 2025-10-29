package com.hereliesaz.ideaz.utils

import java.io.InputStreamReader

data class ProcessResult(val exitCode: Int, val output: String)

object ProcessExecutor {
    fun execute(command: List<String>): ProcessResult {
        try {
            val process = ProcessBuilder(command)
                .redirectErrorStream(true)
                .start()

            val output = process.inputStream.bufferedReader().readText()
            val exitCode = process.waitFor()

            return ProcessResult(exitCode, output)
        } catch (e: Exception) {
            e.printStackTrace()
            return ProcessResult(-1, e.message ?: "Unknown error")
        }
    }
}
