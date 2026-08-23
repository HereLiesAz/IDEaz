package com.hereliesaz.ideaz.utils

import kotlin.math.max

object VersionUtils {

    /**
     * Extracts version string from a filename like "IDEaz-1.5.0.1012-debug.apk".
     * Returns null if no version found.
     */
    fun extractVersionFromFilename(filename: String): String? {
        // Regex to match version numbers separated by dots, surrounded by hyphens or start/end
        // Example: IDEaz-1.5.0.1012-debug.apk -> matches 1.5.0.1012
        // We look for a pattern like -X.Y.Z- or -X.Y.Z.W- inside the name
        val regex = Regex("[-_](\\d+(\\.\\d+)+)[-_]")
        // Attempt 1: try to match bounded version first
        val match = regex.find(filename)
        if (match != null) {
            return match.groupValues[1]
        }

        // Fallback: try strict pattern matching the whole structure if possible
        // But the user format is fairly consistent: Name-Version-Type.apk
        val regexStrict = Regex(".*-(\\d+(\\.\\d+)+)-.*\\.apk")
        return regexStrict.find(filename)?.groupValues?.get(1)
    }

    /**
     * Compares two version strings (e.g. "1.5.0" vs "1.4.9").
     * Returns > 0 if v1 > v2, < 0 if v1 < v2, 0 if equal.
     */
    fun compareVersions(v1: String, v2: String): Int {
        val parts1 = v1.split(".").map { it.toIntOrNull() ?: 0 }
        val parts2 = v2.split(".").map { it.toIntOrNull() ?: 0 }
        val length = max(parts1.size, parts2.size)
        for (i in 0 until length) {
            val p1 = parts1.getOrElse(i) { 0 }
            val p2 = parts2.getOrElse(i) { 0 }
            if (p1 != p2) return p1 - p2
        }
        return 0
    }
}
