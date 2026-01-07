package com.hereliesaz.ideaz.utils

import android.content.Context
import java.io.File
import java.io.FileOutputStream

object AssetExtractor {
    fun requireStdLib(context: Context): String {
        val libDir = File(context.filesDir, "lib")
        if (!libDir.exists()) libDir.mkdirs()

        val destFile = File(libDir, "kotlin-stdlib-js.jar")

        // Optimism: If it exists, it's probably fine.
        // Realism: You should probably check checksums, but we live dangerously.
        if (!destFile.exists()) {
            context.assets.open("kotlin-stdlib-js.jar").use { input ->
                FileOutputStream(destFile).use { output ->
                    input.copyTo(output)
                }
            }
        }
        return destFile.absolutePath
    }
}
