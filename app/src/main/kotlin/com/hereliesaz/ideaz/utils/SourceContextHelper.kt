package com.hereliesaz.ideaz.utils

import java.io.File

object SourceContextHelper {

    data class ContextResult(
        val file: String = "",
        val line: Int = 0,
        val snippet: String = "",
        val isError: Boolean = false,
        val errorMessage: String? = null
    )

    /**
     * Resolves a source location for an inspected element.
     *
     * Currently supports the explicit `__source:filename:line__` tag emitted by
     * Web inspect-on-tap (DOM-attribute carried). The previous Android-resource-id
     * lookup path required a build-time source map; that generator was removed
     * along with the on-device toolchain (Phase 0, Task 6) and source-map
     * regeneration is scheduled for Phase 1's WebViewAssetLoader migration. For
     * any other resourceId we degrade to a no-context error so the caller can
     * fall back to plain element-data context without enrichment.
     */
    fun resolveContext(resourceId: String, projectDir: File): ContextResult {
        try {
            if (!resourceId.startsWith("__source:")) {
                return ContextResult("", 0, "", true, "No source-map data available for '$resourceId'")
            }

            // Format: __source:filename:line__
            val content = resourceId.removePrefix("__source:").removeSuffix("__")
            val lastColonIndex = content.lastIndexOf(':')
            if (lastColonIndex == -1) {
                return ContextResult("", 0, "", true, "Invalid source tag format")
            }

            val fileName = content.substring(0, lastColonIndex)
            val lineStr = content.substring(lastColonIndex + 1)
            val lineNumber = lineStr.toIntOrNull() ?: 0

            // Resolve relative path against projectDir
            val filePath = File(projectDir, fileName).absolutePath
            val file = File(filePath)
            if (!file.exists()) {
                return ContextResult(filePath, lineNumber, "", true, "Source file not found: $filePath")
            }

            val lines = file.readLines()
            val lineIndex = if (lineNumber > 0) lineNumber - 1 else 0
            val snippet = lines.getOrNull(lineIndex)?.trim() ?: "Code not found"
            return ContextResult(filePath, lineNumber, snippet)

        } catch (e: Exception) {
            return ContextResult("", 0, "", true, "Error resolving context: ${e.message}")
        }
    }
}
