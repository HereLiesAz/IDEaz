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

    private val toolNameMap = mapOf(
        // NATIVE BINARIES (must be in jniLibs/arm64-v8a and renamed to lib<name>.so)
        "aapt2" to ToolInfo("aapt2", ToolType.NATIVE),
        "java" to ToolInfo("java", ToolType.NATIVE),
        "jules" to ToolInfo("jules", ToolType.NATIVE),

        // ASSET FILES (will be copied from assets/ into filesDir)
        "d8" to ToolInfo("tools/d8.jar", ToolType.ASSET),
        "apksigner" to ToolInfo("tools/apksigner.jar", ToolType.ASSET),
        "kotlinc" to ToolInfo("tools/kotlin-compiler.jar", ToolType.ASSET),
        "android.jar" to ToolInfo("android.jar", ToolType.ASSET),
        "debug.keystore" to ToolInfo("debug.keystore", ToolType.ASSET)
    )

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
        val libName = "lib${toolName}.so"
        val toolFile = File(nativeLibDir, libName)

        // Try primary path
        if (toolFile.exists()) {
            if (!toolFile.canExecute()) toolFile.setExecutable(true)
            return toolFile.absolutePath
        }

        Log.e(TAG, "NATIVE tool '$toolName' (expected $libName) not found in $nativeLibDir.")
        return null
    }

    private fun getAssetToolPath(context: Context, assetPath: String): String? {
        val destFile = File(context.filesDir, assetPath)

        // --- CHANGED: Always overwrite ASSET tools ---
        // This ensures that if you update the file in assets, the app actually uses it
        // instead of the stale version in internal storage.
        try {
            destFile.parentFile?.mkdirs()
            context.assets.open(assetPath).use { inputStream ->
                FileOutputStream(destFile).use { outputStream ->
                    inputStream.copyTo(outputStream)
                }
            }
            // Set executable if it's a script (though we mostly use jars here)
            destFile.setExecutable(true)
            return destFile.absolutePath
        } catch (e: IOException) {
            Log.e(TAG, "Failed to extract ASSET tool: $assetPath", e)
            return null
        }
    }
}