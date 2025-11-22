package com.hereliesaz.ideaz.buildlogic

import java.io.File
import java.security.MessageDigest

object BuildCacheManager {

    fun shouldSkip(taskName: String, inputFiles: List<File>, outputDir: File): Boolean {
        val snapshotFile = File(outputDir, "$taskName.snapshot")
        if (!snapshotFile.exists()) return false
        if (!outputDir.exists() || (outputDir.isDirectory && outputDir.list().isNullOrEmpty())) return false

        val currentHash = computeHash(inputFiles)
        val savedHash = snapshotFile.readText()

        return currentHash == savedHash
    }

    fun updateSnapshot(taskName: String, inputFiles: List<File>, outputDir: File) {
        if (!outputDir.exists()) outputDir.mkdirs()
        val snapshotFile = File(outputDir, "$taskName.snapshot")
        val currentHash = computeHash(inputFiles)
        snapshotFile.writeText(currentHash)
    }

    private fun computeHash(files: List<File>): String {
        val digest = MessageDigest.getInstance("MD5")
        // Sort files to ensure consistent order
        files.sortedBy { it.absolutePath }.forEach { file ->
            // Use lastModified and length for speed.
            // This is sensitive to 'touch', but robust enough for simple edit-compile cycles.
            val meta = "${file.absolutePath}:${file.lastModified()}:${file.length()}"
            digest.update(meta.toByteArray())
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }
}
