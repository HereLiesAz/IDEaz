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
        // Get (and create if necessary) the directories for our tools.
        // The creation logic is handled within the getter functions.
        val assetDir = getAssetDir(context)
        val nativeToolDir = getNativeToolDir(context)

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

            val nativeLibDir = File(context.applicationInfo.nativeLibraryDir)
            val parentDir = nativeLibDir.parentFile
            var sourceFile: File? = null
            val checkedPaths = mutableListOf<String>()

            // 1. Check the root nativeLibraryDir itself. This is often the correct path.
            val rootFile = File(nativeLibDir, libName)
            checkedPaths.add(rootFile.absolutePath)
            if (rootFile.exists()) {
                sourceFile = rootFile
            }

            // 2. If not found, check ABI-specific subdirectories within the parent `lib` directory.
            // This is the standard location for native libraries on modern Android.
            if (sourceFile == null && parentDir != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                for (abi in Build.SUPPORTED_ABIS) {
                    val abiSpecificFile = File(parentDir, "$abi/$libName")
                    checkedPaths.add(abiSpecificFile.absolutePath)
                    if (abiSpecificFile.exists()) {
                        sourceFile = abiSpecificFile
                        android.util.Log.d("ToolManager", "Found native library for ABI $abi at ${abiSpecificFile.absolutePath}")
                        break // Found it
                    }
                }
            }

            // 3. Fallback for older Android versions or non-standard structures.
            if (sourceFile == null) {
                @Suppress("DEPRECATION")
                val cpuAbi = Build.CPU_ABI
                val oldStylePath = File(nativeLibDir, "$cpuAbi/$libName") // Tries path like `.../lib/arm64/arm-v7a/...`
                if (!checkedPaths.contains(oldStylePath.absolutePath)) {
                    checkedPaths.add(oldStylePath.absolutePath)
                    if (oldStylePath.exists()) {
                        sourceFile = oldStylePath
                    }
                }
            }


            if (sourceFile != null && sourceFile.exists()) {
                // Instead of copying the file, create a symbolic link to it in our tool directory.
                // This allows us to execute the tool from its original, guaranteed-executable location
                // while providing it at a consistent, expected path.
                try {
                    val destPath = java.nio.file.Paths.get(destFile.absolutePath)
                    // Check if a symlink already exists and points to the correct target.
                    if (java.nio.file.Files.isSymbolicLink(destPath)) {
                        val existingTarget = java.nio.file.Files.readSymbolicLink(destPath)
                        if (existingTarget == java.nio.file.Paths.get(sourceFile.absolutePath)) {
                            android.util.Log.d("ToolManager", "Symlink for $toolName already exists and is correct.")
                            return@forEach // Skip to the next tool
                        } else {
                            // If the link is incorrect, delete it before creating a new one.
                            android.util.Log.d("ToolManager", "Incorrect symlink for $toolName found. Deleting.")
                            if (!destFile.deleteRecursively()) {
                                throw java.io.IOException("Failed to delete incorrect symlink at ${destFile.absolutePath}")
                            }
                        }
                    } else if (destFile.exists()) {
                        // If a regular file exists at the destination, delete it.
                        android.util.Log.d("ToolManager", "File exists at symlink destination for $toolName. Deleting.")
                        if (!destFile.deleteRecursively()) {
                            throw java.io.IOException("Failed to delete existing file at ${destFile.absolutePath}")
                        }
                    }

                    java.nio.file.Files.createSymbolicLink(
                        destPath,
                        java.nio.file.Paths.get(sourceFile.absolutePath)
                    )
                    android.util.Log.d("ToolManager", "Created symlink for $toolName at ${destFile.absolutePath} -> ${sourceFile.absolutePath}")
                } catch (e: Exception) {
                    android.util.Log.e("ToolManager", "Failed to create symlink for $toolName", e)
                    throw java.io.IOException("Failed to create symbolic link for tool '$toolName': ${e.message}", e)
                }
            } else {
                val primaryAbi = Build.SUPPORTED_ABIS.getOrNull(0) ?: "unknown"
                val supportedAbis = Build.SUPPORTED_ABIS.joinToString(", ")
                val pathsChecked = checkedPaths.joinToString("\n - ")
                val errorMessage = "FATAL: Native library '$libName' not found for current device architecture ($primaryAbi). " +
                        "The device supports the following ABIs: [$supportedAbis]. " +
                        "Please ensure the APK includes a native library for one of these architectures. " +
                        "Checked paths:\n - $pathsChecked"
                android.util.Log.e("ToolManager", errorMessage)
                throw java.io.FileNotFoundException(errorMessage)
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
        val assetDir = File(context.filesDir, ASSET_DIR)
        createDirectoryIfNotExists(assetDir, "asset")
        return assetDir
    }

    private fun getNativeToolDir(context: Context): File {
        // Use filesDir as it's the last standard, reliable location to try for executable files.
        val toolDir = File(context.filesDir, NATIVE_TOOL_DIR)
        createDirectoryIfNotExists(toolDir, "native tool")
        return toolDir
    }

    private fun createDirectoryIfNotExists(directory: File, type: String) {
        if (!directory.exists()) {
            if (!directory.mkdirs()) {
                throw java.io.IOException("Failed to create $type directory: ${directory.absolutePath}")
            }
        }
    }
}