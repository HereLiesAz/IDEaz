package com.hereliesaz.peridiumide.services

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.hereliesaz.peridiumide.IBuildCallback
import com.hereliesaz.peridiumide.IBuildService
import com.hereliesaz.peridiumide.buildlogic.Aapt2Compile
import com.hereliesaz.peridiumide.buildlogic.Aapt2Link
import com.hereliesaz.peridiumide.buildlogic.ApkBuild
import com.hereliesaz.peridiumide.buildlogic.ApkSign
import com.hereliesaz.peridiumide.buildlogic.BuildOrchestrator
import com.hereliesaz.peridiumide.buildlogic.D8Compile
import com.hereliesaz.peridiumide.buildlogic.KotlincCompile
import com.hereliesaz.peridiumide.utils.ToolManager
import java.io.File

class BuildService : Service() {

    companion object {
        private const val NOTIFICATION_CHANNEL_ID = "PERIDIUM_BUILD_CHANNEL_ID"
        private const val NOTIFICATION_ID = 1
    }

    private val binder = object : IBuildService.Stub() {
        override fun startBuild(projectPath: String, callback: IBuildCallback) {
            this@BuildService.startBuild(projectPath, callback)
        }
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        ToolManager.extractTools(this)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val notification = NotificationCompat.Builder(this, NOTIFICATION_CHANNEL_ID)
            .setContentTitle("Peridium IDE")
            .setContentText("Build Service is running.")
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .build()

        startForeground(NOTIFICATION_ID, notification)

        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? {
        return binder
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                NOTIFICATION_CHANNEL_ID,
                "Peridium Build Service",
                NotificationManager.IMPORTANCE_DEFAULT
            )
            val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.createNotificationChannel(channel)
        }
    }

    private fun startBuild(projectPath: String, callback: IBuildCallback) {
        println("PeridiumBuildService: Received request to build project at $projectPath")

        val aapt2Path = ToolManager.getToolPath(this, "aapt2")
        val kotlincPath = ToolManager.getToolPath(this, "kotlinc")
        val d8Path = ToolManager.getToolPath(this, "d8")
        val apkSignerPath = ToolManager.getToolPath(this, "apksigner")
        val keystorePath = ToolManager.getToolPath(this, "debug.keystore")
        val keystorePass = "android"
        val keyAlias = "androiddebugkey"
        val androidJarPath = ToolManager.getToolPath(this, "android.jar")

        val buildDir = File(filesDir, "build")
        buildDir.deleteRecursively()
        buildDir.mkdirs()

        val compiledResDir = File(buildDir, "compiled_res").absolutePath
        val outputApkPath = File(buildDir, "app.apk").absolutePath
        val outputJavaPath = File(buildDir, "gen").absolutePath
        val classesDir = File(buildDir, "classes").absolutePath
        val finalApkPath = File(buildDir, "app-signed.apk").absolutePath

        val projectDir = File(projectPath)
        val resDir = File(projectDir, "app/src/main/res").absolutePath
        val manifestPath = File(projectDir, "app/src/main/AndroidManifest.xml").absolutePath
        val javaDir = File(projectDir, "app/src/main/java").absolutePath

        val buildOrchestrator = BuildOrchestrator(
            listOf(
                Aapt2Compile(aapt2Path, resDir, compiledResDir),
                Aapt2Link(aapt2Path, compiledResDir, androidJarPath, manifestPath, outputApkPath, outputJavaPath),
                KotlincCompile(kotlincPath, androidJarPath, javaDir, classesDir),
                D8Compile(d8Path, androidJarPath, classesDir, classesDir),
                ApkBuild(finalApkPath, outputApkPath, classesDir),
                ApkSign(apkSignerPath, keystorePath, keystorePass, keyAlias, finalApkPath)
            )
        )

        if (buildOrchestrator.execute()) {
            callback.onSuccess(finalApkPath)
        } else {
            callback.onFailure("Build failed")
        }
    }
}
