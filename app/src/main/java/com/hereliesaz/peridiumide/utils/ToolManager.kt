package com.hereliesaz.peridiumide.utils

import android.content.Context
import java.io.File
import java.io.FileOutputStream

object ToolManager {

    private val TOOLS = listOf("aapt2", "apksigner", "d8", "kotlinc", "debug.keystore", "android.jar")

    fun extractTools(context: Context) {
        val toolDir = getToolDir(context)
        if (!toolDir.exists()) {
            toolDir.mkdirs()
        }

        TOOLS.forEach { toolName ->
            val toolFile = File(toolDir, toolName)
            if (!toolFile.exists()) {
                context.assets.open(toolName).use { inputStream ->
                    FileOutputStream(toolFile).use { outputStream ->
                        inputStream.copyTo(outputStream)
                    }
                }
                toolFile.setExecutable(true, false)
            }
        }
    }

    fun getToolPath(context: Context, toolName: String): String {
        return File(getToolDir(context), toolName).absolutePath
    }

    private fun getToolDir(context: Context): File {
        return File(context.filesDir, "tools")
    }
}
