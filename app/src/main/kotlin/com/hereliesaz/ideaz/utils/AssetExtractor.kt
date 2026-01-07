package com.hereliesaz.ideaz.utils

import android.content.Context
import java.io.File
import java.io.FileOutputStream
import java.io.IOException

object AssetExtractor {
    fun extractStdLib(context: Context) {
        val libDir = File(context.filesDir, "lib")
        if (!libDir.exists()) {
            if (!libDir.mkdirs()) {
                throw IOException("Failed to create directory: ${libDir.absolutePath}")
            }
        }

        val stdLibFile = File(libDir, "stdlib.jar")
        if (!stdLibFile.exists()) {
            try {
                context.assets.open("kotlin-stdlib-js.jar").use { inputStream ->
                    FileOutputStream(stdLibFile).use { outputStream ->
                        inputStream.copyTo(outputStream)
                    }
                }
            } catch (e: IOException) {
                e.printStackTrace()
                // Log failure but allow app to continue
            }
        }
    }
}
