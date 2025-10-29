package com.hereliesaz.peridiumide.services

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat

class BuildService : Service() {

    companion object {
        private const val NOTIFICATION_CHANNEL_ID = "PERIDIUM_BUILD_CHANNEL_ID"
        private const val NOTIFICATION_ID = 1
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // This service will be started and managed by the Host App via an AIDL interface.
        // The foreground notification is still important to prevent the OS from killing the process.
        val notification = NotificationCompat.Builder(this, NOTIFICATION_CHANNEL_ID)
            .setContentTitle("Peridium IDE")
            .setContentText("Build Service is running.")
            .setSmallIcon(android.R.drawable.ic_dialog_info) // Placeholder icon
            .build()

        startForeground(NOTIFICATION_ID, notification)

        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? {
        // TODO: Implement the AIDL Binder interface for the Host App to communicate with this service.
        return null
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                NOTIFICATION_CHANNEL_ID,
                "Peridium Build Service",
                NotificationManager.IMPORTANCE_DEFAULT
            )
            val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.createNotificationChannel(channel)
        }
    }

    /**
     * This method will be triggered via an AIDL call from the Host App.
     * It will orchestrate the "No-Gradle" build pipeline.
     */
    private fun startBuild(projectPath: String) {
        // TODO: Implement the full "No-Gradle" build sequence:
        // 1. Acquire and prepare build tools.
        // 2. Run aapt2 compile.
        // 3. Run aapt2 link.
        // 4. Run kotlinc.
        // 5. Run d8.
        // 6. Package the final APK.
        // 7. Sign the APK.
        // 8. Report status back to Host App via callback.
        println("PeridiumBuildService: Received request to build project at $projectPath")
    }
}
