package com.hereliesaz.ideaz.utils

import android.content.Context
import java.io.File
import java.io.FileOutputStream

object ToolManager {

    private const val ASSET_DIR = "tools"
    private const val NATIVE_TOOL_DIR = "native_tools"

    // Binaries that must be in jniLibs
    private val NATIVE_BINARIES = mapOf(
        "aapt2" to "libaapt2.so",
        "d8" to "libd8.so",
        "apksigner" to "libapksigner.so"
    )

    // Non-binary assets to be extracted
    private val ASSET_FILES = listOf("kotlinc", "debug.keystore", "android.jar")

    fun extractTools(context: Context) {
        val assetDir = getAssetDir(context)
        if (!assetDir.exists()) assetDir.mkdirs()

        val nativeToolDir = getNativeToolDir(context)
        if (!nativeToolDir.exists()) nativeToolDir.mkdirs()

        // Extract non-binary assets
        ASSET_FILES.forEach { fileName ->
            val toolFile = File(assetDir, fileName)
            if (!toolFile.exists()) {
                context.assets.open(fileName).use { inputStream ->
                    FileOutputStream(toolFile).use { outputStream ->
                        inputStream.copyTo(outputStream)
                    }
                }
            }
        }

        // Copy native binaries from nativeLibraryDir to a known, executable location
        NATIVE_BINARIES.forEach { (toolName, libName) ->
            val destFile = File(nativeToolDir, toolName)
            // Always overwrite to ensure the latest version is used
            val sourceFile = File(context.applicationInfo.nativeLibraryDir, libName)
            if (sourceFile.exists()) {
                sourceFile.copyTo(destFile, overwrite = true)
                destFile.setExecutable(true, true)
            } else {
                android.util.Log.e("ToolManager", "Native library not found: ${sourceFile.absolutePath}")
            }
        }
        android.util.Log.d("ToolManager", "Tool extraction complete.")
    }

    fun getToolPath(context: Context, toolName: String): String {
        return when (toolName) {
            in NATIVE_BINARIES -> {
                // Get path to our copied, executable native binary
                val toolFile = File(getNativeToolDir(context), toolName)
                if (!toolFile.exists() || !toolFile.canExecute()) {
                     // Attempt a re-extraction if the file is missing or not executable
                    extractTools(context)
                }
                toolFile.absolutePath
            }
            in ASSET_FILES -> {
                // Get path to extracted asset
                File(getAssetDir(context), toolName).absolutePath
            }
            else -> {
                val validTools = (NATIVE_BINARIES.keys + ASSET_FILES).joinToString(", ")
                throw IllegalArgumentException("Unknown tool: $toolName. Valid tool names are: $validTools")
            }
        }
    }

    private fun getAssetDir(context: Context): File {
        return File(context.filesDir, ASSET_DIR)
    }

    private fun getNativeToolDir(context: Context): File {
        // Use cacheDir for executables to avoid noexec restrictions on filesDir
        return File(context.cacheDir, NATIVE_TOOL_DIR)
    }
}