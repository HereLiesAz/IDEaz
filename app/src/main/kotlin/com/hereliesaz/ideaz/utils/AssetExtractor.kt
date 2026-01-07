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
                context.assets.open("kotlin-stdlib-js.jar").use { input ->
                    FileOutputStream(destFile).use { output ->
                        input.copyTo(output)
                    }
                }
                // Update stored version only on success
                prefs.edit().putLong(KEY_ASSET_VERSION, currentVersion).apply()
            } catch (e: Exception) {
                e.printStackTrace()
                // If extraction fails, we might still return the path if file exists,
                // but if it doesn't exist, the app will likely crash later.
                // We assume reliable assets.
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
