package com.hereliesaz.ideaz.services

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.preference.PreferenceManager
import com.hereliesaz.ideaz.IBuildCallback
import com.hereliesaz.ideaz.IBuildService
import com.hereliesaz.ideaz.MainActivity
import com.hereliesaz.ideaz.R
import com.hereliesaz.ideaz.api.CreateWorkflowDispatchRequest
import com.hereliesaz.ideaz.api.GitHubApiClient
import com.hereliesaz.ideaz.ui.SettingsViewModel
import com.hereliesaz.ideaz.utils.ApkInstaller
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.io.File
import java.io.FileOutputStream
import java.net.URL

class BuildService : Service() {

    private val binder = object : IBuildService.Stub() {
        override fun startBuild(projectPath: String, callback: IBuildCallback) {
            this@BuildService.startBuild(projectPath, callback)
        }

        override fun updateNotification(message: String) {
            this@BuildService.updateNotification(message)
        }

        override fun cancelBuild() {
            this@BuildService.cancelBuild()
        }
    }

    private val serviceScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var currentCallback: IBuildCallback? = null
    private var isBuilding = false

    companion object {
        private const val TAG = "BuildService"
        private const val NOTIFICATION_ID = 1
        private const val CHANNEL_ID = "build_service_channel"
    }

    override fun onBind(intent: Intent?): IBinder {
        return binder
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onDestroy() {
        super.onDestroy()
        serviceScope.cancel()
    }

    private fun startBuild(projectPath: String, callback: IBuildCallback) {
        if (isBuilding) {
            Log.w(TAG, "Build already in progress")
            return
        }
        isBuilding = true
        currentCallback = callback

        // Start Foreground
        val notification = createNotification("Starting Remote Build...")
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }

        serviceScope.launch {
            try {
                executeRemoteBuild(projectPath)
            } catch (e: Exception) {
                Log.e(TAG, "Build failed", e)
                try {
                    callback.onFailure("Build Service Error: ${e.message}")
                } catch (e2: Exception) {}
            } finally {
                isBuilding = false
                stopForeground(true)
            }
        }
    }

    private fun updateNotification(message: String) {
        val notificationManager = getSystemService(NotificationManager::class.java)
        val notification = createNotification(message)
        notificationManager.notify(NOTIFICATION_ID, notification)

        try {
            currentCallback?.onLog(message)
        } catch (e: Exception) {}
    }

    private fun cancelBuild() {
        isBuilding = false
        stopForeground(true)
        // TODO: Cancel GitHub workflow?
    }

    private suspend fun executeRemoteBuild(projectPath: String) {
        val prefs = PreferenceManager.getDefaultSharedPreferences(this)
        val token = prefs.getString(SettingsViewModel.KEY_GITHUB_TOKEN, null)
        val user = prefs.getString(SettingsViewModel.KEY_GITHUB_USER, null)
        val appName = prefs.getString(SettingsViewModel.KEY_APP_NAME, null) // Assuming Repo name = App Name
        val branch = prefs.getString(SettingsViewModel.KEY_BRANCH_NAME, "main")

        if (token.isNullOrBlank() || user.isNullOrBlank() || appName.isNullOrBlank()) {
            throw IllegalStateException("Missing GitHub Configuration (Token, User, or AppName)")
        }

        val api = GitHubApiClient.createService(token)

        // 1. Get HEAD SHA
        updateNotification("Fetching branch details...")
        val branchInfo = api.getBranch(user, appName, branch ?: "main")
        val headSha = branchInfo.commit.sha

        // 2. Trigger Workflow
        updateNotification("Triggering Workflow...")
        // Workflow ID: android_ci_jules.yml. In API, we can use the filename.
        try {
            api.createWorkflowDispatch(user, appName, "android_ci_jules.yml", CreateWorkflowDispatchRequest(ref = branch ?: "main"))
        } catch (e: Exception) {
            // If it fails, maybe it doesn't exist? Or maybe we rely on Push event?
            Log.w(TAG, "Workflow dispatch failed (maybe not setup?): ${e.message}")
            // Proceed to polling, maybe the Push triggered it.
        }

        // 3. Poll for Run
        updateNotification("Waiting for Workflow Run...")
        var runId: Long? = null
        var attempts = 0
        while (runId == null && attempts < 10) {
            delay(3000)
            val runs = api.listWorkflowRuns(user, appName, branch = branch)
            // Find run that matches HEAD SHA and is recent
            val match = runs.workflowRuns.firstOrNull {
                it.headSha == headSha && it.status != "completed" // Prefer active
            } ?: runs.workflowRuns.firstOrNull {
                it.headSha == headSha // Or just matching SHA
            }

            if (match != null) {
                runId = match.id
            }
            attempts++
        }

        if (runId == null) {
            throw IllegalStateException("Could not find GitHub Action run for commit $headSha")
        }

        // 4. Poll for Completion
        var status = "queued"
        var conclusion: String? = null

        while (status != "completed") {
            delay(5000)
            // Fetch single run details? API doesn't have getRun, use list or add getRun.
            // Using list for now as I didn't add getRun.
            val runs = api.listWorkflowRuns(user, appName) // Filter?
            val run = runs.workflowRuns.find { it.id == runId }

            if (run != null) {
                status = run.status
                conclusion = run.conclusion
                updateNotification("Build Status: ${status} (${conclusion ?: "..."})")
            }
        }

        if (conclusion != "success") {
            // Fetch logs?
             try {
                 val logBody = api.getWorkflowRunLogs(user, appName, runId)
                 // This returns a zip usually, or text?
                 // getWorkflowRunLogs redirects to a URL. Retrofit follows redirects.
                 // It might be a zip file.
                 // For now, just report failure.
             } catch (e: Exception) {}
            throw IllegalStateException("Remote Build Failed with conclusion: $conclusion")
        }

        // 5. Download Artifact
        updateNotification("Build Success. Checking Releases...")
        // We look for 'latest-debug' release asset
        val releases = api.getReleases(user, appName)
        val release = releases.find { it.tagName == "latest-debug" }
            ?: throw IllegalStateException("Could not find 'latest-debug' release")

        val asset = release.assets.find { it.name.endsWith(".apk") }
            ?: throw IllegalStateException("No APK asset found in 'latest-debug' release")

        updateNotification("Downloading APK...")
        val downloadUrl = asset.browserDownloadUrl

        // Download file
        val apkFile = File(cacheDir, "update.apk")
        URL(downloadUrl).openStream().use { input ->
            FileOutputStream(apkFile).use { output ->
                input.copyTo(output)
            }
        }

        // 6. Install
        updateNotification("Installing...")
        ApkInstaller.installApk(this, apkFile.absolutePath)

        currentCallback?.onSuccess(apkFile.absolutePath)
    }

    private fun createNotification(text: String): Notification {
        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        val pendingIntent = PendingIntent.getActivity(this, 0, intent, PendingIntent.FLAG_IMMUTABLE)

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("IDEaz Remote Build")
            .setContentText(text)
            .setSmallIcon(R.mipmap.ic_launcher) // Ensure this exists or use android.R.drawable.ic_menu_upload
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .build()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val name = "Remote Build Service"
            val descriptionText = "Shows status of remote builds"
            val importance = NotificationManager.IMPORTANCE_LOW
            val channel = NotificationChannel(CHANNEL_ID, name, importance).apply {
                description = descriptionText
            }
            val notificationManager = getSystemService(NotificationManager::class.java)
            notificationManager.createNotificationChannel(channel)
        }
    }
}
