package com.hereliesaz.ideaz.utils

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.isActive
import java.io.BufferedReader
import java.io.InputStreamReader

object LogcatReader {
    fun observe(): Flow<String> = flow {
        // -v time to show timestamp
        val process = Runtime.getRuntime().exec("logcat -v time")
        val reader = BufferedReader(InputStreamReader(process.inputStream))

        try {
            var line: String? = reader.readLine()
            while (currentCoroutineContext().isActive && line != null) {
                emit(line)
                line = reader.readLine()
            }
        } catch (e: Exception) {
            emit("Logcat reader failed: ${e.message}")
        } finally {
            process.destroy()
        }
    }.flowOn(Dispatchers.IO)
}
