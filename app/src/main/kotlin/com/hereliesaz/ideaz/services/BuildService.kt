package com.hereliesaz.ideaz.services

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.hereliesaz.ideaz.IBuildCallback
import com.hereliesaz.ideaz.IBuildService
import com.hereliesaz.ideaz.buildlogic.*
import com.hereliesaz.ideaz.utils.ApkInstaller
import com.hereliesaz.ideaz.utils.ToolManager
import com.hereliesaz.ideaz.utils.ProjectAnalyzer
import com.hereliesaz.ideaz.models.ProjectType
import com.hereliesaz.ideaz.ui.web.WebRuntimeActivity
import java.io.File
import java.util.ArrayDeque

class BuildService : Service() {
    companion object {
        private const val NOTIFICATION_CHANNEL_ID = "IDEAZ_BUILD_CHANNEL_ID"
        private const val NOTIFICATION_ID = 1
        private const val MIN_SDK = 26
        private const val TARGET_SDK = 36
        private const val MAX_LOG_LINES = 5
    }

    // Buffer to hold the last 5 lines of logs
    private val logBuffer = ArrayDeque<String>(MAX_LOG_LINES)

    private val binder = object : IBuildService.Stub() {
        override fun startBuild(projectPath: String, callback: IBuildCallback) = this@BuildService.startBuild(projectPath, callback)
        override fun updateNotification(message: String) = this@BuildService.updateNotification(message)
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

    override fun onBind(intent: Intent?): IBinder = binder

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(NOTIFICATION_CHANNEL_ID, "IDEaz Build Service", NotificationManager.IMPORTANCE_DEFAULT)
            getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }
    }

    private fun updateNotification(message: String) {
        // Synchronize access to the buffer to handle potential threading issues from AIDL calls
        synchronized(logBuffer) {
            // Split message by newlines in case a chunk contains multiple lines
            message.lines().forEach { line ->
                if (line.isNotBlank()) {
                    if (logBuffer.size >= MAX_LOG_LINES) {
                        logBuffer.removeFirst()
                    }
                    logBuffer.addLast(line)
                }
            }
        }

        val latestLog = synchronized(logBuffer) { logBuffer.lastOrNull() } ?: "Processing..."
        val bigText = synchronized(logBuffer) { logBuffer.joinToString("\n") }

        val notification = NotificationCompat.Builder(this, NOTIFICATION_CHANNEL_ID)
            .setContentTitle("IDEaz IDE")
            .setContentText(latestLog) // Collapsed state shows the single most recent line
            .setStyle(NotificationCompat.BigTextStyle().bigText(bigText)) // Expanded state shows the buffer (up to 5 lines)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setOnlyAlertOnce(true) // Prevents sound/vibration on every log update
            .build()

        getSystemService(NotificationManager::class.java).notify(NOTIFICATION_ID, notification)
    }

    private fun startBuild(projectPath: String, callback: IBuildCallback) {
        // Clear buffer at start of new build
        synchronized(logBuffer) {
            logBuffer.clear()
        }
        updateNotification("Starting build...")

        val projectDir = File(projectPath)
        val buildDir = File(filesDir, "build").apply { mkdirs() }
        val cacheDir = File(filesDir, "cache").apply { mkdirs() }
        val localRepoDir = File(filesDir, "local-repo").apply { mkdirs() }

        val type = ProjectAnalyzer.detectProjectType(projectDir)

        if (type == ProjectType.WEB) {
            val outputDir = File(filesDir, "web_dist")
            val step = WebBuildStep(projectDir, outputDir)
            val result = step.execute(callback)
            if (result.success) {
                val indexHtml = File(outputDir, "index.html")
                callback.onSuccess(indexHtml.absolutePath)

                val intent = Intent(this, WebRuntimeActivity::class.java).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    putExtra("URL", indexHtml.toURI().toString())
                    putExtra("TIMESTAMP", System.currentTimeMillis())
                }
                startActivity(intent)
            } else {
                callback.onFailure(result.output)
            }
            return
        }

        if (type == ProjectType.REACT_NATIVE) {
            val step = ReactNativeBuildStep(this, projectDir, buildDir, cacheDir, localRepoDir)
            val result = step.execute(callback)
            if (result.success) {
                callback.onSuccess(File(buildDir, "app-signed.apk").absolutePath)
                ApkInstaller.installApk(this, File(buildDir, "app-signed.apk").absolutePath)
            } else {
                callback.onFailure(result.output)
            }
            return
        }

        val resolver = DependencyResolver(projectDir, File(projectDir, "dependencies.toml"), localRepoDir)
        val resolverResult = resolver.execute()
        if (!resolverResult.success) {
            callback.onFailure("Dependency resolution failed: ${resolverResult.output}")
            return
        }

        val aapt2Path = ToolManager.getToolPath(this, "aapt2")
        val kotlincJarPath = ToolManager.getToolPath(this, "kotlin-compiler.jar")
        val d8Path = ToolManager.getToolPath(this, "d8.jar")
        val apkSignerPath = ToolManager.getToolPath(this, "apksigner.jar")
        val keystorePath = ToolManager.getToolPath(this, "debug.keystore")
        val androidJarPath = ToolManager.getToolPath(this, "android.jar")
        val javaBinaryPath = ToolManager.getToolPath(this, "java")

        if (aapt2Path == null || kotlincJarPath == null || d8Path == null || apkSignerPath == null || keystorePath == null || androidJarPath == null || javaBinaryPath == null) {
            callback.onFailure("Build failed: One or more tools not found. check logs for details.")
            return
        }

        val buildOrchestrator = BuildOrchestrator(
            listOf(
                GenerateSourceMap(File(projectDir, "app/src/main/res"), buildDir, cacheDir),
                Aapt2Compile(aapt2Path, File(projectDir, "app/src/main/res").absolutePath, File(buildDir, "compiled_res").absolutePath, MIN_SDK, TARGET_SDK),
                Aapt2Link(aapt2Path, File(buildDir, "compiled_res").absolutePath, androidJarPath, File(projectDir, "app/src/main/AndroidManifest.xml").absolutePath, File(buildDir, "app.apk").absolutePath, File(buildDir, "gen").absolutePath, MIN_SDK, TARGET_SDK),
                KotlincCompile(kotlincJarPath, androidJarPath, File(projectDir, "app/src/main/java").absolutePath, File(buildDir, "classes"), resolverResult.output, javaBinaryPath),
                D8Compile(d8Path, javaBinaryPath, androidJarPath, File(buildDir, "classes").absolutePath, File(buildDir, "classes").absolutePath, resolverResult.output),
                ApkBuild(File(buildDir, "app-signed.apk").absolutePath, File(buildDir, "app.apk").absolutePath, File(buildDir, "classes").absolutePath),
                ApkSign(apkSignerPath, javaBinaryPath, keystorePath, "android", "androiddebugkey", File(buildDir, "app-signed.apk").absolutePath)
            )
        )

        val result = buildOrchestrator.execute(callback)
        if (result.success) {
            callback.onSuccess(File(buildDir, "app-signed.apk").absolutePath)
            ApkInstaller.installApk(this, File(buildDir, "app-signed.apk").absolutePath)
        } else {
            callback.onFailure(result.output)
        }
    }
}