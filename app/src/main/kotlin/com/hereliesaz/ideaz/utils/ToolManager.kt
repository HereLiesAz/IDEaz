package com.hereliesaz.ideaz.utils

import android.content.Context
import android.util.Log
import java.io.File
import java.io.FileOutputStream
import java.io.IOException

enum class ToolType {
    NATIVE, // Executable binary in jniLibs
    ASSET   // File in assets (like .jar or .keystore)
}

data class ToolInfo(val path: String, val type: ToolType)

object ToolManager {

    private const val TAG = "ToolManager"

    // --- THIS IS THE CORRECT MAP ---
    private val toolNameMap = mapOf(
        // NATIVE BINARIES (in jniLibs/arm64-v8a/lib<name>.so)
        "aapt2" to ToolInfo("aapt2", ToolType.NATIVE),
        "java" to ToolInfo("java", ToolType.NATIVE),
        "jules" to ToolInfo("jules", ToolType.NATIVE),

        // ASSET JARS (in assets/tools/ or assets/)
        "d8" to ToolInfo("tools/d8.jar", ToolType.ASSET),
        "apksigner" to ToolInfo("tools/apksigner.jar", ToolType.ASSET),
        "kotlinc" to ToolInfo("tools/kotlin-compiler.jar", ToolType.ASSET),
        "android.jar" to ToolInfo("android.jar", ToolType.ASSET),
        "debug.keystore" to ToolInfo("debug.keystore", ToolType.ASSET)
    )
    // --- END ---

    fun getToolPath(context: Context, toolName: String): String? {
        val toolInfo = toolNameMap[toolName] ?: run {
            Log.e(TAG, "Tool not found in map: $toolName")
            return null
        }

        return when (toolInfo.type) {
            ToolType.NATIVE -> getNativeToolPath(context, toolInfo.path)
            ToolType.ASSET -> getAssetToolPath(context, toolInfo.path)
        }
    }

    private fun getNativeToolPath(context: Context, toolName: String): String? {
        val nativeLibDir = context.applicationInfo.nativeLibraryDir
        Log.d(TAG, "Searching for NATIVE tool '$toolName' in: $nativeLibDir")

        val libName = "lib${toolName}.so"
        val toolFile = File(nativeLibDir, libName)

        if (toolFile.exists()) {
            if (!toolFile.canExecute()) {
                try {
                    toolFile.setExecutable(true)
                } catch (e: SecurityException) {
                    Log.e(TAG, "Failed to set NATIVE tool executable: ${toolFile.absolutePath}", e)
                    return null
                }
            }
            Log.d(TAG, "Found NATIVE tool at path: ${toolFile.absolutePath}")
            return toolFile.absolutePath
        }

        Log.e(TAG, "NATIVE tool '$toolName' (expected $libName) not found in $nativeLibDir.")
        return null
    }

    private fun getAssetToolPath(context: Context, assetPath: String): String? {
        val destFile = File(context.filesDir, assetPath)
        Log.d(TAG, "Searching for ASSET tool '$assetPath' at: ${destFile.absolutePath}")

        // --- FIX: Always overwrite assets ---
        // This ensures your real android.jar (API 36) is used.
        try {
            destFile.parentFile?.mkdirs()

            context.assets.open(assetPath).use { inputStream ->
                FileOutputStream(destFile).use { outputStream ->
                    inputStream.copyTo(outputStream)
                }
            }
            Log.d(TAG, "Successfully extracted/overwritten ASSET tool to: ${destFile.absolutePath}")
            return destFile.absolutePath
        } catch (e: IOException) {
            Log.e(TAG, "Failed to extract ASSET tool: $assetPath. Make sure the file exists in 'app/src/main/assets/$assetPath'", e)
            return null
        }
    }
}