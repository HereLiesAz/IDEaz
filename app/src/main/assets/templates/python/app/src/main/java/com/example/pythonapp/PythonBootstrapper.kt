package com.example.pythonapp

import android.content.Context
import android.util.Log
import com.chaquo.python.Python
import com.chaquo.python.android.AndroidPlatform
import java.io.File
import java.io.FileOutputStream
import java.io.IOException

object PythonBootstrapper {
    private const val TAG = "PythonBootstrapper"

    fun initialize(context: Context) {
        if (Python.isStarted()) {
            Log.d(TAG, "Python already started")
            return
        }

        try {
            val pythonDir = File(context.filesDir, "python")
            if (!pythonDir.exists()) {
                Log.d(TAG, "Extracting Python assets...")
                extractAssets(context, "python", pythonDir)
            } else {
                // TODO: Version check or force update?
                // For now, we assume if it exists, it's good.
                // Ideally, compare timestamps or versions.
                // Re-extracting every time is slow.
            }

            // Set environment variables
            // Note: Standard Chaquopy sets this up via AndroidPlatform,
            // but since we are manually bootstrapping, we might need to be explicit.
            try {
                // os.setenv is not directly available in standard Java without JNI or reflection
                // However, AndroidPlatform handles this internally usually.
                // We will rely on AndroidPlatform(context) to find the assets.
                // If it fails, we might need to hack the env.
            } catch (e: Exception) {
                Log.w(TAG, "Failed to set env vars", e)
            }

            Log.d(TAG, "Loading native libraries...")
            // Load Chaquopy Java bridge
            System.loadLibrary("chaquopy_java")

            // Note: libpython3.x.so is usually loaded by chaquopy_java,
            // but we ensure it's available in the lib path.

            Log.d(TAG, "Initializing Python...")
            val platform = AndroidPlatform(context)
            Python.start(platform)
            Log.d(TAG, "Python initialized successfully")

        } catch (e: Exception) {
            Log.e(TAG, "Failed to initialize Python", e)
            throw RuntimeException("Failed to initialize Python", e)
        }
    }

    private fun extractAssets(context: Context, assetPath: String, destDir: File) {
        val assets = context.assets.list(assetPath) ?: return
        if (assets.isEmpty()) {
            // It might be a file or empty dir. Try to copy as file.
            copyAsset(context, assetPath, destDir)
        } else {
            // It's a directory
            if (!destDir.exists()) destDir.mkdirs()
            for (asset in assets) {
                val subAssetPath = if (assetPath.isEmpty()) asset else "$assetPath/$asset"
                val subDestDir = File(destDir, asset)

                // Recursively check if subAssetPath is dir or file
                val subAssets = context.assets.list(subAssetPath)
                if (subAssets != null && subAssets.isNotEmpty()) {
                     extractAssets(context, subAssetPath, subDestDir)
                } else {
                    // Try to open as file
                    try {
                        context.assets.open(subAssetPath).close()
                         // It is a file
                         copyAsset(context, subAssetPath, subDestDir) // subDestDir here acts as target file
                    } catch (e: IOException) {
                        // It's an empty directory
                        subDestDir.mkdirs()
                    }
                }
            }
        }
    }

    private fun copyAsset(context: Context, assetPath: String, destFile: File) {
        try {
            destFile.parentFile?.mkdirs()
            context.assets.open(assetPath).use { input ->
                FileOutputStream(destFile).use { output ->
                    input.copyTo(output)
                }
            }
        } catch (e: IOException) {
            Log.e(TAG, "Failed to copy asset: $assetPath", e)
        }
    }
}
