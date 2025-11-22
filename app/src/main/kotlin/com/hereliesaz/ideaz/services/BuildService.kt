package com.hereliesaz.ideaz.services

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.preference.PreferenceManager
import com.hereliesaz.ideaz.IBuildCallback
import com.hereliesaz.ideaz.IBuildService
import com.hereliesaz.ideaz.buildlogic.*
import com.hereliesaz.ideaz.utils.ApkInstaller
import com.hereliesaz.ideaz.utils.ToolManager
import com.hereliesaz.ideaz.utils.ProjectAnalyzer
import com.hereliesaz.ideaz.models.ProjectType
import com.hereliesaz.ideaz.ui.SettingsViewModel
import com.hereliesaz.ideaz.ui.web.WebRuntimeActivity
import java.io.File
import java.util.ArrayDeque

class BuildService : Service() {
    companion object {
        private const val TAG = "BuildService"
        private const val NOTIFICATION_CHANNEL_ID = "IDEAZ_BUILD_CHANNEL_ID"
        private const val NOTIFICATION_ID = 1
        private const val MIN_SDK = 26
        private const val TARGET_SDK = 36
        private const val MAX_LOG_LINES = 5
    }

    private val logBuffer = ArrayDeque<String>(MAX_LOG_LINES)

    private val binder = object : IBuildService.Stub() {
        override fun startBuild(projectPath: String, callback: IBuildCallback) = this@BuildService.startBuild(projectPath, callback)
        override fun updateNotification(message: String) = this@BuildService.updateNotification(message)
    }

    override fun onCreate() {
        super.onCreate()
        // Ensure tools are installed/repaired on service creation
        ToolManager.installTools(this)
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
        synchronized(logBuffer) {
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
            .setContentText(latestLog)
            .setStyle(NotificationCompat.BigTextStyle().bigText(bigText))
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setOnlyAlertOnce(true)
            .build()

        getSystemService(NotificationManager::class.java).notify(NOTIFICATION_ID, notification)
    }

    // Helper to check tool and log specifically to the user console
    private fun checkTool(name: String, callback: IBuildCallback): String? {
        callback.onLog("Verifying tool: $name...")
        val path = ToolManager.getToolPath(this, name)

        if (path != null) {
            val file = File(path)
            if (file.exists()) {
                callback.onLog("  OK: $path (${file.length()} bytes)")
                return path
            } else {
                callback.onLog("  ERROR: Path returned '$path' but file does not exist!")
                return null
            }
        } else {
            callback.onLog("  ERROR: ToolManager returned null for '$name'. Check nativeLibraryDir or assets.")
            return null
        }
    }

    private fun startBuild(projectPath: String, callback: IBuildCallback) {
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

        // --- VERBOSE TOOL CHECK ---
        callback.onLog("\n--- Toolchain Verification ---")
        val aapt2Path = checkTool("aapt2", callback)
        val kotlincJarPath = checkTool("kotlin-compiler.jar", callback)
        val d8Path = checkTool("d8.jar", callback)
        val apkSignerPath = checkTool("apksigner.jar", callback)
        val androidJarPath = checkTool("android.jar", callback)
        val javaBinaryPath = checkTool("java", callback)

        // Handle Keystore: Check for custom, fallback to default asset
        val prefs = PreferenceManager.getDefaultSharedPreferences(this)
        val customKsPath = prefs.getString(SettingsViewModel.KEY_KEYSTORE_PATH, null)
        var keystorePath = if (customKsPath != null && File(customKsPath).exists()) {
            callback.onLog("Using Custom Keystore: $customKsPath")
            customKsPath
        } else {
            checkTool("debug.keystore", callback)
        }

        // Read signing creds
        val ksPass = prefs.getString(SettingsViewModel.KEY_KEYSTORE_PASS, "android") ?: "android"
        val keyAlias = prefs.getString(SettingsViewModel.KEY_KEY_ALIAS, "androiddebugkey") ?: "androiddebugkey"
        // Note: Apksigner wrapper currently only uses key-pass if we modify ApkSign class to take it.
        // For now, usually keypass=storepass for debug.
        // ApkSign class needs update if key pass differs. Using kspass for both in this version.

        callback.onLog("------------------------------\n")

        val missingTools = mutableListOf<String>()
        if (aapt2Path == null) missingTools.add("aapt2")
        if (kotlincJarPath == null) missingTools.add("kotlin-compiler.jar")
        if (d8Path == null) missingTools.add("d8.jar")
        if (apkSignerPath == null) missingTools.add("apksigner.jar")
        if (keystorePath == null) missingTools.add("keystore")
        if (androidJarPath == null) missingTools.add("android.jar")
        if (javaBinaryPath == null) missingTools.add("java")

        if (missingTools.isNotEmpty()) {
            val errorMsg = "Build failed: One or more tools not found: ${missingTools.joinToString(", ")}"
            Log.e(TAG, errorMsg)
            callback.onFailure(errorMsg)
            return
        }
        // ---------------------------

        val buildOrchestrator = BuildOrchestrator(
            listOf(
                Aapt2Compile(aapt2Path!!, File(projectDir, "app/src/main/res").absolutePath, File(buildDir, "compiled_res").absolutePath, MIN_SDK, TARGET_SDK),
                Aapt2Link(aapt2Path, File(buildDir, "compiled_res").absolutePath, androidJarPath!!, File(projectDir, "app/src/main/AndroidManifest.xml").absolutePath, File(buildDir, "app.apk").absolutePath, File(buildDir, "gen").absolutePath, MIN_SDK, TARGET_SDK),
                KotlincCompile(kotlincJarPath!!, androidJarPath, File(projectDir, "app/src/main/java").absolutePath, File(buildDir, "classes"), resolverResult.output, javaBinaryPath!!),
                D8Compile(d8Path!!, javaBinaryPath, androidJarPath, File(buildDir, "classes").absolutePath, File(buildDir, "classes").absolutePath, resolverResult.output),
                ApkBuild(File(buildDir, "app-signed.apk").absolutePath, File(buildDir, "app.apk").absolutePath, File(buildDir, "classes").absolutePath),
                ApkSign(apkSignerPath!!, javaBinaryPath, keystorePath!!, ksPass, keyAlias, File(buildDir, "app-signed.apk").absolutePath),
                GenerateSourceMap(File(projectDir, "app/src/main/res"), buildDir, cacheDir)
            )
        )

        val result = buildOrchestrator.execute(callback)
        if (result.success) {
            callback.onSuccess(File(buildDir, "app-signed.apk").absolutePath)
            // Install Trigger
            ApkInstaller.installApk(this, File(buildDir, "app-signed.apk").absolutePath)
        } else {
            callback.onFailure(result.output)
        }
    }
}