package com.hereliesaz.ideaz.services

import android.app.ActivityOptions
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
import com.hereliesaz.ideaz.api.GitHubApiClient
import com.hereliesaz.ideaz.utils.CrashHandler
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.util.zip.ZipInputStream
import org.eclipse.jgit.api.Git
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.consumeAsFlow
import java.io.File
import java.util.ArrayDeque
import kotlin.coroutines.coroutineContext

/**
 * A foreground [Service] that orchestrates the build process.
 * 
 * This service implements advanced log batching to minimize IPC overhead and prevent ANRs
 * in the main application process. It also handles Background Activity Launch (BAL) restrictions
 * for targeting Android 14+.
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
        private const val NOTIFICATION_UPDATE_INTERVAL_MS = 1000L
        private const val UI_LOG_BATCH_INTERVAL_MS = 250L
    }

    private val logBuffer = ArrayDeque<String>(MAX_LOG_LINES)
    private val serviceScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var buildJob: Job? = null
    private var currentProjectPath: String? = null
    private var lastNotificationUpdate = 0L
    private var pendingNotificationUpdate: Job? = null

    private val binder = object : IBuildService.Stub() {
        override fun startBuild(projectPath: String, callback: IBuildCallback) = this@BuildService.startBuild(projectPath, callback)
        override fun downloadDependencies(projectPath: String, callback: IBuildCallback) = this@BuildService.downloadDependencies(projectPath, callback)
        override fun updateNotification(message: String) = this@BuildService.updateNotification(message)
        override fun cancelBuild() = this@BuildService.cancelBuild()
    }

    override fun onCreate() {
        super.onCreate()
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
            .setContentText("Build Service is ready.")
            .setStyle(NotificationCompat.BigTextStyle().bigText("Build Service is ready."))
            .build()
        startForeground(NOTIFICATION_ID, notification)
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder = binder

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(NOTIFICATION_CHANNEL_ID, "IDEaz Build Service", NotificationManager.IMPORTANCE_LOW)
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

        val now = System.currentTimeMillis()
        if (now - lastNotificationUpdate < NOTIFICATION_UPDATE_INTERVAL_MS) {
            if (pendingNotificationUpdate == null || pendingNotificationUpdate?.isCompleted == true) {
                pendingNotificationUpdate = serviceScope.launch {
                    delay(NOTIFICATION_UPDATE_INTERVAL_MS - (now - lastNotificationUpdate))
                    performNotificationUpdate()
                }
            }
            return
        }

        serviceScope.launch { performNotificationUpdate() }
    }

    private suspend fun performNotificationUpdate() = withContext(Dispatchers.IO) {
        lastNotificationUpdate = System.currentTimeMillis()
        val latestLog = synchronized(logBuffer) { logBuffer.lastOrNull() } ?: "Processing..."
        val bigText = synchronized(logBuffer) { logBuffer.joinToString("\n") }

        val notification = createNotificationBuilder()
            .setContentText(latestLog)
            .setStyle(NotificationCompat.BigTextStyle().bigText(bigText))
            .setOnlyAlertOnce(true)
            .build()

        val nm = getSystemService(NotificationManager::class.java)
        try {
            nm.notify(NOTIFICATION_ID, notification)
        } catch (e: Exception) {
             Log.e(TAG, "Failed to update notification", e)
        }
    }

    private fun createNotificationBuilder(): NotificationCompat.Builder {
        val options = ActivityOptions.makeBasic()
        if (Build.VERSION.SDK_INT >= 34) {
            options.setPendingIntentBackgroundActivityStartMode(ActivityOptions.MODE_BACKGROUND_ACTIVITY_START_ALLOWED)
        }
        if (Build.VERSION.SDK_INT >= 35) {
            options.setPendingIntentCreatorBackgroundActivityStartMode(ActivityOptions.MODE_BACKGROUND_ACTIVITY_START_ALLOWED)
        }

        val contentIntent = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            },
            PendingIntent.FLAG_IMMUTABLE,
            options.toBundle()
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

    /**
     * Wraps the provided IBuildCallback to implement log batching and local notification updates.
     */
    private fun wrapCallback(callback: IBuildCallback): IBuildCallback {
        return object : IBuildCallback.Stub() {
            private val logChannel = Channel<String>(Channel.UNLIMITED)
            private val batchJob = serviceScope.launch {
                val batch = StringBuilder()
                var lastSend = 0L
                logChannel.consumeAsFlow().collect { msg ->
                    batch.append(msg).append("\n")
                    val now = System.currentTimeMillis()
                    if (now - lastSend > UI_LOG_BATCH_INTERVAL_MS) {
                        val batchStr = batch.toString()
                        batch.setLength(0)
                        lastSend = now
                        try {
                            callback.onLog(batchStr)
                        } catch (e: Exception) {
                            Log.e(TAG, "Failed to send log to UI", e)
                        }
                    }
                }
            }

            override fun onLog(message: String) {
                updateNotification(message)
                logChannel.trySend(message)
            }

            override fun onSuccess(apkPath: String) {
                batchJob.cancel()
                try { callback.onSuccess(apkPath) } catch (e: Exception) {}
            }

            override fun onFailure(message: String) {
                batchJob.cancel()
                try { callback.onFailure(message) } catch (e: Exception) {}
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

    private fun downloadDependencies(projectPath: String, callback: IBuildCallback) {
        cancelBuild()
        val wrappedCallback = wrapCallback(callback)
        buildJob = serviceScope.launch(Dispatchers.IO) {
            try {
                android.os.Process.setThreadPriority(android.os.Process.THREAD_PRIORITY_BACKGROUND)
                currentProjectPath = projectPath
                synchronized(logBuffer) {
                    logBuffer.clear()
                }
                updateNotification("Downloading dependencies...")

                val projectDir = File(projectPath)
                if (!projectDir.exists()) {
                    wrappedCallback.onFailure("Project directory not found: $projectPath")
                    return@launch
                }
                val localRepoDir = File(filesDir, "local-repo").apply { mkdirs() }

                val type = ProjectAnalyzer.detectProjectType(projectDir)

                if (type == ProjectType.ANDROID) {
                    val resolver = HttpDependencyResolver(projectDir, File(projectDir, "dependencies.toml"), localRepoDir, wrappedCallback)
                    val resolverResult = resolver.execute()
                    if (resolverResult.success && isActive) {
                        wrappedCallback.onLog("\n[IDE] Dependencies downloaded successfully.")
                        updateNotification("Dependencies downloaded.")
                    } else if (isActive) {
                        wrappedCallback.onFailure("Dependency resolution failed: ${resolverResult.output}")
                    }
                } else if (type == ProjectType.WEB) {
                    if (File(projectDir, "package.json").exists()) {
                        wrappedCallback.onLog("\n[IDE] 'package.json' detected. 'npm install' is not currently supported on device.")
                    } else {
                        wrappedCallback.onLog("\n[IDE] No dependency file detected for Web project.")
                    }
                    updateNotification("Download finished (Web).")
                } else {
                    wrappedCallback.onLog("\n[IDE] Dependency download not supported for $type projects.")
                    updateNotification("Download finished ($type).")
                }
            } catch (e: Throwable) {
                Log.e(TAG, "Download dependencies failed", e)
                if (isActive) {
                    wrappedCallback.onFailure("[IDE] Failed: ${e.message}")
                }
            }
        }
    }

    private fun startBuild(projectPath: String, callback: IBuildCallback) {
        cancelBuild()
        val wrappedCallback = wrapCallback(callback)
        buildJob = serviceScope.launch(Dispatchers.IO) {
            try {
                android.os.Process.setThreadPriority(android.os.Process.THREAD_PRIORITY_BACKGROUND)
                currentProjectPath = projectPath
                synchronized(logBuffer) {
                    logBuffer.clear()
                }
                updateNotification("Starting build...")

                val projectDir = File(projectPath)
                val type = ProjectAnalyzer.detectProjectType(projectDir)

                val prefs = PreferenceManager.getDefaultSharedPreferences(this@BuildService)
                val localBuildEnabled = prefs.getBoolean(SettingsViewModel.KEY_ENABLE_LOCAL_BUILDS, false)

                // --- REMOTE BUILD CHECK ---
                if (!localBuildEnabled && type != ProjectType.WEB) {
                    startRemoteBuild(projectPath, wrappedCallback)
                    return@launch
                }

                val buildDir = File(projectDir, "build").apply { mkdirs() }
                val cacheDir = File(filesDir, "cache").apply { mkdirs() }
                val localRepoDir = File(filesDir, "local-repo").apply { mkdirs() }

                val packageName = ProjectAnalyzer.detectPackageName(projectDir)

                // --- WEB BUILD ---
                if (type == ProjectType.WEB) {
                    val outputDir = File(filesDir, "web_dist")
                    val step = WebBuildStep(projectDir, outputDir)
                    val result = step.execute(wrappedCallback)
                    if (result.success && isActive) {
                        val indexHtml = File(outputDir, "index.html")
                        wrappedCallback.onSuccess(indexHtml.absolutePath)
                    } else if (isActive) {
                        wrappedCallback.onFailure(result.output)
                    }
                    return@launch
                }

                // --- ANDROID BUILD ---
                val resolver = HttpDependencyResolver(projectDir, File(projectDir, "dependencies.toml"), localRepoDir, wrappedCallback)
                val resolverResult = resolver.execute()
                if (!resolverResult.success && isActive) {
                    wrappedCallback.onFailure("Dependency resolution failed: ${resolverResult.output}")
                    return@launch
                }

                if (!isActive) return@launch

                // --- TOOL VERIFICATION ---
                if (!ToolManager.areToolsInstalled(this@BuildService)) {
                    wrappedCallback.onFailure("Local build tools not installed. Please enable them in Settings.")
                    return@launch
                }

                wrappedCallback.onLog("\n--- Toolchain Verification ---")
                val aapt2Path = checkTool("aapt2", wrappedCallback)
                val kotlincJarPath = checkTool("kotlin-compiler.jar", wrappedCallback)
                val d8Path = checkTool("d8.jar", wrappedCallback)
                val apkSignerPath = checkTool("apksigner.jar", wrappedCallback)
                val androidJarPath = checkTool("android.jar", wrappedCallback)
                val javaBinaryPath = checkTool("java", wrappedCallback)

                val customKsPath = prefs.getString(SettingsViewModel.KEY_KEYSTORE_PATH, null)
                val keystorePath = if (customKsPath != null && File(customKsPath).exists()) {
                    wrappedCallback.onLog("Using Custom Keystore: $customKsPath")
                    customKsPath
                } else {
                    checkTool("debug.keystore", wrappedCallback)
                }

                val ksPass = prefs.getString(SettingsViewModel.KEY_KEYSTORE_PASS, "android") ?: "android"
                val keyAlias = prefs.getString(SettingsViewModel.KEY_KEY_ALIAS, "androiddebugkey") ?: "androiddebugkey"

                wrappedCallback.onLog("------------------------------\n")

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
                    wrappedCallback.onFailure(errorMsg)
                    return@launch
                }

                if (!isActive) return@launch

                // --- PRE-PROCESSING ---
                val processAars = ProcessAars(resolver.resolvedArtifacts, buildDir, aapt2Path!!)
                val aarResult = processAars.execute(wrappedCallback)
                if (!aarResult.success && isActive) {
                    wrappedCallback.onFailure(aarResult.output)
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
                try {
                    injectResizeableActivity(projectDir)
                } catch (e: Exception) {
                    Log.w(TAG, "Failed to inject resizeableActivity", e)
                }

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

                val result = buildOrchestrator.execute(wrappedCallback)
                if (result.success && isActive) {
                    wrappedCallback.onSuccess(File(buildDir, "app-signed.apk").absolutePath)
                    ApkInstaller.installApk(this@BuildService, File(buildDir, "app-signed.apk").absolutePath)
                } else if (isActive) {
                    wrappedCallback.onFailure(result.output)
                }
            } catch (e: Throwable) {
                Log.e(TAG, "Build service crashed", e)
                if (isActive) {
                    wrappedCallback.onFailure("[IDE] Failed with internal error: ${e.message}\n${e.stackTraceToString()}")
                }
            }
        }
    }

    private suspend fun startRemoteBuild(projectPath: String, callback: IBuildCallback) {
        val projectDir = File(projectPath)

        try {
            injectResizeableActivity(projectDir)
        } catch (e: Exception) {
            callback.onLog("Warning: Failed to inject resizeableActivity: ${e.message}\n")
        }

        updateNotification("Syncing to GitHub...")
        callback.onLog("Syncing changes to GitHub...\n")

        val prefs = PreferenceManager.getDefaultSharedPreferences(this)
        val token = prefs.getString(SettingsViewModel.KEY_GITHUB_TOKEN, null)
        val user = prefs.getString(SettingsViewModel.KEY_GITHUB_USER, null)
        val repo = projectDir.name

        if (token.isNullOrBlank() || user.isNullOrBlank()) {
            callback.onFailure("GitHub token or user not set. Please configure in settings.")
            return
        }

        var headSha: String? = null
        try {
            val git = GitManager(projectDir)
            if (git.hasChanges()) {
                git.addAll()
                git.commit("Remote Build Trigger: ${System.currentTimeMillis()}")
            }
            git.push(user, token)

            val gitObj = Git.open(projectDir)
            val head = gitObj.repository.resolve("HEAD")
            headSha = head.name
            gitObj.close()

            callback.onLog("Synced. HEAD: ${headSha?.take(7)}\n")
        } catch (e: org.eclipse.jgit.errors.RepositoryNotFoundException) {
            callback.onFailure("Project is not a valid Git repository. Please initialize it or clone a remote one.")
            return
        } catch (e: Exception) {
            CrashHandler.report(this, e)
            callback.onFailure("Git Sync failed: ${e.message}")
            return
        }

        if (headSha == null) {
            callback.onFailure("Could not determine HEAD SHA.")
            return
        }

        updateNotification("Waiting for build...")
        callback.onLog("Waiting for GitHub Action...\n")

        val api = GitHubApiClient.createService(token)
        var runId: Long? = null
        var attempts = 0

        while (runId == null && attempts < 20 && coroutineContext.isActive) {
            try {
                val runs = api.listWorkflowRuns(user, repo, headSha = headSha)
                val run = runs.workflowRuns.firstOrNull()
                if (run != null) {
                    runId = run.id
                    callback.onLog("Workflow Run ID: $runId\n")
                } else {
                    kotlinx.coroutines.delay(3000)
                    attempts++
                }
            } catch (e: Exception) {
                kotlinx.coroutines.delay(3000)
                attempts++
            }
        }

        if (runId == null) {
            callback.onFailure("Workflow run not found for commit $headSha. Check GitHub Actions.")
            return
        }

        var status = "queued"
        var conclusion: String? = null

        while ((status == "queued" || status == "in_progress") && coroutineContext.isActive) {
            kotlinx.coroutines.delay(5000)
            try {
                val runs = api.listWorkflowRuns(user, repo, headSha = headSha)
                val run = runs.workflowRuns.find { it.id == runId }
                if (run != null) {
                    status = run.status
                    conclusion = run.conclusion
                    updateNotification("Build: $status")
                    if (status == "completed") break
                }
            } catch (e: Exception) {
                // Ignore
            }
        }

        if (!coroutineContext.isActive) return

        if (conclusion != "success") {
            callback.onLog("Build finished with status: $conclusion\n")
            try {
                val jobs = api.getRunJobs(user, repo, runId)
                val failedJob = jobs.jobs.find { it.conclusion == "failure" }
                if (failedJob != null) {
                    val logResp = api.getJobLogs(user, repo, failedJob.id)
                    if (logResp.isSuccessful) {
                        val logText = logResp.body()?.string() ?: "No log content"
                        callback.onFailure("Remote Build Failed:\n$logText")
                    } else {
                        callback.onFailure("Remote Build Failed. Could not retrieve logs.")
                    }
                } else {
                    callback.onFailure("Remote Build Failed (Unknown reason).")
                }
            } catch (e: Exception) {
                CrashHandler.report(this, e)
                callback.onFailure("Remote Build Failed: ${e.message}")
            }
            return
        }

        callback.onLog("Build Successful. Downloading artifact...\n")
        updateNotification("Downloading APK...")

        try {
            val artifactsResp = api.getRunArtifacts(user, repo, runId)
            val apkArtifact = artifactsResp.artifacts.find { it.name.contains("debug") && it.name.endsWith("apk") }
                ?: artifactsResp.artifacts.find { it.name.endsWith(".apk") }
                ?: artifactsResp.artifacts.find { it.name == "app-debug" }

            if (apkArtifact == null) {
                callback.onFailure("No APK artifact found in build.")
                return
            }

            val downloadUrl = apkArtifact.archiveDownloadUrl
            val zipFile = File(filesDir, "remote_build.zip")

            if (downloadFileWithAuth(downloadUrl, zipFile, token, callback)) {
                callback.onLog("Unzipping artifact...\n")
                val destDir = File(filesDir, "remote_extracted")
                destDir.deleteRecursively()
                destDir.mkdirs()

                ZipInputStream(zipFile.inputStream()).use { zis ->
                    var entry = zis.nextEntry
                    while (entry != null) {
                        val newFile = File(destDir, entry.name)
                        if (entry.isDirectory) {
                            newFile.mkdirs()
                        } else {
                            newFile.parentFile?.mkdirs()
                            FileOutputStream(newFile).use { fos ->
                                zis.copyTo(fos)
                            }
                        }
                        entry = zis.nextEntry
                    }
                }

                val apkFile = destDir.walkTopDown().find { it.name.endsWith(".apk") }
                if (apkFile != null) {
                    callback.onLog("Installing APK: ${apkFile.name}\n")
                    updateNotification("Installing...")
                    ApkInstaller.installApk(this, apkFile.absolutePath)
                    callback.onSuccess(apkFile.absolutePath)
                } else {
                    callback.onFailure("APK file not found in artifact zip.")
                }

            } else {
                callback.onFailure("Failed to download artifact.")
            }
        } catch (e: Exception) {
            CrashHandler.report(this, e)
            callback.onFailure("Download/Install failed: ${e.message}")
        }
    }

    private fun injectResizeableActivity(projectDir: File) {
        val manifestFile = File(projectDir, "app/src/main/AndroidManifest.xml")
        if (manifestFile.exists()) {
            var content = manifestFile.readText()
            if (!content.contains("android:resizeableActivity")) {
                content = content.replaceFirst("<application", "<application android:resizeableActivity=\"true\"")
                manifestFile.writeText(content)
            }
        }
    }

    private fun downloadFileWithAuth(urlStr: String, destination: File, token: String, callback: IBuildCallback): Boolean {
        return try {
            val url = URL(urlStr)
            val connection = url.openConnection() as HttpURLConnection
            connection.setRequestProperty("Authorization", "Bearer $token")
            connection.connect()

            if (connection.responseCode != HttpURLConnection.HTTP_OK) {
                callback.onLog("Download failed: HTTP ${connection.responseCode}\n")
                return false
            }

            val input = connection.inputStream
            val output = FileOutputStream(destination)
            input.copyTo(output)
            output.close()
            input.close()
            true
        } catch (e: Exception) {
            callback.onLog("Download error: ${e.message}\n")
            false
        }
    }
}
