package com.hereliesaz.ideaz.services

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.hereliesaz.ideaz.IBuildCallback
import com.hereliesaz.ideaz.IBuildService
import com.hereliesaz.ideaz.MainActivity
import com.hereliesaz.ideaz.buildlogic.*
import com.hereliesaz.ideaz.utils.ApkInstaller
import com.hereliesaz.ideaz.utils.ToolManager
import java.io.File
import android.content.pm.PackageInstaller
import android.app.PendingIntent

class BuildService : Service() {

    companion object {
        private const val NOTIFICATION_CHANNEL_ID = "IDEAZ_BUILD_CHANNEL_ID"
        private const val NOTIFICATION_ID = 1

        // --- SDK versions from build.gradle.kts ---
        private const val MIN_SDK = 26
        private const val TARGET_SDK = 36
    }

    private val binder = object : IBuildService.Stub() {
        override fun startBuild(projectPath: String, callback: IBuildCallback) {
            this@BuildService.startBuild(projectPath, callback)
        }

        override fun updateNotification(message: String) {
            this@BuildService.updateNotification(message)
        }
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val notification = NotificationCompat.Builder(this, NOTIFICATION_CHANNEL_ID)
            .setContentTitle("IDEaz IDE")
            .setContentText("Build Service is running.")
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .build()

        startForeground(NOTIFICATION_ID, notification)

        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder {
        return binder
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                NOTIFICATION_CHANNEL_ID,
                "IDEaz Build Service",
                NotificationManager.IMPORTANCE_DEFAULT
            )
            val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.createNotificationChannel(channel)
        }
    }

    private fun updateNotification(message: String) {
        val notification = NotificationCompat.Builder(this, NOTIFICATION_CHANNEL_ID)
            .setContentTitle("IDEaz IDE")
            .setContentText(message)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .build()
        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.notify(NOTIFICATION_ID, notification)
    }

    private fun startBuild(projectPath: String, callback: IBuildCallback) {
        val projectDir = File(projectPath)
        val buildDir = File(filesDir, "build")
        buildDir.mkdirs()

        val cacheDir = File(filesDir, "cache")
        cacheDir.mkdirs()

        // Dependency Resolution
        val localRepoDir = File(filesDir, "local-repo")
        localRepoDir.mkdirs()
        val dependenciesFile = File(projectDir, "dependencies.txt")
        val resolver = DependencyResolver(projectDir, dependenciesFile, localRepoDir)
        val resolverResult = resolver.execute()
        if (!resolverResult.success) {
            callback.onFailure("Dependency resolution failed: ${resolverResult.output}")
            return
        }
        val classpath = resolverResult.output

        // Tool Paths (All are NATIVE tools now)
        val aapt2Path = ToolManager.getToolPath(this, "aapt2")
        val kotlincPath = ToolManager.getToolPath(this, "kotlinc")
        val d8Path = ToolManager.getToolPath(this, "d8")
        val apkSignerPath = ToolManager.getToolPath(this, "apksigner")
        val keystorePath = ToolManager.getToolPath(this, "debug.keystore")
        val keystorePass = "android"
        val keyAlias = "androiddebugkey"
        val androidJarPath = ToolManager.getToolPath(this, "android.jar")

        val requiredTools = mapOf(
            "aapt2" to aapt2Path,
            "kotlinc" to kotlincPath,
            "d8" to d8Path,
            "apksigner" to apkSignerPath,
            "debug.keystore" to keystorePath,
            "android.jar" to androidJarPath
        )

        for ((toolName, toolPath) in requiredTools) {
            if (toolPath == null) {
                callback.onFailure("Build failed: Required tool '$toolName' not found.")
                return
            }
        }

        // Build Directories
        val compiledResDir = File(buildDir, "compiled_res").absolutePath
        val outputApkPath = File(buildDir, "app.apk").absolutePath
        val outputJavaPath = File(buildDir, "gen").absolutePath
        val classesDir = File(buildDir, "classes").absolutePath
        val finalApkPath = File(buildDir, "app-signed.apk").absolutePath
        val resDir = File(projectDir, "app/src/main/res").absolutePath
        val manifestPath = File(projectDir, "app/src/main/AndroidManifest.xml").absolutePath
        val javaDir = File(projectDir, "app/src/main/java").absolutePath

        val buildOrchestrator = BuildOrchestrator(
            listOf(
                GenerateSourceMap(File(resDir), buildDir, cacheDir),
                // --- FIX: All constructors now match their definitions ---
                Aapt2Compile(aapt2Path!!, resDir, compiledResDir, MIN_SDK, TARGET_SDK),
                Aapt2Link(aapt2Path!!, compiledResDir, androidJarPath!!, manifestPath, outputApkPath, outputJavaPath, MIN_SDK, TARGET_SDK),
                KotlincCompile(kotlincPath!!, androidJarPath!!, javaDir, File(classesDir), classpath),
                D8Compile(d8Path!!, androidJarPath!!, classesDir, classesDir, classpath),
                ApkBuild(finalApkPath, outputApkPath, classesDir),
                ApkSign(apkSignerPath!!, keystorePath!!, keystorePass, keyAlias, finalApkPath)
                // --- END FIX ---
            )
        )

        val result = buildOrchestrator.execute(callback)
        if (result.success) {
            callback.onSuccess(finalApkPath)
            ApkInstaller.installApk(this, finalApkPath)
        } else {
            callback.onFailure(result.output)
        }
    }
}