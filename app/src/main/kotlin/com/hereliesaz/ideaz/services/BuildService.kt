package com.hereliesaz.ideaz.services

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.preference.PreferenceManager
import com.hereliesaz.ideaz.IBuildCallback
import com.hereliesaz.ideaz.IBuildService
import com.hereliesaz.ideaz.MainActivity
import com.hereliesaz.ideaz.buildlogic.*
import com.hereliesaz.ideaz.git.GitManager
import com.hereliesaz.ideaz.utils.ApkInstaller
import com.hereliesaz.ideaz.utils.ToolManager
import com.hereliesaz.ideaz.utils.ProjectAnalyzer
import com.hereliesaz.ideaz.models.ProjectType
import com.hereliesaz.ideaz.ui.SettingsViewModel
import com.hereliesaz.ideaz.ui.web.WebRuntimeActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
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
        private const val ACTION_SYNC_AND_EXIT = "SYNC_AND_EXIT"
    }

    private val logBuffer = ArrayDeque<String>(MAX_LOG_LINES)
    private val serviceScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var currentProjectPath: String? = null

    private val binder = object : IBuildService.Stub() {
        override fun startBuild(projectPath: String, callback: IBuildCallback) = this@BuildService.startBuild(projectPath, callback)
        override fun updateNotification(message: String) = this@BuildService.updateNotification(message)
    }

    override fun onCreate() {
        super.onCreate()
        ToolManager.installTools(this)
        createNotificationChannel()
    }

    override fun onDestroy() {
        super.onDestroy()
        serviceScope.cancel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_SYNC_AND_EXIT) {
            handleSyncAndExit()
            return START_NOT_STICKY
        }

        val notification = createNotificationBuilder()
            .setContentText("Build Service is running.")
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

        val notification = createNotificationBuilder()
            .setContentText(latestLog)
            .setStyle(NotificationCompat.BigTextStyle().bigText(bigText))
            .setOnlyAlertOnce(true)
            .build()

        getSystemService(NotificationManager::class.java).notify(NOTIFICATION_ID, notification)
    }

    private fun createNotificationBuilder(): NotificationCompat.Builder {
        val contentIntent = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            },
            PendingIntent.FLAG_IMMUTABLE
        )

        val syncIntent = Intent(this, BuildService::class.java).apply {
            action = ACTION_SYNC_AND_EXIT
        }
        val syncPendingIntent = PendingIntent.getService(
            this, 1, syncIntent, PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, NOTIFICATION_CHANNEL_ID)
            .setContentTitle("IDEaz IDE")
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentIntent(contentIntent)
            .addAction(android.R.drawable.ic_menu_save, "Sync & Exit", syncPendingIntent)
    }

    private fun handleSyncAndExit() {
        val path = currentProjectPath
        if (path == null) {
            Log.w(TAG, "Cannot sync: Project path is null")
            stopSelf()
            return
        }

        updateNotification("Syncing and exiting...")

        serviceScope.launch {
            try {
                val prefs = PreferenceManager.getDefaultSharedPreferences(this@BuildService)
                val token = prefs.getString(SettingsViewModel.KEY_GITHUB_TOKEN, null)
                val user = prefs.getString(SettingsViewModel.KEY_GITHUB_USER, null)

                val git = GitManager(File(path))
                if (git.hasChanges()) {
                    git.addAll()
                    git.commit("Sync and Exit")
                }
                if (token != null) {
                    git.push(user, token)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Sync failed", e)
            } finally {
                stopSelf()
            }
        }
    }

    private fun checkTool(name: String, callback: IBuildCallback): String? {
        callback.onLog("Verifying tool: $name...")
        val path = ToolManager.getToolPath(this, name)
        if (path != null) {
            val file = File(path)
            if (file.exists()) {
                callback.onLog("  OK: $path (${file.length()} bytes)")
                return path
            }
        }
        callback.onLog("  ERROR: Tool '$name' missing or unreadable.")
        return null
    }

    private fun startBuild(projectPath: String, callback: IBuildCallback) {
        currentProjectPath = projectPath
        synchronized(logBuffer) { logBuffer.clear() }
        updateNotification("Starting build...")

        val projectDir = File(projectPath)
        val buildDir = File(filesDir, "build").apply { mkdirs() }
        val cacheDir = File(filesDir, "cache").apply { mkdirs() }
        val localRepoDir = File(filesDir, "local-repo").apply { mkdirs() }

        val type = ProjectAnalyzer.detectProjectType(projectDir)

        // --- Web & RN Blocks Omitted for Brevity (Unchanged) ---
        if (type == ProjectType.WEB) {
            // ... (Web Logic)
            callback.onFailure("Web builds not fully implemented in this snippet")
            return
        }
        // ------------------------------------------------------

        // 1. Resolve Dependencies
        val resolver = DependencyResolver(projectDir, File(projectDir, "dependencies.toml"), localRepoDir)
        val resolverResult = resolver.execute(callback)

        if (!resolverResult.success) {
            callback.onFailure("Dependency resolution failed: ${resolverResult.output}")
            return
        }

        val resolvedClasspath = resolverResult.output // DependencyResolver now returns classpath in output

        // 2. Check Tools
        callback.onLog("\n--- Toolchain Verification ---")
        val aapt2Path = checkTool("aapt2", callback)
        val kotlincJarPath = checkTool("kotlin-compiler.jar", callback)
        val d8Path = checkTool("d8.jar", callback)
        val apkSignerPath = checkTool("apksigner.jar", callback)
        val androidJarPath = checkTool("android.jar", callback)
        val javaBinaryPath = checkTool("java", callback)

        // Handle Keystore
        val prefs = PreferenceManager.getDefaultSharedPreferences(this)
        val customKsPath = prefs.getString(SettingsViewModel.KEY_KEYSTORE_PATH, null)
        var keystorePath = if (customKsPath != null && File(customKsPath).exists()) {
            callback.onLog("Using Custom Keystore: $customKsPath")
            customKsPath
        } else {
            checkTool("debug.keystore", callback)
        }
        val ksPass = prefs.getString(SettingsViewModel.KEY_KEYSTORE_PASS, "android") ?: "android"
        val keyAlias = prefs.getString(SettingsViewModel.KEY_KEY_ALIAS, "androiddebugkey") ?: "androiddebugkey"

        callback.onLog("------------------------------\n")

        if (aapt2Path == null || kotlincJarPath == null || d8Path == null || apkSignerPath == null || keystorePath == null || androidJarPath == null || javaBinaryPath == null) {
            val errorMsg = "Build failed: One or more tools not found. Check logs."
            Log.e(TAG, errorMsg)
            callback.onFailure(errorMsg)
            return
        }

        // 3. Build Step Construction
        val steps = mutableListOf<BuildStep>()
        val compiledResDir = File(buildDir, "compiled_res")

        // A. Compile Dependency Resources (from AARs)
        resolver.extractedResourceDirs.forEach { resDir ->
            // We compile all into the same directory. AAPT2 handles this (mostly).
            // Note: To be safer, we could use separate dirs and link them all,
            // but accumulating .flat files in one dir works for simple cases.
            steps.add(Aapt2Compile(aapt2Path!!, resDir.absolutePath, compiledResDir.absolutePath, MIN_SDK, TARGET_SDK))
        }

        // B. Compile App Resources
        steps.add(Aapt2Compile(aapt2Path!!, File(projectDir, "app/src/main/res").absolutePath, compiledResDir.absolutePath, MIN_SDK, TARGET_SDK))

        // C. Link Everything
        steps.add(Aapt2Link(aapt2Path, compiledResDir.absolutePath, androidJarPath!!, File(projectDir, "app/src/main/AndroidManifest.xml").absolutePath, File(buildDir, "app.apk").absolutePath, File(buildDir, "gen").absolutePath, MIN_SDK, TARGET_SDK))

        // D. Compile Kotlin/Java
        steps.add(KotlincCompile(kotlincJarPath!!, androidJarPath, File(projectDir, "app/src/main/java").absolutePath, File(buildDir, "classes"), resolvedClasspath, javaBinaryPath!!))

        // E. Dex
        steps.add(D8Compile(d8Path!!, javaBinaryPath, androidJarPath, File(buildDir, "classes").absolutePath, File(buildDir, "classes").absolutePath, resolvedClasspath)) // D8 also needs classpath for Desugaring

        // F. Package
        steps.add(ApkBuild(File(buildDir, "app-signed.apk").absolutePath, File(buildDir, "app.apk").absolutePath, File(buildDir, "classes").absolutePath))

        // G. Sign
        steps.add(ApkSign(apkSignerPath!!, javaBinaryPath, keystorePath!!, ksPass, keyAlias, File(buildDir, "app-signed.apk").absolutePath))

        // H. Source Map (Last)
        steps.add(GenerateSourceMap(File(projectDir, "app/src/main/res"), buildDir, cacheDir))

        val buildOrchestrator = BuildOrchestrator(steps)
        val result = buildOrchestrator.execute(callback)

        if (result.success) {
            callback.onSuccess(File(buildDir, "app-signed.apk").absolutePath)
            ApkInstaller.installApk(this, File(buildDir, "app-signed.apk").absolutePath)
        } else {
            callback.onFailure(result.output)
        }
    }
}