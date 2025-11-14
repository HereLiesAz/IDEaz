package com.hereliesaz.ideaz.utils

import android.content.Context
import android.os.Build
import android.util.Log
import java.io.File
import java.io.FileOutputStream
import java.io.IOException

enum class ToolType {
    NATIVE_BINARY, // For .so files in jniLibs, which are executable by the OS
    ASSET_FILE     // For regular files in assets (e.g., jars, keystores) that need to be extracted
}

data class Tool(
    val name: String,
    val type: ToolType,
    val assetPath: String // Path within assets, or just the name for native binaries
)

object ToolManager {

    private const val TAG = "ToolManager"

    // Central registry for all required tools.
    // All executables are now NATIVE_BINARY and will be loaded from jniLibs.
    // Non-executable files remain as ASSET_FILEs.
    private val tools = mapOf(
        "jules" to Tool("jules", ToolType.NATIVE_BINARY, "libjules.so"),
        "aapt2" to Tool("aapt2", ToolType.NATIVE_BINARY, "libaapt2.so"),
        "kotlinc" to Tool("kotlinc", ToolType.NATIVE_BINARY, "libkotlinc.so"),
        "d8" to Tool("d8", ToolType.NATIVE_BINARY, "libd8.so"),
        "apksigner" to Tool("apksigner", ToolType.NATIVE_BINARY, "libapksigner.so"),
        "java" to Tool("java", ToolType.NATIVE_BINARY, "libjava.so"),
        "debug.keystore" to Tool("debug.keystore", ToolType.ASSET_FILE, "debug.keystore"),
        "android.jar" to Tool("android.jar", ToolType.ASSET_FILE, "android.jar")
    )

    fun getToolPath(context: Context, toolName: String): String? {
        val tool = tools[toolName]
        if (tool == null) {
            Log.e(TAG, "Tool '$toolName' is not defined in the ToolManager registry.")
            return null
        }

        return when (tool.type) {
            ToolType.NATIVE_BINARY -> findNativeBinary(context, tool)
            ToolType.ASSET_FILE -> extractAndGetAssetPath(context, tool)
        }
    }

    private fun findNativeBinary(context: Context, tool: Tool): String? {
        val nativeLibDir = context.applicationInfo.nativeLibraryDir
        Log.d(TAG, "Searching for NATIVE tool '${tool.name}' in: $nativeLibDir")

        val supportedAbis = Build.SUPPORTED_ABIS
        for (abi in supportedAbis) {
            val file = File(nativeLibDir, "$abi/lib${tool.name}.so")
            if (file.exists() && file.canExecute()) {
                Log.d(TAG, "Found NATIVE tool at: ${file.absolutePath}")
                return file.absolutePath
            }
        }

        val fallbackFile = File(nativeLibDir, "lib${tool.name}.so")
        if (fallbackFile.exists() && fallbackFile.canExecute()) {
            Log.d(TAG, "Found NATIVE tool at fallback path: ${fallbackFile.absolutePath}")
            return fallbackFile.absolutePath
        }

        Log.e(TAG, "NATIVE tool '${tool.name}' not found or not executable in $nativeLibDir.")
        return null
    }

    private fun extractAndGetAssetPath(context: Context, tool: Tool): String? {
        val toolFile = File(context.filesDir, tool.name)
        Log.d(TAG, "Searching for ASSET tool '${tool.name}' at: ${toolFile.absolutePath}")

        if (toolFile.exists()) {
            Log.d(TAG, "ASSET tool '${tool.name}' already exists.")
            return toolFile.absolutePath
        }

        Log.d(TAG, "ASSET tool '${tool.name}' not found, extracting from assets...")
        try {
            context.assets.open(tool.assetPath).use { inputStream ->
                FileOutputStream(toolFile).use { outputStream ->
                    inputStream.copyTo(outputStream)
                }
            }
            // Note: We do not set executable flag here, as these are not meant to be executed.
            Log.d(TAG, "ASSET tool '${tool.name}' extracted successfully to ${toolFile.absolutePath}")
            return toolFile.absolutePath
        } catch (e: IOException) {
            Log.e(TAG, "Failed to extract ASSET tool '${tool.name}' from assets.", e)
            return null
        }
    }
}
