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
                val out = context.contentResolver.openOutputStream(uri) ?: return@withContext false
                out.use { outputStream ->
                    ZipOutputStream(outputStream).use { zipOut ->
                        filesDir.walkTopDown().forEach { file ->
                            if (file.isFile) {
                                val relativePath = file.relativeTo(filesDir).path
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
            } catch (e: IOException) {
                Log.e(TAG, "Export failed", e)
                return@withContext false
            }
            return@withContext true
        }
    }

    suspend fun importData(context: Context, uri: Uri): Boolean {
        return withContext(Dispatchers.IO) {
            try {
                val filesDir = context.filesDir
                val `in` = context.contentResolver.openInputStream(uri) ?: return@withContext false
                `in`.use { inputStream ->
                    ZipInputStream(inputStream).use { zipIn ->
                        var entry = zipIn.nextEntry
                        while (entry != null) {
                            val filePath = File(filesDir, entry.name)
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
            } catch (e: IOException) {
                Log.e(TAG, "Import failed", e)
                return@withContext false
            }
            return@withContext true
        }
    }

    private fun shouldSkip(path: String): Boolean {
        return false
    }
}
