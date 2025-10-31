package com.hereliesaz.ideaz.utils

import com.hereliesaz.ideaz.IBuildCallback
import java.io.BufferedReader
import java.io.InputStreamReader

data class ProcessResult(val exitCode: Int, val output: String)

object ProcessExecutor {
    fun execute(command: List<String>, callback: IBuildCallback? = null): ProcessResult {
        try {
            val process = ProcessBuilder(command)
                .redirectErrorStream(true)
                .start()

            val reader = BufferedReader(InputStreamReader(process.inputStream))
            val output = StringBuilder()
            var line: String?
            while (reader.readLine().also { line = it } != null) {
                output.append(line).append("\n")
                callback?.onLog(line)
            }

            val exitCode = process.waitFor()

            return ProcessResult(exitCode, output.toString())
        } catch (e: Exception) {
            e.printStackTrace()
            return ProcessResult(-1, e.message ?: "Unknown error")
        }
    }
}
