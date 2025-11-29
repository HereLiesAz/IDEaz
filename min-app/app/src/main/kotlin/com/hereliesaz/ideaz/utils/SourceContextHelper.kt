package com.hereliesaz.ideaz.utils

import com.hereliesaz.ideaz.models.SourceMapEntry
import java.io.File

object SourceContextHelper {

    data class ContextResult(
        val file: String = "",
        val line: Int = 0,
        val snippet: String = "",
        val isError: Boolean = false,
        val errorMessage: String? = null
    )

    fun resolveContext(resourceId: String, projectDir: File, sourceMap: Map<String, SourceMapEntry>): ContextResult {
        try {
            var filePath: String? = null
            var lineNumber: Int = 0

            if (resourceId.startsWith("__source:")) {
                // Format: __source:filename:line__
                val content = resourceId.removePrefix("__source:").removeSuffix("__")
                val lastColonIndex = content.lastIndexOf(':')

                if (lastColonIndex != -1) {
                    val fileName = content.substring(0, lastColonIndex)
                    val lineStr = content.substring(lastColonIndex + 1)
                    lineNumber = lineStr.toIntOrNull() ?: 0

                    // Resolve relative path against projectDir
                    filePath = File(projectDir, fileName).absolutePath
                } else {
                    return ContextResult("", 0, "", true, "Invalid source tag format")
                }
            } else {
                // Standard Android ID
                // Strip package/type (e.g., com.example:id/button1 -> button1)
                val simpleId = resourceId.substringAfterLast("/")

                val entry = sourceMap[simpleId]
                if (entry != null) {
                    filePath = entry.file
                    lineNumber = entry.line
                } else {
                    return ContextResult("", 0, "", true, "Element ID '$simpleId' not found in source map")
                }
            }

            if (filePath != null) {
                val file = File(filePath)
                if (file.exists()) {
                    val lines = file.readLines()
                    val lineIndex = if (lineNumber > 0) lineNumber - 1 else 0
                    val snippet = lines.getOrNull(lineIndex)?.trim() ?: "Code not found"
                    return ContextResult(filePath, lineNumber, snippet)
                } else {
                    return ContextResult(filePath, lineNumber, "", true, "Source file not found: $filePath")
                }
            }

            return ContextResult("", 0, "", true, "Unknown error")

        } catch (e: Exception) {
            return ContextResult("", 0, "", true, "Error resolving context: ${e.message}")
        }
    }
}
