package com.hereliesaz.ideaz.services

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.preference.PreferenceManager
import com.hereliesaz.ideaz.IBuildCallback
import com.hereliesaz.ideaz.IBuildService
import com.hereliesaz.ideaz.MainActivity
import com.hereliesaz.ideaz.features.preview.ContainerActivity
import com.hereliesaz.ideaz.git.GitManager
import com.hereliesaz.ideaz.utils.ApkInstaller
import com.hereliesaz.ideaz.ui.SettingsViewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import org.json.JSONObject
import java.io.BufferedInputStream
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.util.ArrayDeque
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream

/**
 * The Logistics Manager.
 * Instead of building locally, this service:
 * 1. Injects container-safe flags into the manifest.
 * 2. Pushes changes to GitHub.
 * 3. Polls GitHub Actions for the build result.
 * 4. Downloads and installs the artifact.
 */
class BuildService : Service() {

    companion object {
        private const val TAG = "BuildService"
        private const val NOTIFICATION_CHANNEL_ID = "IDEAZ_BUILD_CHANNEL_ID"
        private const val NOTIFICATION_ID = 1
        private const val MAX_LOG_LINES = 50
        private const val POLL_INTERVAL_MS = 10_000L // 10 seconds
        private const val ACTION_CANCEL_BUILD = "CANCEL_BUILD"
    }

    private val logBuffer = ArrayDeque<String>(MAX_LOG_LINES)
    private val serviceScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var buildJob: Job? = null

    private val binder = object : IBuildService.Stub() {
        override fun startBuild(projectPath: String, callback: IBuildCallback) =
            this@BuildService.startRemoteBuild(projectPath, callback)

        override fun downloadDependencies(projectPath: String, callback: IBuildCallback) {
            // In a remote build scenario, we don't strictly need local dependencies,
            // but we might want them for code completion/analysis.
            // For now, we'll just log that we are skipping local resolution.
            callback.onLog("Skipping local dependency download (Remote Build active).")
        }

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
        if (intent?.action == ACTION_CANCEL_BUILD) {
            cancelBuild()
        }
        val notification = createNotificationBuilder()
            .setContentText("Ready for remote build.")
            .build()
        startForeground(NOTIFICATION_ID, notification)
        return START_NOT_STICKY
    }

    override fun onBind(intent: Intent?): IBinder = binder

    private fun updateNotification(message: String) {
        synchronized(logBuffer) {
            message.lines().forEach { line ->
                if (line.isNotBlank()) {
                    if (logBuffer.size >= MAX_LOG_LINES) logBuffer.removeFirst()
                    logBuffer.addLast(line)
                }
            }
        }
        val latestLog = synchronized(logBuffer) { logBuffer.lastOrNull() } ?: "Processing..."
        val bigText = synchronized(logBuffer) { logBuffer.joinToString("\n") }

        val notification = createNotificationBuilder()
            .setContentText(latestLog)
            .setStyle(NotificationCompat.BigTextStyle().bigText(bigText))
            .build()
        getSystemService(NotificationManager::class.java).notify(NOTIFICATION_ID, notification)
    }

    private fun createNotificationBuilder(): NotificationCompat.Builder {
        val contentIntent = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java).apply { flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP },
            PendingIntent.FLAG_IMMUTABLE
        )

        val cancelIntent = Intent(this, BuildService::class.java).apply { action = ACTION_CANCEL_BUILD }
        val cancelPendingIntent = PendingIntent.getService(this, 1, cancelIntent, PendingIntent.FLAG_IMMUTABLE)

        return NotificationCompat.Builder(this, NOTIFICATION_CHANNEL_ID)
            .setContentTitle("IDEaz Remote Build")
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentIntent(contentIntent)
            .addAction(android.R.drawable.ic_menu_close_clear_cancel, "Cancel", cancelPendingIntent)
            .setOngoing(true)
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(NOTIFICATION_CHANNEL_ID, "IDEaz Build Service", NotificationManager.IMPORTANCE_DEFAULT)
            getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }
    }

    private fun cancelBuild() {
        buildJob?.cancel()
        buildJob = null
        updateNotification("Build cancelled.")
    }

    private fun startRemoteBuild(projectPath: String, callback: IBuildCallback) {
        cancelBuild()
        buildJob = serviceScope.launch(Dispatchers.IO) {
            try {
                updateNotification("Initializing remote build sequence...")
                callback.onLog("\n--- Remote Build Sequence Initiated ---")

                val prefs = PreferenceManager.getDefaultSharedPreferences(this@BuildService)
                val token = prefs.getString(SettingsViewModel.KEY_GITHUB_TOKEN, "") ?: ""
                val username = prefs.getString(SettingsViewModel.KEY_GITHUB_USER, "") ?: ""
                val repoUrl = prefs.getString(SettingsViewModel.KEY_REPO_URL, "") ?: ""

                // Extract Owner/Repo from URL (naive parsing)
                // Expected format: https://github.com/Owner/Repo(.git)
                val repoPath = repoUrl.removeSuffix(".git").substringAfter("github.com/")
                if (token.isBlank() || username.isBlank() || repoPath.isBlank()) {
                    callback.onFailure("Missing GitHub credentials. Check Settings.")
                    return@launch
                }

                val projectDir = File(projectPath)
                if (!projectDir.exists()) {
                    callback.onFailure("Project path does not exist.")
                    return@launch
                }

                // 1. Prepare Local Source (Inject Container Flags)
                callback.onLog("Injecting container compatibility flags...")
                injectResizeableFlag(File(projectDir, "app/src/main/AndroidManifest.xml"))

                // 2. Commit and Push
                callback.onLog("Syncing changes to GitHub...")
                val git = GitManager(projectDir)
                if (git.hasChanges()) {
                    git.addAll()
                    git.commit("Auto-build trigger from IDEaz")
                }

                // We need the commit SHA to track the specific build
                val currentHead = git.getHeadSha()
                callback.onLog("Pushing commit: ${currentHead.take(7)}...")
                git.push(username, token)

                // 3. Poll GitHub Actions
                callback.onLog("Waiting for GitHub Actions...")
                updateNotification("Waiting for GitHub to accept the burden...")

                var runId: Long = -1
                var status = "queued"
                var conclusion = "null"
                var attempt = 0
                val maxAttempts = 60 // ~10 minutes

                while (isActive && attempt < maxAttempts) {
                    // Fetch runs triggered by this commit
                    val response = githubApiRequest(
                        "https://api.github.com/repos/$repoPath/actions/runs?head_sha=$currentHead",
                        token
                    )

                    val runs = JSONObject(response).optJSONArray("workflow_runs")
                    if (runs != null && runs.length() > 0) {
                        val latestRun = runs.getJSONObject(0)
                        runId = latestRun.getLong("id")
                        status = latestRun.getString("status") // queued, in_progress, completed
                        conclusion = latestRun.optString("conclusion", "null") // success, failure

                        val workflowName = latestRun.getString("name")
                        callback.onLog("Remote Build ($workflowName): $status")
                        updateNotification("Remote Build: $status")

                        if (status == "completed") {
                            break
                        }
                    } else {
                        callback.onLog("No workflow run found yet. Retrying...")
                    }

                    delay(POLL_INTERVAL_MS)
                    attempt++
                }

                if (!isActive) return@launch
                if (status != "completed") {
                    callback.onFailure("Build timed out or stuck in $status.")
                    return@launch
                }

                if (conclusion != "success") {
                    callback.onFailure("Remote build FAILED. Check GitHub for logs.")
                    return@launch
                }

                // 4. Download Artifacts
                callback.onLog("Build Successful! Fetching artifacts...")
                updateNotification("Downloading APK...")

                val artifactsResponse = githubApiRequest(
                    "https://api.github.com/repos/$repoPath/actions/runs/$runId/artifacts",
                    token
                )

                val artifacts = JSONObject(artifactsResponse).optJSONArray("artifacts")
                var downloadUrl: String? = null

                // Find an artifact that looks like an APK (usually zipped)
                if (artifacts != null) {
                    for (i in 0 until artifacts.length()) {
                        val art = artifacts.getJSONObject(i)
                        val name = art.getString("name")
                        if (name.contains("apk") || name.contains("debug") || name.contains("release")) {
                            downloadUrl = art.getString("archive_download_url")
                            break
                        }
                    }
                }

                if (downloadUrl == null) {
                    callback.onFailure("No APK artifact found in the completed build.")
                    return@launch
                }

                val downloadDir = File(filesDir, "downloads").apply { mkdirs() }
                val zipFile = File(downloadDir, "build_artifact.zip")

                downloadFile(downloadUrl, zipFile, token)

                // 5. Extract and Install
                callback.onLog("Extracting...")
                val apkFile = extractApkFromZip(zipFile, downloadDir)

                if (apkFile != null) {
                    callback.onSuccess(apkFile.absolutePath)
                    updateNotification("Installing...")

                    // Install logic
                    ApkInstaller.installApk(this@BuildService, apkFile.absolutePath)

                    // Launch Container
                    val launchContainerIntent = Intent(this@BuildService, ContainerActivity::class.java).apply {
                        // Package name might be guessed or parsed.
                        // Since we don't have aapt locally to dump badging, we assume the user
                        // configured the standard package name in the project.
                        // We can try to parse it from the manifest file on disk.
                        val pkg = ProjectAnalyzer.detectPackageName(projectDir)
                        putExtra(ContainerActivity.EXTRA_PACKAGE_NAME, pkg)
                        flags = Intent.FLAG_ACTIVITY_NEW_TASK
                    }
                    startActivity(launchContainerIntent)

                } else {
                    callback.onFailure("Downloaded artifact did not contain an .apk file.")
                }

            } catch (e: Exception) {
                Log.e(TAG, "Remote build error", e)
                callback.onFailure("Error: ${e.message}")
            }
        }
    }

    private fun githubApiRequest(urlStr: String, token: String): String {
        val url = URL(urlStr)
        val conn = url.openConnection() as HttpURLConnection
        conn.requestMethod = "GET"
        conn.setRequestProperty("Authorization", "Bearer $token")
        conn.setRequestProperty("Accept", "application/vnd.github.v3+json")
        conn.setRequestProperty("User-Agent", "IDEaz-Android-App")

        if (conn.responseCode !in 200..299) {
            throw RuntimeException("GitHub API Error: ${conn.responseCode} ${conn.responseMessage}")
        }

        return conn.inputStream.bufferedReader().use { it.readText() }
    }

    private fun downloadFile(urlStr: String, destFile: File, token: String) {
        val url = URL(urlStr)
        val conn = url.openConnection() as HttpURLConnection
        conn.requestMethod = "GET"
        conn.setRequestProperty("Authorization", "Bearer $token")
        conn.setRequestProperty("User-Agent", "IDEaz-Android-App")

        // GitHub artifact downloads often redirect
        conn.instanceFollowRedirects = true

        if (conn.responseCode !in 200..299) {
            throw RuntimeException("Download failed: ${conn.responseCode}")
        }

        conn.inputStream.use { input ->
            FileOutputStream(destFile).use { output ->
                input.copyTo(output)
            }
        }
    }

    private fun extractApkFromZip(zipFile: File, outputDir: File): File? {
        ZipInputStream(BufferedInputStream(zipFile.inputStream())).use { zis ->
            var entry: ZipEntry?
            while (zis.nextEntry.also { entry = it } != null) {
                if (entry!!.name.endsWith(".apk")) {
                    val outFile = File(outputDir, entry!!.name)
                    FileOutputStream(outFile).use { fos ->
                        zis.copyTo(fos)
                    }
                    return outFile
                }
            }
        }
        return null
    }

    // Helper from previous turn logic to detect package name if not using AAPT
    object ProjectAnalyzer {
        fun detectPackageName(projectDir: File): String {
            val manifest = File(projectDir, "app/src/main/AndroidManifest.xml")
            if (manifest.exists()) {
                val content = manifest.readText()
                val match = Regex("package=\"([^\"]+)\"").find(content)
                if (match != null) return match.groupValues[1]
            }
            return "com.example.app" // Fallback
        }
    }

    /**
     * Ensures the app can be resized (required for VirtualDisplay containment)
     */
    private fun injectResizeableFlag(manifestFile: File) {
        try {
            if (!manifestFile.exists()) return
            var content = manifestFile.readText()

            if (!content.contains("android:resizeableActivity")) {
                if (content.contains("<activity")) {
                    content = content.replaceFirst("<activity", "<activity android:resizeableActivity=\"true\"")
                } else if (content.contains("<application")) {
                    content = content.replaceFirst("<application", "<application android:resizeableActivity=\"true\"")
                }
                manifestFile.writeText(content)
                Log.d(TAG, "Injected resizeableActivity=true into manifest")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to inject resizeable flag", e)
        }
    }
}