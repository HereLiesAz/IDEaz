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
    // Non-actionable framework log spam that should never reach the in-app
    // console. Match by substring; add new entries here as noise is identified.
    private val NOISE: List<String> = listOf(
        // Jetpack Compose accessibility emits this for nested nodes on nearly
        // every frame — pure noise for the user.
        "Drawing order is not available, was AccessibilityNodeInfo requested for a child node before its parent",
    )

    private fun isNoise(line: String): Boolean = NOISE.any { line.contains(it) }

    fun observe(): Flow<String> = flow {
        // -v time to show timestamp
        val process = Runtime.getRuntime().exec("logcat -v time")
        try {
            BufferedReader(InputStreamReader(process.inputStream)).use { reader ->
                var line: String? = reader.readLine()
                while (currentCoroutineContext().isActive && line != null) {
                    val current = line
                    if (!isNoise(current)) emit(current)
                    line = reader.readLine()
                }
            }
        } catch (e: Exception) {
            emit("Logcat reader failed: ${e.message}")
        } finally {
            process.destroy()
        }
    }.flowOn(Dispatchers.IO)
}
