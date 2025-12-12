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
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.File
import java.util.ArrayDeque

/**
 * A foreground [Service] that orchestrates the build process in a separate process (`:build_process`).
 */
class BuildService : Service() {
    companion object {
        private const val TAG = "BuildService"
        private const val NOTIFICATION_CHANNEL_ID = "IDEAZ_BUILD_CHANNEL_ID"
        private const val NOTIFICATION_ID = 1
        private const val MIN_SDK = 26
        private const val TARGET_SDK = 36
        private const val MAX_LOG_LINES = 50
        private const val ACTION_SYNC_AND_EXIT = "SYNC_AND_EXIT"
        private const val ACTION_BUILD_LOG_INPUT = "BUILD_LOG_INPUT"
        private const val EXTRA_TEXT_REPLY = "KEY_TEXT_REPLY"
    }

    private val logBuffer = ArrayDeque<String>(MAX_LOG_LINES)
    private val serviceScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var buildJob: Job? = null
    private var currentProjectPath: String? = null

    private val binder = object : IBuildService.Stub() {
        override fun startBuild(projectPath: String, callback: IBuildCallback) = this@BuildService.startBuild(projectPath, callback)
        override fun updateNotification(message: String) = this@BuildService.updateNotification(message)
        override fun cancelBuild() = this@BuildService.cancelBuild()
    }

    override fun onCreate() {
        super.onCreate()
        // No auto-install from assets. Tools must be downloaded.
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
        } else if (intent?.action == ACTION_BUILD_LOG_INPUT) {
            val remoteInput = androidx.core.app.RemoteInput.getResultsFromIntent(intent)
            if (remoteInput != null) {
                val input = remoteInput.getCharSequence(EXTRA_TEXT_REPLY).toString()
                val promptIntent = Intent("com.hereliesaz.ideaz.AI_PROMPT").apply {
                    putExtra("PROMPT", input)
                }
                sendBroadcast(promptIntent)
            }
            return START_NOT_STICKY
        }

        val notification = createNotificationBuilder()
            .setContentText("Build Service is running.")
            .setStyle(NotificationCompat.BigTextStyle().bigText("Build Service is running."))
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

        val remoteInput = androidx.core.app.RemoteInput.Builder(EXTRA_TEXT_REPLY)
            .setLabel("Prompt AI...")
            .build()

        val replyIntent = Intent(this, BuildService::class.java).apply {
            action = ACTION_BUILD_LOG_INPUT
        }
        val replyPendingIntent = PendingIntent.getService(
            this,
            2,
            replyIntent,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) PendingIntent.FLAG_MUTABLE else PendingIntent.FLAG_UPDATE_CURRENT
        )

        val replyAction = NotificationCompat.Action.Builder(
            android.R.drawable.ic_input_add,
            "Prompt",
            replyPendingIntent
        )
        .addRemoteInput(remoteInput)
        .build()

        return NotificationCompat.Builder(this, NOTIFICATION_CHANNEL_ID)
            .setContentTitle("IDEaz IDE")
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentIntent(contentIntent)
            .addAction(replyAction)
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
            } else {
                callback.onLog("  ERROR: Path returned '$path' but file does not exist!")
                return null
            }
        } else {
            callback.onLog("  ERROR: ToolManager returned null for '$name'. Ensure tools are downloaded.")
            return null
        }
    }

    private fun cancelBuild() {
        try {
            buildJob?.cancel()
            buildJob = null
            updateNotification("Build cancelled.")
        } catch (e: Exception) {
            Log.e(TAG, "Error cancelling build", e)
        }
    }

    private fun startBuild(projectPath: String, callback: IBuildCallback) {
        cancelBuild()
        buildJob = serviceScope.launch(Dispatchers.IO) {
            try {
                android.os.Process.setThreadPriority(android.os.Process.THREAD_PRIORITY_BACKGROUND)
                currentProjectPath = projectPath
                synchronized(logBuffer) {
                    logBuffer.clear()
                }
                updateNotification("Starting build...")

                val projectDir = File(projectPath)
                val buildDir = File(projectDir, "build").apply { mkdirs() }
                val cacheDir = File(filesDir, "cache").apply { mkdirs() }
                val localRepoDir = File(filesDir, "local-repo").apply { mkdirs() }

                val type = ProjectAnalyzer.detectProjectType(projectDir)
                val packageName = ProjectAnalyzer.detectPackageName(projectDir)

                // --- WEB BUILD ---
                if (type == ProjectType.WEB) {
                    // ... (existing web build logic)
                    val outputDir = File(filesDir, "web_dist")
                    val step = WebBuildStep(projectDir, outputDir)
                    val result = step.execute(callback)
                    if (result.success && isActive) {
                        val indexHtml = File(outputDir, "index.html")
                        callback.onSuccess(indexHtml.absolutePath)
                    } else if (isActive) {
                        callback.onFailure(result.output)
                    }
                    return@launch
                }

                // --- ANDROID BUILD ---
                // ... (Dependency Resolution)
                val resolver = HttpDependencyResolver(projectDir, File(projectDir, "dependencies.toml"), localRepoDir, callback)
                val resolverResult = resolver.execute()
                if (!resolverResult.success && isActive) {
                    callback.onFailure("Dependency resolution failed: ${resolverResult.output}")
                    return@launch
                }

                if (!isActive) return@launch

                // --- TOOL VERIFICATION ---
                if (!ToolManager.areToolsInstalled(this@BuildService)) {
                    callback.onFailure("Local build tools not installed. Please enable them in Settings.")
                    return@launch
                }

                callback.onLog("\n--- Toolchain Verification ---")
                val aapt2Path = checkTool("aapt2", callback)
                val kotlincJarPath = checkTool("kotlin-compiler.jar", callback)
                val d8Path = checkTool("d8.jar", callback)
                val apkSignerPath = checkTool("apksigner.jar", callback)
                val androidJarPath = checkTool("android.jar", callback)
                val javaBinaryPath = checkTool("java", callback)

                val prefs = PreferenceManager.getDefaultSharedPreferences(this@BuildService)
                val customKsPath = prefs.getString(SettingsViewModel.KEY_KEYSTORE_PATH, null)
                val keystorePath = if (customKsPath != null && File(customKsPath).exists()) {
                    callback.onLog("Using Custom Keystore: $customKsPath")
                    customKsPath
                } else {
                    checkTool("debug.keystore", callback)
                }

                val ksPass = prefs.getString(SettingsViewModel.KEY_KEYSTORE_PASS, "android") ?: "android"
                val keyAlias = prefs.getString(SettingsViewModel.KEY_KEY_ALIAS, "androiddebugkey") ?: "androiddebugkey"

                callback.onLog("------------------------------\n")

                val missingTools = mutableListOf<String>()
                if (aapt2Path == null) missingTools.add("aapt2")
                if (kotlincJarPath == null) missingTools.add("kotlin-compiler.jar")
                if (d8Path == null) missingTools.add("d8.jar")
                if (apkSignerPath == null) missingTools.add("apksigner.jar")
                if (keystorePath == null) missingTools.add("keystore")
                if (androidJarPath == null) missingTools.add("android.jar")
                if (javaBinaryPath == null) missingTools.add("java")

                if (missingTools.isNotEmpty() && isActive) {
                    val errorMsg = "Build failed: One or more tools not found: ${missingTools.joinToString(", ")}"
                    Log.e(TAG, errorMsg)
                    callback.onFailure(errorMsg)
                    return@launch
                }

                if (!isActive) return@launch

                // --- PRE-PROCESSING ---
                val processAars = ProcessAars(resolver.resolvedArtifacts, buildDir, aapt2Path!!)
                val aarResult = processAars.execute(callback)
                if (!aarResult.success && isActive) {
                    callback.onFailure(aarResult.output)
                    return@launch
                }

                val aarJars = processAars.jars.joinToString(File.pathSeparator)
                val resolvedJars = resolver.resolvedClasspath
                val fullClasspath = if (aarJars.isNotEmpty()) {
                    "$resolvedJars${File.pathSeparator}$aarJars"
                } else {
                    resolvedJars
                }

                if (!isActive) return@launch

                val processedManifestPath = File(buildDir, "processed_manifest.xml").absolutePath

                // --- BUILD EXECUTION ---
                val buildOrchestrator = BuildOrchestrator(
                    listOf(
                        ProcessManifest(File(projectDir, "app/src/main/AndroidManifest.xml").absolutePath, processedManifestPath, packageName, MIN_SDK, TARGET_SDK),
                        Aapt2Compile(aapt2Path, File(projectDir, "app/src/main/res").absolutePath, File(buildDir, "compiled_res").absolutePath, MIN_SDK, TARGET_SDK),
                        Aapt2Link(aapt2Path, File(buildDir, "compiled_res").absolutePath, androidJarPath!!, processedManifestPath, File(buildDir, "app.apk").absolutePath, File(buildDir, "gen").absolutePath, MIN_SDK, TARGET_SDK, processAars.compiledAars, packageName),
                        KotlincCompile(kotlincJarPath!!, androidJarPath, File(projectDir, "app/src/main/java").absolutePath, File(buildDir, "classes"), fullClasspath, javaBinaryPath!!),
                        D8Compile(d8Path!!, javaBinaryPath, androidJarPath, File(buildDir, "classes").absolutePath, File(buildDir, "classes").absolutePath, fullClasspath),
                        ApkBuild(File(buildDir, "app-signed.apk").absolutePath, File(buildDir, "app.apk").absolutePath, File(buildDir, "classes").absolutePath),
                        ApkSign(apkSignerPath!!, javaBinaryPath, keystorePath!!, ksPass, keyAlias, File(buildDir, "app-signed.apk").absolutePath),
                        GenerateSourceMap(File(projectDir, "app/src/main/res"), buildDir, cacheDir)
                    )
                )

                val result = buildOrchestrator.execute(callback)
                if (result.success && isActive) {
                    callback.onSuccess(File(buildDir, "app-signed.apk").absolutePath)
                    ApkInstaller.installApk(this@BuildService, File(buildDir, "app-signed.apk").absolutePath)
                } else if (isActive) {
                    callback.onFailure(result.output)
                }
            } catch (e: Throwable) {
                Log.e(TAG, "Build service crashed", e)
                if (isActive) {
                    callback.onFailure("[IDE] Failed with internal error: ${e.message}\n${e.stackTraceToString()}")
                }
            }
        }
    }
}