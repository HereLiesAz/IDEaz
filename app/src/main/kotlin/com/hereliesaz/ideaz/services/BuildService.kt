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
import com.hereliesaz.ideaz.features.preview.ContainerActivity
import com.hereliesaz.ideaz.git.GitManager
import com.hereliesaz.ideaz.utils.ApkInstaller
import com.hereliesaz.ideaz.ui.SettingsViewModel
import kotlinx.coroutines.*
import org.json.JSONObject
import java.io.BufferedInputStream
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.util.ArrayDeque
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream

class BuildService : Service() {

    companion object {
        private const val TAG = "BuildService"
        private const val NOTIFICATION_CHANNEL_ID = "IDEAZ_BUILD_CHANNEL_ID"
        private const val NOTIFICATION_ID = 1
        private const val POLL_INTERVAL_MS = 10_000L // 10 seconds
        private const val MAX_LOG_LINES = 50
    }

    private val logBuffer = ArrayDeque<String>(MAX_LOG_LINES)
    private val serviceScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var buildJob: Job? = null

    // AIDL Stub for interaction with BuildDelegate
    private val binder = object : IBuildService.Stub() {
        override fun startBuild(projectPath: String, callback: IBuildCallback) =
            this@BuildService.startRemoteMonitor(projectPath, callback)

        override fun downloadDependencies(projectPath: String, callback: IBuildCallback) {
            // No-op for remote build
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

    override fun onBind(intent: Intent?): IBinder = binder

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val notification = createNotificationBuilder()
            .setContentText("Build Monitor Active")
            .build()
        startForeground(NOTIFICATION_ID, notification)
        return START_NOT_STICKY
    }

    private fun startRemoteMonitor(projectPath: String, callback: IBuildCallback) {
        cancelBuild()
        buildJob = serviceScope.launch(Dispatchers.IO) {
            try {
                updateNotification("Syncing & Initializing Monitor...")
                callback.onLog("--- Remote Build Sequence Started ---")

                val prefs = PreferenceManager.getDefaultSharedPreferences(this@BuildService)
                val token = prefs.getString(SettingsViewModel.KEY_GITHUB_TOKEN, "") ?: ""
                val username = prefs.getString(SettingsViewModel.KEY_GITHUB_USER, "") ?: ""
                // Get repo name from settings or project folder name
                val repoName = prefs.getString(SettingsViewModel.KEY_APP_NAME, File(projectPath).name) ?: "IDEaz"

                if (token.isBlank() || username.isBlank()) {
                    callback.onFailure("Missing GitHub credentials.")
                    return@launch
                }

                val projectDir = File(projectPath)

                // 1. Ensure Manifest is Container-Ready
                // Even if Jules commits, we enforce this flag locally before any sync happens.
                injectResizeableFlag(File(projectDir, "app/src/main/AndroidManifest.xml"), callback)

                // 2. Sync Local Changes (If any)
                // We assume Jules might have pushed, but we also push local tweaks.
                val git = GitManager(projectDir)
                if (git.hasChanges()) {
                    callback.onLog("Pushing local configuration changes...")
                    git.addAll()
                    git.commit("IDEaz: Pre-build configuration injection")
                    git.push(username, token)
                }

                val currentHead = git.getHeadSha()
                callback.onLog("Tracking commit: ${currentHead.take(7)}")

                // 3. Poll GitHub Actions
                callback.onLog("Polling GitHub Actions for workflow...")
                updateNotification("Waiting for GitHub...")

                var runId: Long = -1
                var attempt = 0
                val maxAttempts = 60

                // Polling Loop
                while (isActive && attempt < maxAttempts) {
                    val url = "https://api.github.com/repos/$username/$repoName/actions/runs?head_sha=$currentHead"
                    val response = githubApiRequest(url, token)

                    val runs = JSONObject(response).optJSONArray("workflow_runs")
                    if (runs != null && runs.length() > 0) {
                        val latestRun = runs.getJSONObject(0)
                        runId = latestRun.getLong("id")
                        val status = latestRun.getString("status")
                        val conclusion = latestRun.optString("conclusion", "null")

                        updateNotification("Remote Status: $status")
                        callback.onLog("Workflow $status...")

                        if (status == "completed") {
                            if (conclusion == "success") break
                            else {
                                callback.onFailure("Remote build failed with conclusion: $conclusion")
                                return@launch
                            }
                        }
                    }
                    delay(POLL_INTERVAL_MS)
                    attempt++
                }

                if (runId == -1L) {
                    callback.onFailure("Timeout: No workflow run found for this commit.")
                    return@launch
                }

                // 4. Download Artifact
                updateNotification("Downloading APK...")
                callback.onLog("Build success. Fetching artifacts...")

                val artifactsUrl = "https://api.github.com/repos/$username/$repoName/actions/runs/$runId/artifacts"
                val artResponse = githubApiRequest(artifactsUrl, token)
                val artifacts = JSONObject(artResponse).optJSONArray("artifacts")

                var downloadUrl: String? = null
                if (artifacts != null) {
                    for (i in 0 until artifacts.length()) {
                        val art = artifacts.getJSONObject(i)
                        val name = art.getString("name")
                        // Look for standard artifact names
                        if (name.contains("apk") || name.contains("debug") || name.contains("release")) {
                            downloadUrl = art.getString("archive_download_url")
                            break
                        }
                    }
                }

                if (downloadUrl == null) {
                    callback.onFailure("No APK artifact found in build output.")
                    return@launch
                }

                val downloadDir = File(filesDir, "downloads").apply { mkdirs() }
                val zipFile = File(downloadDir, "artifact.zip")
                downloadFile(downloadUrl, zipFile, token)

                // 5. Extract & Install
                callback.onLog("Extracting...")
                val apkFile = extractApkFromZip(zipFile, downloadDir)

                if (apkFile != null) {
                    callback.onLog("Installing APK...")
                    updateNotification("Installing...")

                    // Install logic
                    ApkInstaller.installApk(this@BuildService, apkFile.absolutePath)

                    callback.onSuccess(apkFile.absolutePath)

                    // 6. Launch Container
                    val pkgName = detectPackageName(projectDir)
                    val launchIntent = Intent(this@BuildService, ContainerActivity::class.java).apply {
                        putExtra(ContainerActivity.EXTRA_PACKAGE_NAME, pkgName)
                        flags = Intent.FLAG_ACTIVITY_NEW_TASK
                    }
                    startActivity(launchIntent)

                } else {
                    callback.onFailure("Downloaded zip contained no APK.")
                }

            } catch (e: Exception) {
                Log.e(TAG, "Remote monitor error", e)
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

        if (conn.responseCode !in 200..299) {
            throw RuntimeException("GitHub API ${conn.responseCode}: ${conn.responseMessage}")
        }
        return conn.inputStream.bufferedReader().readText()
    }

    private fun downloadFile(urlStr: String, destFile: File, token: String) {
        val url = URL(urlStr)
        val conn = url.openConnection() as HttpURLConnection
        conn.requestMethod = "GET"
        conn.setRequestProperty("Authorization", "Bearer $token")
        conn.instanceFollowRedirects = true

        if (conn.responseCode !in 200..299) throw RuntimeException("Download failed: ${conn.responseCode}")

        conn.inputStream.use { input ->
            FileOutputStream(destFile).use { output -> input.copyTo(output) }
        }
    }

    private fun extractApkFromZip(zipFile: File, outputDir: File): File? {
        ZipInputStream(BufferedInputStream(zipFile.inputStream())).use { zis ->
            var entry: ZipEntry?
            while (zis.nextEntry.also { entry = it } != null) {
                if (entry!!.name.endsWith(".apk")) {
                    val outFile = File(outputDir, entry!!.name)
                    FileOutputStream(outFile).use { fos -> zis.copyTo(fos) }
                    return outFile
                }
            }
        }
        return null
    }

    private fun injectResizeableFlag(manifestFile: File, callback: IBuildCallback) {
        try {
            if (!manifestFile.exists()) return
            var content = manifestFile.readText()
            if (!content.contains("android:resizeableActivity")) {
                callback.onLog("Injecting android:resizeableActivity=\"true\"...")
                // Simple regex injection into <application> tag
                content = content.replaceFirst("<application", "<application android:resizeableActivity=\"true\"")
                manifestFile.writeText(content)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Manifest injection failed", e)
        }
    }

    private fun detectPackageName(projectDir: File): String {
        val manifest = File(projectDir, "app/src/main/AndroidManifest.xml")
        if (manifest.exists()) {
            val match = Regex("package=\"([^\"]+)\"").find(manifest.readText())
            if (match != null) return match.groupValues[1]
        }
        return "com.example.app"
    }

    // Notification Helpers (Standard Boilerplate)
    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(NOTIFICATION_CHANNEL_ID, "IDEaz Builds", NotificationManager.IMPORTANCE_LOW)
            getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }
    }

    private fun updateNotification(msg: String) {
        val notif = createNotificationBuilder().setContentText(msg).build()
        getSystemService(NotificationManager::class.java).notify(NOTIFICATION_ID, notif)
    }

    private fun createNotificationBuilder(): NotificationCompat.Builder {
        val intent = Intent(this, MainActivity::class.java)
        val pending = PendingIntent.getActivity(this, 0, intent, PendingIntent.FLAG_IMMUTABLE)
        return NotificationCompat.Builder(this, NOTIFICATION_CHANNEL_ID)
            .setContentTitle("IDEaz Build Service")
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setContentIntent(pending)
    }

    private fun cancelBuild() {
        buildJob?.cancel()
        updateNotification("Cancelled")
    }
}