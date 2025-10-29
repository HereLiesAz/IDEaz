package com.hereliesaz.peridiumide.utils

import java.io.InputStreamReader

object ProcessExecutor {
    fun execute(command: List<String>): Boolean {
        try {
            val process = ProcessBuilder(command)
                .redirectErrorStream(true)
                .start()

            val reader = InputStreamReader(process.inputStream)
            reader.use {
                val output = it.readText()
                if (output.isNotEmpty()) {
                    println(output)
                }
            }

            val exitCode = process.waitFor()
            return exitCode == 0
        } catch (e: Exception) {
            e.printStackTrace()
            return false
        }
    }
}
