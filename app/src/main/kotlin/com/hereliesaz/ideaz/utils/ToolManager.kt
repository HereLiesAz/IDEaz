package com.hereliesaz.ideaz.utils

import android.content.Context
import android.os.Build
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

            // The native library isn't always at the root of nativeLibraryDir.
            // It's often in a subdirectory named after the ABI.
            // We'll construct the path using the primary ABI.
            val primaryAbi = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                Build.SUPPORTED_ABIS[0]
            } else {
                @Suppress("DEPRECATION")
                Build.CPU_ABI
            }

            val nativeLibDir = File(context.applicationInfo.nativeLibraryDir)
            var sourceFile = File(nativeLibDir, libName) // Check original path first

            if (!sourceFile.exists()) {
                // If not found, try constructing path with primary ABI from the parent directory.
                // This handles cases where nativeLibraryDir points to `/lib/arm64` but we need `/lib/arm64-v8a`
                val parentDir = nativeLibDir.parentFile
                if (parentDir != null) {
                    val abiSpecificFile = File(parentDir, "$primaryAbi/$libName")
                    if (abiSpecificFile.exists()) {
                        sourceFile = abiSpecificFile
                    }
                }
            }

            if (sourceFile.exists()) {
                sourceFile.copyTo(destFile, overwrite = true)
                destFile.setExecutable(true, true)
            } else {
                 android.util.Log.e("ToolManager", "Native library not found: $libName in ${nativeLibDir.absolutePath} or for ABI $primaryAbi")
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