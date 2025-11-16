package com.hereliesaz.ideaz.utils

import android.content.Context
import android.os.Build
import android.util.Log
import java.io.File
import java.io.FileOutputStream
import java.io.IOException

object ToolManager {

    private const val TAG = "ToolManager"
    private const val TOOLS_DIR = "tools"

    // --- FIX: All tools are native binaries ---
    private val NATIVE_TOOLS = mapOf(
        // Tool Name -> Asset File Name
        "jules" to "libjules.so",
        "java" to "libjava.so",
        "aapt2" to "libaapt2.so",
        "kotlinc" to "libkotlinc.so",
        "d8" to "libd8.so",
        "apksigner" to "libapksigner.so",
        "gemini" to "libgemini.so"
    )
    // These are non-executable files
    private val ASSET_FILES = listOf(
        "android.jar",
        "debug.keystore"
    )

    /**
     * Copies all tools from assets to the internal filesDir to make them executable.
     */
    fun installTools(context: Context) {
        Log.d(TAG, "Checking and installing tools...")
        val toolsDir = File(context.filesDir, TOOLS_DIR)
        if (!toolsDir.exists()) {
            toolsDir.mkdirs()
        }

        val abi = Build.SUPPORTED_ABIS[0] // e.g., arm64-v8a
        Log.d(TAG, "Installing tools for ABI: $abi")

        // 1. Copy Native Executables
        NATIVE_TOOLS.forEach { (toolName, assetName) ->
            val assetPath = "tools/$abi/$assetName"
            val destFile = File(toolsDir, toolName) // Rename lib<name>.so to <name>
            copyAndMakeExecutable(context, assetPath, destFile)
        }

        // 2. Copy other asset files
        ASSET_FILES.forEach { assetName ->
            val assetPath = "tools/$assetName" // ABI-independent
            val destFile = File(toolsDir, assetName)
            copyAsset(context, assetPath, destFile)
        }
        Log.d(TAG, "Tool installation complete.")
    }

    private fun copyAndMakeExecutable(context: Context, assetPath: String, destFile: File) {
        if (copyAsset(context, assetPath, destFile)) {
            try {
                destFile.setExecutable(true, true)
                Log.d(TAG, "Made executable: ${destFile.name}")
            } catch (e: SecurityException) {
                Log.e(TAG, "Failed to make executable: ${destFile.name}", e)
            }
        }
    }

    private fun copyAsset(context: Context, assetPath: String, destFile: File): Boolean {
        if (destFile.exists()) {
            // Log.d(TAG, "Tool already exists: ${destFile.name}")
            return true // Already exists
        }
        Log.d(TAG, "Copying tool from assets: $assetPath -> ${destFile.absolutePath}")
        try {
            context.assets.open(assetPath).use { input ->
                FileOutputStream(destFile).use { output ->
                    input.copyTo(output)
                }
            }
            return true
        } catch (e: IOException) {
            Log.e(TAG, "Failed to copy tool: $assetPath", e)
            return false
        }
    }

    /**
     * Gets the path to an installed tool.
     */
    fun getToolPath(context: Context, toolName: String): String? {
        val file = File(context.filesDir, "$TOOLS_DIR/$toolName")

        if (file.exists()) {
            if (NATIVE_TOOLS.containsKey(toolName)) {
                if (file.canExecute()) {
                    return file.absolutePath
                } else {
                    Log.e(TAG, "Tool '$toolName' exists but is not executable.")
                }
            } else {
                // It's a non-executable asset file like android.jar
                return file.absolutePath
            }
        }

        Log.e(TAG, "Tool '$toolName' not found. Did installTools() run? Looked for: ${file.absolutePath}")
        return null
    }
}