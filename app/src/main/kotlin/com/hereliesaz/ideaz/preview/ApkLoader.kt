package com.hereliesaz.ideaz.preview

import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.content.res.AssetManager
import android.content.res.Resources
import android.os.Build
import dalvik.system.DexClassLoader
import org.lsposed.hiddenapibypass.HiddenApiBypass
import java.io.File

object ApkLoader {
    data class LoadedApk(
        val classLoader: ClassLoader,
        val resources: Resources,
        val mainActivityClassName: String?,
        val themeId: Int
    )

    @SuppressLint("DiscouragedPrivateApi")
    fun loadApk(context: Context, apkPath: String): LoadedApk {
        val apkFile = File(apkPath)
        if (!apkFile.exists()) throw IllegalArgumentException("APK not found at: $apkPath")

        val optimizedDir = context.getDir("dex_opt", Context.MODE_PRIVATE)
        val classLoader = DexClassLoader(apkPath, optimizedDir.absolutePath, null, context.classLoader)

        val assetManager = AssetManager::class.java.newInstance()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            HiddenApiBypass.invoke(AssetManager::class.java, assetManager, "addAssetPath", apkPath)
        } else {
            val addAssetPath = AssetManager::class.java.getMethod("addAssetPath", String::class.java)
            addAssetPath.invoke(assetManager, apkPath)
        }

        val resources = Resources(assetManager, context.resources.displayMetrics, context.resources.configuration)

        val packageInfo = context.packageManager.getPackageArchiveInfo(apkPath, PackageManager.GET_ACTIVITIES)
            ?: throw IllegalStateException("Unable to parse APK manifest.")

        var mainActivityName = packageInfo.activities?.firstOrNull()?.name
        packageInfo.activities?.forEach {
            if (it.name.endsWith(".MainActivity")) mainActivityName = it.name
        }

        var theme = packageInfo.applicationInfo?.theme ?: 0
        if (theme == 0) theme = android.R.style.Theme_DeviceDefault

        return LoadedApk(classLoader, resources, mainActivityName, theme)
    }
}