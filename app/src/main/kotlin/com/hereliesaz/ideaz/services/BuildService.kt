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
import com.hereliesaz.ideaz.git.GitManager
import com.hereliesaz.ideaz.ui.SettingsViewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.ArrayDeque

/**
 * Foreground [Service] that owns the persistent build notification.
 *
 * The on-device build pipeline has been removed; remote GitHub Actions builds
 * are driven directly from [com.hereliesaz.ideaz.ui.delegates.BuildDelegate]
 * via [com.hereliesaz.ideaz.buildlogic.RemoteBuildManager]. This service
 * remains because the foreground notification (with "Sync & Exit" and the
 * AI prompt RemoteInput) is still useful, and the AIDL surface is still bound
 * by [BuildDelegate] for log forwarding and cancellation.
 */
class BuildService : Service() {
    companion object {
        private const val TAG = "BuildService"
        private const val NOTIFICATION_CHANNEL_ID = "IDEAZ_BUILD_CHANNEL_ID"
        private const val NOTIFICATION_ID = 1

        /** Max lines to keep in the notification expanded view. */
        private const val MAX_LOG_LINES = 50

        // Notification Actions
        private const val ACTION_SYNC_AND_EXIT = "SYNC_AND_EXIT"
        private const val ACTION_BUILD_LOG_INPUT = "BUILD_LOG_INPUT"
        private const val EXTRA_TEXT_REPLY = "KEY_TEXT_REPLY"

        private const val NOTIFICATION_UPDATE_INTERVAL_MS = 1000L
    }

    private val logBuffer = ArrayDeque<String>(MAX_LOG_LINES)
    private val serviceScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var currentProjectPath: String? = null
    private var lastNotificationUpdate = 0L
    private var pendingNotificationUpdate: Job? = null

    private val binder = object : IBuildService.Stub() {
        override fun startBuild(projectPath: String, callback: IBuildCallback) {
            // On-device builds were removed. Remote builds run from BuildDelegate;
            // this method only updates the persistent notification so the user can
            // see something happened, then signals failure so the caller falls back
            // to its remote path.
            currentProjectPath = projectPath
            callback.onFailure(
                "On-device build pipeline has been removed. " +
                "Use remote (GitHub Actions) builds via Project settings."
            )
        }

        override fun downloadDependencies(projectPath: String, callback: IBuildCallback) {
            currentProjectPath = projectPath
            callback.onFailure(
                "On-device dependency resolution has been removed. " +
                "Dependencies are resolved by remote (GitHub Actions) builds."
            )
        }

        override fun updateNotification(message: String) = this@BuildService.updateNotification(message)
        override fun cancelBuild() {
            // Nothing to cancel locally now.
        }
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
                    setPackage(packageName)
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

    /**
     * Updates the persistent notification with the latest log message. Throttled
     * to avoid spamming the notification system.
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
}
