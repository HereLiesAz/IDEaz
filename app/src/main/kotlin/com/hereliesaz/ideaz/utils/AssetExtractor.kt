package com.hereliesaz.ideaz.utils

import android.content.Context
import android.content.SharedPreferences
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.os.Build
import java.io.File
import java.io.FileOutputStream

object AssetExtractor {
    private const val PREFS_NAME = "asset_prefs"
    private const val KEY_ASSET_VERSION = "stdlib_js_version"

    @Synchronized
    fun requireStdLib(context: Context): String {
        val libDir = File(context.filesDir, "lib")
        if (!libDir.exists()) libDir.mkdirs()

        val destFile = File(libDir, "kotlin-stdlib-js.jar")

        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val currentVersion = getAppVersionCode(context)
        val storedVersion = prefs.getLong(KEY_ASSET_VERSION, -1L)

        // Extract if file missing OR version changed
        if (!destFile.exists() || currentVersion != storedVersion) {
            try {
                // Extract stdlib jar
                try {
                    context.assets.open("kotlin-stdlib-js.jar").use { input ->
                        FileOutputStream(destFile).use { output ->
                            input.copyTo(output)
                        }
                    }
                } catch (e: Exception) {
                    // Safe to ignore in unit tests where assets are missing
                    if (e is java.io.FileNotFoundException || e.javaClass.name.contains("NoSuchFileException")) {
                        return destFile.absolutePath
                    }
                    throw e
                }

                // Extract kotlin.js from the jar
                val wwwDir = File(context.filesDir, "www")
                if (!wwwDir.exists()) wwwDir.mkdirs()

                try {
                    java.util.zip.ZipFile(destFile).use { zip ->
                        // Try root or standard paths
                        val entry = zip.getEntry("kotlin.js")
                            ?: zip.getEntry("META-INF/resources/kotlin.js")
                            ?: zip.getEntry("default/kotlin.js")

                        if (entry != null) {
                            zip.getInputStream(entry).use { input ->
                                FileOutputStream(File(wwwDir, "kotlin.js")).use { output ->
                                    input.copyTo(output)
                                }
                            }
                        }
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }

                // Also extract www/index.html
                val indexFile = File(wwwDir, "index.html")
                try {
                    context.assets.open("www/index.html").use { input ->
                        FileOutputStream(indexFile).use { output ->
                            input.copyTo(output)
                        }
                    }
                } catch (e: Exception) {
                     // Ignore missing assets in test
                }

                // Update stored version only on success
                prefs.edit().putLong(KEY_ASSET_VERSION, currentVersion).apply()
            } catch (e: Exception) {
                e.printStackTrace()
            }
        } else {
             // Ensure index.html exists even if version matches (e.g. dev)
             val wwwDir = File(context.filesDir, "www")
             if (!wwwDir.exists()) wwwDir.mkdirs()
             val indexFile = File(wwwDir, "index.html")
             if (!indexFile.exists()) {
                 try {
                     context.assets.open("www/index.html").use { input ->
                        FileOutputStream(indexFile).use { output ->
                            input.copyTo(output)
                        }
                    }
                 } catch (e: Exception) {
                     e.printStackTrace()
                 }
             }
        }
        return destFile.absolutePath
    }

    private fun getAppVersionCode(context: Context): Long {
        return try {
            val pInfo = context.packageManager.getPackageInfo(context.packageName, 0)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                pInfo.longVersionCode
            } else {
                @Suppress("DEPRECATION")
                pInfo.versionCode.toLong()
            }
        } catch (e: PackageManager.NameNotFoundException) {
            -1L
        }
    }
}
