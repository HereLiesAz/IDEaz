package com.hereliesaz.peridiumide.utils

import android.content.Context
import java.io.File
import java.io.FileOutputStream

object ToolManager {

    private const val TOOLS_DIR = "tools"

    fun getToolPath(context: Context, toolName: String): String {
        return File(getToolsDir(context), toolName).absolutePath
    }

    fun extractTools(context: Context) {
        val toolsDir = getToolsDir(context)
        if (toolsDir.exists()) {
            // Assume tools are already extracted
            return
        }
        toolsDir.mkdirs()
        val assets = context.assets
        assets.list("")?.forEach { toolName ->
            val toolFile = File(toolsDir, toolName)
            assets.open(toolName).use { inStream ->
                FileOutputStream(toolFile).use { outStream ->
                    inStream.copyTo(outStream)
                }
            }
            toolFile.setExecutable(true)
        }
    }

    private fun getToolsDir(context: Context): File {
        return File(context.filesDir, TOOLS_DIR)
    }
}
