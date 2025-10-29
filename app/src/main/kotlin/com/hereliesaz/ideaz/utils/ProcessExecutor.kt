package com.hereliesaz.ideaz.utils

import java.io.InputStreamReader

object ProcessExecutor {
    fun execute(command: List<String>): Boolean {
        try {
            val process = ProcessBuilder(command)
                .redirectErrorStream(true)
                .start()

            process.inputStream.bufferedReader().useLines { lines ->
                lines.forEach(::println)
            }

            val exitCode = process.waitFor()
            return exitCode == 0
        } catch (e: Exception) {
            e.printStackTrace()
            return false
        }
    }
}
