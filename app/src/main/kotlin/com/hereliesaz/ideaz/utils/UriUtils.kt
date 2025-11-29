package com.hereliesaz.ideaz.utils

import android.content.Context
import android.net.Uri
import android.os.Environment
import android.provider.DocumentsContract
import android.os.storage.StorageManager
import java.io.File

object UriUtils {
    fun getPathFromUri(context: Context, uri: Uri): String? {
        try {
            if (DocumentsContract.isTreeUri(uri)) {
                val docId = DocumentsContract.getTreeDocumentId(uri)
                val parts = docId.split(":")
                if (parts.size >= 2) {
                    val type = parts[0]
                    val path = parts[1]

                    if (type == "primary") {
                        return Environment.getExternalStorageDirectory().absolutePath + "/" + path
                    } else {
                        // Handle SD cards / Secondary volumes
                        val storageManager = context.getSystemService(Context.STORAGE_SERVICE) as StorageManager
                        val storageVolumes = storageManager.storageVolumes
                        for (volume in storageVolumes) {
                            // volume.uuid is nullable
                            if (!volume.isPrimary && volume.uuid == type) {
                                val dir = volume.directory
                                if (dir != null) {
                                    return dir.absolutePath + "/" + path
                                }
                            }
                        }
                        // Fallback attempt
                        return "/storage/$type/$path"
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return null
    }
}
