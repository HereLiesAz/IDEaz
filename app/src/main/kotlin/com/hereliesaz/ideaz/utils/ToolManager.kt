package com.hereliesaz.ideaz.utils

import android.content.Context
import android.util.Log
import java.io.File
import java.io.FileOutputStream
import java.io.IOException

object ToolManager {

    private const val TAG = "ToolManager"
    private const val TOOLS_DIR = "tools"

    // Mapping from the command-line tool name to its corresponding .so file name
    // These are now expected to be in the jniLibs folder, not assets.
    private val NATIVE_TOOLS = mapOf(
        "jules" to "libjules.so",
        "java" to "libjava.so",
        "aapt2" to "libaapt2.so",
        "kotlinc" to "libkotlinc.so",
        "d8" to "libd8.so",
        "apksigner" to "libapksigner.so",
        "gemini" to "libgemini.so"
    )

    // These are non-executable files that are still needed. We'll copy them from assets.
    private val ASSET_FILES = listOf(
        "android.jar",
        "debug.keystore"
    )

    /**
     * Copies non-native asset files from assets to the internal filesDir.
     * Native tools are expected to be installed by the system from jniLibs.
     */
    fun installTools(context: Context) {
        Log.d(TAG, "Checking and installing asset files...")
        val toolsDir = File(context.filesDir, TOOLS_DIR)
        if (!toolsDir.exists()) {
            toolsDir.mkdirs()
        }

        // Copy non-executable asset files from the assets directory.
        ASSET_FILES.forEach { assetName ->
            val assetPath = "tools/$assetName" // These are ABI-independent
            val destFile = File(toolsDir, assetName)
            copyAsset(context, assetPath, destFile)
        }
        Log.d(TAG, "Asset file installation complete.")
    }

    private fun copyAsset(context: Context, assetPath: String, destFile: File): Boolean {
        if (destFile.exists()) {
            return true // Already exists
        }
        Log.d(TAG, "Copying asset: $assetPath -> ${destFile.absolutePath}")
        try {
            context.assets.open(assetPath).use { input ->
                FileOutputStream(destFile).use { output ->
                    input.copyTo(output)
                }
            }
            return true
        } catch (e: IOException) {
            Log.e(TAG, "Failed to copy asset: $assetPath", e)
            return false
        }
    }

    /**
     * Gets the path to an installed tool.
     * For native tools, it looks in the native library directory.
     * For other assets, it looks in our custom tools directory.
     */
    fun getToolPath(context: Context, toolName: String): String? {
        // 1. Check if it's a native tool from jniLibs
        if (NATIVE_TOOLS.containsKey(toolName)) {
            val libName = NATIVE_TOOLS[toolName]!!
            val nativeLibDir = context.applicationInfo.nativeLibraryDir
            val toolFile = File(nativeLibDir, libName)
            return if (toolFile.exists() && toolFile.canExecute()) {
                toolFile.absolutePath
            } else {
                Log.e(TAG, "Native tool '${toolFile.absolutePath}' not found or not executable.")
                null
            }
        }

        // 2. Check if it's a non-native asset file we copied
        if (ASSET_FILES.contains(toolName)) {
            val toolFile = File(context.filesDir, "$TOOLS_DIR/$toolName")
            return if (toolFile.exists()) {
                toolFile.absolutePath
            } else {
                Log.e(TAG, "Asset file '$toolName' not found. Looked for: ${toolFile.absolutePath}")
                null
            }
        }

        Log.e(TAG, "Tool '$toolName' is not a known native tool or asset file.")
        return null
    }
}