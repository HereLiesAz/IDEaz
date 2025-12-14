package com.hereliesaz.ideaz.utils

import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList

object ErrorCollector {
    private const val MAX_REPEATS = 3

    // Thread-safe storage
    private val errorCounts = ConcurrentHashMap<String, Int>()
    private val pendingErrors = CopyOnWriteArrayList<String>()

    // Errors containing these strings will be ignored
    private val IGNORED_ERRORS = listOf(
        "CancellationException",
        "StandbyState", // Common in UI state machines
        "User cancelled",
        "Socket closed"
    )

    fun report(t: Throwable, tag: String? = null) {
        val message = "${tag ?: "App"}: ${t.message ?: t.javaClass.simpleName}"
        report(message, t)
    }

    fun report(message: String, t: Throwable? = null) {
        if (isIgnored(message)) return

        // Create a unique key for deduplication (e.g., the message itself or message + exception type)
        val key = if (t != null) "$message|${t.javaClass.simpleName}" else message

        val count = errorCounts.merge(key, 1) { old, _ -> old + 1 } ?: 1

        if (count <= MAX_REPEATS) {
            val formattedError = buildString {
                appendLine("[$count] $message")
                t?.let {
                    // limit stack trace to first few lines to save tokens/space
                    it.stackTrace.take(5).forEach { element ->
                        appendLine("\tat $element")
                    }
                }
            }
            pendingErrors.add(formattedError)
        }
    }

    fun getAndClear(): String? {
        if (pendingErrors.isEmpty()) return null

        val allErrors = StringBuilder()
        // Drain the list
        val iterator = pendingErrors.iterator()
        while (iterator.hasNext()) {
            allErrors.appendLine(iterator.next())
            allErrors.appendLine("---")
        }
        pendingErrors.clear()

        return allErrors.toString()
    }

    private fun isIgnored(message: String): Boolean {
        return IGNORED_ERRORS.any { message.contains(it, ignoreCase = true) }
    }

    // For testing
    fun clearAll() {
        errorCounts.clear()
        pendingErrors.clear()
    }
}
