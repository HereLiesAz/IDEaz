package com.hereliesaz.ideaz.utils

import android.content.Context
import java.io.File
import java.io.FileOutputStream

object ToolManager {

// Binaries that must be in jniLibs
private val NATIVE_BINARIES = mapOf(
"aapt2" to "libaapt2.so",
"d8" to "libd8.so",
"apksigner" to "libapksigner.so"
)

// Non-binary assets to be extracted
private val ASSET_FILES = listOf("kotlinc", "debug.keystore", "android.jar")

fun extractTools(context: Context) {
val assetDir = getAssetDir(context) // /files/tools
if (!assetDir.exists()) {
assetDir.mkdirs()
}

// Extract non-binary assets
ASSET_FILES.forEach { toolName ->
val toolFile = File(assetDir, toolName)
if (!toolFile.exists()) {
context.assets.open(toolName).use { inputStream ->
FileOutputStream(toolFile).use { outputStream ->
inputStream.copyTo(outputStream)
}
}
}
}

// No need to extract native binaries; the OS already did.
// We also don't need chmod, as nativeLibraryDir is already executable.
android.util.Log.d("ToolManager", "Asset extraction complete.")
}

fun getToolPath(context: Context, toolName: String): String {
return if (NATIVE_BINARIES.containsKey(toolName)) {
// Get path to executable native binary
val libName = NATIVE_BINARIES[toolName]
File(context.applicationInfo.nativeLibraryDir, libName).absolutePath
} else if (ASSET_FILES.contains(toolName)) {
// Get path to extracted asset
File(getAssetDir(context), toolName).absolutePath
} else {
throw IllegalArgumentException("Unknown tool: $toolName")
}
}

private const val ASSET_DIR = "tools"

private fun getAssetDir(context: Context): File {
// Use filesDir for non-executable assets like JARs and keystores
val assetDir = File(context.filesDir, ASSET_DIR)
android.util.Log.d("ToolManager", "Asset directory: ${assetDir.absolutePath}")
return assetDir
}
}