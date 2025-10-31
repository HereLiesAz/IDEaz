package com.hereliesaz.ideaz.utils

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
                context.assets.open("tools/$toolName").use { inputStream ->
                    FileOutputStream(toolFile).use { outputStream ->
                        inputStream.copyTo(outputStream)
                    }
                }
            }
            val success = toolFile.setExecutable(true, false)
            android.util.Log.d("ToolManager", "Set executable for ${toolFile.absolutePath}: $success")

        }
    }

    fun getToolPath(context: Context, toolName: String): String {
        return File(getToolDir(context), toolName).absolutePath
    }

    private const val TOOL_DIR = "tools"

    private fun getToolDir(context: Context): File {
        val toolDir = File(context.filesDir, TOOL_DIR)
        android.util.Log.d("ToolManager", "Tool directory: ${toolDir.absolutePath}")
        return toolDir
    }
}