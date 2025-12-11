package com.hereliesaz.ideaz.utils

import android.content.Context
import android.util.Log
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream

/**
 * Manages the installation, retrieval, and deletion of external build tools.
 * Modularized: This manager now strictly looks for tools in a downloadable location.
 * It does NOT fallback to assets/nativeLibraryDir automatically.
 */
object ToolManager {

    private const val TAG = "ToolManager"
    private const val ROOT_DIR = "local_build_tools"

    // Subdirectories in the zip
    private const val TOOLS_SUBDIR = "tools"
    private const val NATIVE_SUBDIR = "native"

    // Mappings
    private val NATIVE_TOOLS = mapOf(
        "jules" to "libjules.so",
        "java" to "libjdk.so",
        "aapt2" to "libaapt2.so",
        "gemini" to "libgemini.so"
    )

    private val JAR_TOOLS = listOf(
        "android.jar",
        "debug.keystore",
        "kotlin-compiler.jar",
        "d8.jar",
        "apksigner.jar"
    )

    // --- ADDED THIS METHOD TO FIX COMPILATION ERROR ---
    fun init(context: Context) {
        val root = File(context.filesDir, ROOT_DIR)
        if (!root.exists()) {
            root.mkdirs()
        }

        if (areToolsInstalled(context)) {
            Log.i(TAG, "Local build tools verified.")
        } else {
            Log.w(TAG, "Local build tools missing or incomplete. Waiting for download.")
        }
    }

    /**
     * Checks if the local build tools are installed and intact.
     */
    fun areToolsInstalled(context: Context): Boolean {
        val root = File(context.filesDir, ROOT_DIR)
        if (!root.exists()) return false

        // Quick verification of critical files
        val toolsDir = File(root, TOOLS_SUBDIR)
        val nativeDir = File(root, NATIVE_SUBDIR)

        // Check a few key jars
        if (!File(toolsDir, "android.jar").exists()) return false
        if (!File(toolsDir, "d8.jar").exists()) return false

        // Check a few key binaries
        // Note: In downloadable mode, we look for the file inside the unzipped location
        // We use startsWith check or specific check depending on extraction logic
        if (!File(nativeDir, "libaapt2.so").exists()) return false

        return true
    }

    /**
     * Installs tools from a provided Zip file (downloaded extension).
     */
    fun installToolsFromZip(context: Context, zipFile: File): Boolean {
        Log.d(TAG, "Installing tools from ${zipFile.absolutePath}...")
        val root = File(context.filesDir, ROOT_DIR)

        // Clean start
        if (root.exists()) root.deleteRecursively()
        root.mkdirs()

        return try {
            unzip(zipFile, root)
            // Mark native binaries as executable
            val nativeDir = File(root, NATIVE_SUBDIR)
            nativeDir.listFiles()?.forEach { file ->
                file.setExecutable(true)
            }
            Log.d(TAG, "Tools installed successfully.")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to unzip tools", e)
            false
        }
    }

    /**
     * Deletes the local build tools.
     */
    fun deleteTools(context: Context) {
        val root = File(context.filesDir, ROOT_DIR)
        if (root.exists()) {
            root.deleteRecursively()
            Log.d(TAG, "Local build tools deleted.")
        }
    }

    /**
     * Resolves the absolute path to a specific tool.
     */
    fun getToolPath(context: Context, toolName: String): String? {
        val root = File(context.filesDir, ROOT_DIR)

        // 1. Check Native
        if (NATIVE_TOOLS.containsKey(toolName)) {
            val filename = NATIVE_TOOLS[toolName]!!
            val file = File(root, "$NATIVE_SUBDIR/$filename")
            return if (file.exists()) file.absolutePath else null
        }

        // 2. Check Jars/Assets
        if (JAR_TOOLS.contains(toolName)) {
            val file = File(root, "$TOOLS_SUBDIR/$toolName")
            return if (file.exists()) file.absolutePath else null
        }

        Log.e(TAG, "Tool $toolName unknown or not found in local modules.")
        return null
    }

    fun getJavaLibsPath(context: Context): String {
        return File(context.filesDir, "$ROOT_DIR/$TOOLS_SUBDIR/java_libs").absolutePath
    }

    // --- Private Utils ---

    private fun unzip(zipFile: File, targetDir: File) {
        ZipInputStream(zipFile.inputStream()).use { zis ->
            var entry: ZipEntry? = zis.nextEntry
            while (entry != null) {
                val file = File(targetDir, entry.name)
                // --- Prevent Zip Slip: ensure destination is within targetDir ---
                val destPath = file.toPath().normalize()
                val basePath = targetDir.toPath().normalize()
                if (!destPath.startsWith(basePath)) {
                    Log.w(TAG, "Skipping potentially malicious zip entry: ${entry.name}")
                    entry = zis.nextEntry
                    continue
                }
                if (entry.isDirectory) {
                    file.mkdirs()
                } else {
                    file.parentFile?.mkdirs()
                    FileOutputStream(file).use { fos ->
                        zis.copyTo(fos)
                    }
                }
                entry = zis.nextEntry
            }
        }
    }

    fun installTools(context: Context) {
        // No-op. We don't auto-install from assets anymore.
    }
}