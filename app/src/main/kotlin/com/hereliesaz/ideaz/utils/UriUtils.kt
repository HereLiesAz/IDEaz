package com.hereliesaz.ideaz.utils

import android.content.Context
import android.net.Uri
import android.os.Environment
import android.provider.DocumentsContract
import android.os.storage.StorageManager
import android.util.Log

object UriUtils {
    fun getPathFromUri(context: Context, uri: Uri): String? {
        try {
            if (DocumentsContract.isTreeUri(uri)) {
                val docId = DocumentsContract.getTreeDocumentId(uri)
                val parts = docId.split(":")
                if (parts.size >= 2) {
                    val type = parts[0]
                    val path = parts[1]

                    val storageManager = context.getSystemService(Context.STORAGE_SERVICE) as StorageManager
                    val storageVolumes = storageManager.storageVolumes

                    if (type == "primary") {
                        val primaryVolume = storageVolumes.find { it.isPrimary }
                        primaryVolume?.directory?.let { return "${it.absolutePath}/$path" }
                        // Fallback for safety on older devices or edge cases
                        @Suppress("DEPRECATION")
                        return "${Environment.getExternalStorageDirectory().absolutePath}/$path"
                    } else {
                        // Handle SD cards / Secondary volumes
                        val volume = storageVolumes.find { !it.isPrimary && it.uuid == type }
                        volume?.directory?.let { return "${it.absolutePath}/$path" }
                        // Fallback attempt for non-standard volumes
                        return "/storage/$type/$path"
                    }
                }
            }
        } catch (e: Exception) {
            Log.e("UriUtils", "Failed to get path from URI: $uri", e)
        }
        return null
    }
}
