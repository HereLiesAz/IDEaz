package com.hereliesaz.ideaz.utils

import android.content.Context
import android.net.Uri
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

object BackupManager {
    private const val TAG = "BackupManager"

    suspend fun exportData(context: Context, uri: Uri): Boolean {
        return withContext(Dispatchers.IO) {
            try {
                val filesDir = context.filesDir
                context.contentResolver.openOutputStream(uri)?.use { outputStream ->
                    ZipOutputStream(outputStream).use { zipOut ->
                        filesDir.walkTopDown().forEach { file ->
                            if (file.isFile) {
                                val relativePath = file.relativeTo(filesDir).path
                                // Basic filtering
                                if (!shouldSkip(relativePath)) {
                                    val entry = ZipEntry(relativePath)
                                    zipOut.putNextEntry(entry)
                                    file.inputStream().use { input ->
                                        input.copyTo(zipOut)
                                    }
                                    zipOut.closeEntry()
                                }
                            }
                        }
                    }
                }
                true
            } catch (e: Exception) {
                Log.e(TAG, "Export failed", e)
                false
            }
        }
    }

    suspend fun importData(context: Context, uri: Uri): Boolean {
        return withContext(Dispatchers.IO) {
            try {
                val filesDir = context.filesDir
                context.contentResolver.openInputStream(uri)?.use { inputStream ->
                    ZipInputStream(inputStream).use { zipIn ->
                        var entry = zipIn.nextEntry
                        while (entry != null) {
                            val filePath = File(filesDir, entry.name)
                            // Zip Slip check
                            if (!filePath.canonicalPath.startsWith(filesDir.canonicalPath)) {
                                throw IOException("Zip entry is outside of the target dir: ${entry.name}")
                            }

                            if (entry.isDirectory) {
                                filePath.mkdirs()
                            } else {
                                filePath.parentFile?.mkdirs()
                                FileOutputStream(filePath).use { output ->
                                    zipIn.copyTo(output)
                                }
                            }
                            zipIn.closeEntry()
                            entry = zipIn.nextEntry
                        }
                    }
                }
                true
            } catch (e: Exception) {
                Log.e(TAG, "Import failed", e)
                false
            }
        }
    }

    private fun shouldSkip(path: String): Boolean {
        // Filter out common build artifacts and large dependencies
        // Keep git history
        return path.contains("/build/") ||
               path.contains("/.gradle/") ||
               path.startsWith("build/") ||
               path.startsWith(".gradle/") ||
               path.contains("/node_modules/") ||
               path.startsWith("node_modules/")
    }
}