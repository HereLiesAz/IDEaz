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
import com.hereliesaz.ideaz.utils.HybridToolchainManager
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
 * **Role:**
 * This service acts as the dedicated "Build Agent" on the device. It runs in a separate process
 * (`:build_process`) to isolate memory intensive operations (like compilation) from the main UI thread/process.
 *
 * **Key Features:**
 * - **AIDL Interface:** Exposes [IBuildService] for IPC with the main app.
 * - **Log Batching:** Implements advanced batching of logs sent via AIDL to minimize Cross-Process Communication (IPC) overhead.
 * - **Notification Management:** Shows a persistent notification with the latest log line and "Sync & Exit" action.
 * - **Hot Reload:** Monitors file changes (via [startHotReloadWatcher]) to trigger Zipline reloads.
 * - **Build Steps:** Uses [BuildOrchestrator] to execute a sequence of [BuildStep]s (e.g., Aapt2, Kotlinc, D8).
 */
class BuildService : Service() {
    companion object {
        private const val TAG = "BuildService"
        private const val NOTIFICATION_CHANNEL_ID = "IDEAZ_BUILD_CHANNEL_ID"
        private const val NOTIFICATION_ID = 1
        private const val MIN_SDK = 26
        private const val TARGET_SDK = 36

        /** Max lines to keep in the notification expanded view. */
        private const val MAX_LOG_LINES = 50

        // Notification Actions
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
    private var fileObserver: com.hereliesaz.ideaz.utils.RecursiveFileObserver? = null

    /**
     * The Binder implementation returned to clients binding to this service.
     */
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
        fileObserver?.stopWatching()
        serviceScope.cancel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // Handle Notification Actions
        if (intent?.action == ACTION_SYNC_AND_EXIT) {
            handleSyncAndExit()
            return START_NOT_STICKY
        } else if (intent?.action == ACTION_BUILD_LOG_INPUT) {
            val remoteInput = androidx.core.app.RemoteInput.getResultsFromIntent(intent)
            if (remoteInput != null) {
                val input = remoteInput.getCharSequence(EXTRA_TEXT_REPLY).toString()
                // Broadcast prompt back to Main App
                val promptIntent = Intent("com.hereliesaz.ideaz.AI_PROMPT").apply {
                    putExtra("PROMPT", input)
                    setPackage(packageName)
                }
                sendBroadcast(promptIntent)
            }
            return START_NOT_STICKY
        }

        // Start Foreground immediately
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

    /**
     * Updates the persistent notification with the latest log message.
     * Throttled to [NOTIFICATION_UPDATE_INTERVAL_MS] to avoid system spam.
     */
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
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                PendingIntent.FLAG_MUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            } else {
                PendingIntent.FLAG_UPDATE_CURRENT
            }
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
     * Wraps the provided [IBuildCallback] to implement log batching.
     * This prevents flooding the binder transaction buffer with individual log lines.
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
                    val versionCatalog = File(projectDir, "gradle/libs.versions.toml")
                    val depFile = if (versionCatalog.exists()) versionCatalog else File(projectDir, "dependencies.toml")
                    val resolver = HttpDependencyResolver(projectDir, depFile, localRepoDir, wrappedCallback)
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
                } else if (type == ProjectType.REACT_NATIVE) {
                    wrappedCallback.onLog("\n[IDE] 'npm install' is not currently supported on device. Dependencies are pre-bundled in the IDE runtime.")
                    updateNotification("Download finished (RN).")
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

                // Start Hot Reload Watcher
                startHotReloadWatcher(projectDir)
                val type = ProjectAnalyzer.detectProjectType(projectDir)

                val prefs = PreferenceManager.getDefaultSharedPreferences(this@BuildService)
                val localBuildEnabled = prefs.getBoolean(SettingsViewModel.KEY_ENABLE_LOCAL_BUILDS, false)

                // --- REMOTE BUILD CHECK ---
                // Remote builds are now handled by BuildDelegate/RemoteBuildManager.
                // This service is strictly for Local Builds.

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

                // --- REACT NATIVE BUILD ---
                if (type == ProjectType.REACT_NATIVE) {
                    // Integrated SimpleJsBundler for local build
                    val outputDir = File(buildDir, "react_native_dist")
                    val step = ReactNativeBuildStep(projectDir, outputDir)
                    val result = step.execute(wrappedCallback)
                    if (result.success && isActive) {
                        val bundleFile = File(outputDir, "index.android.bundle")
                        wrappedCallback.onSuccess(bundleFile.absolutePath)
                    } else if (isActive) {
                        wrappedCallback.onFailure(result.output)
                    }
                    return@launch
                }

                // --- ANDROID BUILD ---
                val versionCatalog = File(projectDir, "gradle/libs.versions.toml")
                val depFile = if (versionCatalog.exists()) versionCatalog else File(projectDir, "dependencies.toml")
                val resolver = HttpDependencyResolver(projectDir, depFile, localRepoDir, wrappedCallback)
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
                // Process AARs (Extract resources)
                val processAars = ProcessAars(resolver.resolvedArtifacts, buildDir, aapt2Path!!)
                val aarResult = processAars.execute(wrappedCallback)
                if (!aarResult.success && isActive) {
                    wrappedCallback.onFailure(aarResult.output)
                    return@launch
                }

                val aarJars = processAars.jars.joinToString(File.pathSeparator)
                val resolvedJars = resolver.resolvedClasspath

                // NEW: Hybrid Host Runtime (Inject Redwood/Zipline libs)
                val hybridRuntime = HybridToolchainManager.getHostRuntimeClasspath(filesDir, wrappedCallback)
                val hybridJars = hybridRuntime.joinToString(File.pathSeparator) { it.absolutePath }

                val fullClasspath = listOf(resolvedJars, aarJars, hybridJars)
                    .filter { it.isNotEmpty() }
                    .joinToString(File.pathSeparator)

                if (!isActive) return@launch

                val processedManifestPath = File(buildDir, "processed_manifest.xml").absolutePath

                // --- BUILD EXECUTION ---
                try {
                    injectResizeableActivity(projectDir)
                } catch (e: Exception) {
                    Log.w(TAG, "Failed to inject resizeableActivity", e)
                }

                val schemaType = detectSchema(projectDir)
                val generatedHostDir = File(buildDir, "generated/host")
                val buildSteps = mutableListOf<BuildStep>(
                     ProcessManifest(File(projectDir, "app/src/main/AndroidManifest.xml").absolutePath, processedManifestPath, packageName, MIN_SDK, TARGET_SDK),
                     Aapt2Compile(aapt2Path, File(projectDir, "app/src/main/res").absolutePath, File(buildDir, "compiled_res").absolutePath, MIN_SDK, TARGET_SDK),
                     Aapt2Link(aapt2Path, File(buildDir, "compiled_res").absolutePath, androidJarPath!!, processedManifestPath, File(buildDir, "app.apk").absolutePath, File(buildDir, "gen").absolutePath, MIN_SDK, TARGET_SDK, processAars.compiledAars, packageName)
                )

                if (schemaType != null) {
                    wrappedCallback.onLog("[IDE] Detected Schema: $schemaType. Enabling Hybrid Host generation.")
                    buildSteps.add(RedwoodCodegen(javaBinaryPath!!, schemaType, generatedHostDir, true, filesDir))
                }

                val sourceDirs = mutableListOf(File(projectDir, "app/src/main/java"))
                if (schemaType != null) {
                    sourceDirs.add(generatedHostDir)
                }

                // --- PYTHON INJECTION ---
                val pythonInjector = PythonInjector(resolver.resolvedArtifacts, buildDir)
                val pythonResult = pythonInjector.execute(wrappedCallback)
                if (!pythonResult.success && isActive) {
                    wrappedCallback.onFailure(pythonResult.output)
                    return@launch
                }

                buildSteps.add(KotlincCompile(kotlincJarPath!!, androidJarPath, sourceDirs, File(buildDir, "classes"), fullClasspath, javaBinaryPath!!))
                buildSteps.add(D8Compile(d8Path!!, javaBinaryPath, androidJarPath, File(buildDir, "classes").absolutePath, File(buildDir, "classes").absolutePath, fullClasspath))

                // Updated ApkBuild to include Native Libs and Assets
                buildSteps.add(ApkBuild(
                    finalApkPath = File(buildDir, "app-signed.apk").absolutePath,
                    resourcesApkPath = File(buildDir, "app.apk").absolutePath,
                    classesDir = File(buildDir, "classes").absolutePath,
                    jniLibsDir = File(buildDir, "intermediates/jniLibs").absolutePath,
                    assetsDir = File(buildDir, "intermediates/assets").absolutePath
                ))
                buildSteps.add(ApkSign(apkSignerPath!!, javaBinaryPath, keystorePath!!, ksPass, keyAlias, File(buildDir, "app-signed.apk").absolutePath))

                // --- GUEST BUILD ---
                if (schemaType != null) {
                    // Zipline functionality is currently disabled due to deprecation.
                    // Skipping guest build steps to save resources.
                    // TODO: Re-enable after updating Zipline/Redwood or fixing deprecation.
                    wrappedCallback.onLog("[IDE] Guest Build skipped (Zipline disabled).")
                }

                buildSteps.add(GenerateSourceMap(File(projectDir, "app/src/main/res"), buildDir, cacheDir))

                val buildOrchestrator = BuildOrchestrator(buildSteps)

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


    private fun detectSchema(projectDir: File): String? {
        val srcDir = File(projectDir, "app/src/main/java")
        if (!srcDir.exists()) return null

        // Simple heuristic: grep for @Schema
        val schemaFile = srcDir.walkTopDown().filter { it.isFile && it.extension == "kt" }
            .find { it.readText().contains("@Schema") }

        if (schemaFile != null) {
            val relative = schemaFile.relativeTo(srcDir).path.replace(File.separatorChar, '.')
            return relative.removeSuffix(".kt")
        }
        return null
    }

    private fun startHotReloadWatcher(projectDir: File) {
        fileObserver?.stopWatching()
        val pythonSrcDir = File(projectDir, "app/src/main/assets/python")
        if (!pythonSrcDir.exists()) return

        fileObserver = com.hereliesaz.ideaz.utils.RecursiveFileObserver(
            pythonSrcDir.absolutePath,
            android.os.FileObserver.CLOSE_WRITE or android.os.FileObserver.MODIFY
        ) { event, fullPath ->
            if (fullPath == null) return@RecursiveFileObserver
            val path = fullPath

            // Debounce or immediate? Immediate for now.
            // We need to read the file and send broadcast.
            // Cannot call suspend functions or IO on this thread safely without scope.
            serviceScope.launch(Dispatchers.IO) {
                try {
                    val file = File(path)
                    if (file.exists() && file.isFile) {
                        val content = file.readText()
                        val packageName = ProjectAnalyzer.detectPackageName(projectDir)

                        if (packageName != null) {
                            // Calculate relative path for identification
                            val relativePath = file.relativeTo(pythonSrcDir).path

                            val intent = Intent("com.ideaz.ACTION_RELOAD_PYTHON")
                            intent.putExtra("path", relativePath)
                            intent.putExtra("content", content)
                            intent.setPackage(packageName)
                            sendBroadcast(intent)
                            Log.d(TAG, "Sent Hot Reload broadcast for $relativePath to $packageName")
                            updateNotification("Hot Reload: $relativePath")
                        }
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Hot Reload failed", e)
                }
            }
        }
        fileObserver?.startWatching()
        Log.d(TAG, "Started Recursive Hot Reload watcher on ${pythonSrcDir.absolutePath}")
    }
}
