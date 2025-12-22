package com.example.pythonapp

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import com.chaquo.python.Python
import kotlin.concurrent.thread

class PythonService : Service() {

    companion object {
        private const val TAG = "PythonService"
        private const val CHANNEL_ID = "PythonServiceChannel"
        private const val NOTIFICATION_ID = 101
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        startForeground(NOTIFICATION_ID, createNotification())

        // Initialize Python and start server
        try {
            PythonBootstrapper.initialize(this)
            startPythonServer()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start Python", e)
        }
    }

    private fun startPythonServer() {
        thread {
            try {
                Log.d(TAG, "Starting main.py...")
                val python = Python.getInstance()
                val mainModule = python.getModule("main")
                // Assuming main.py has a run_server function, or we just execute the script.
                // If main.py has `if __name__ == "__main__": run_server()`, importing it won't run it.
                // Better to explicitly call a function.
                if (mainModule.containsKey("run_server")) {
                    mainModule.callAttr("run_server")
                } else {
                     Log.w(TAG, "main.py does not have a run_server function")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Python server crashed", e)
            }
        }
    }

    // TODO: Implement BroadcastReceiver for Hot Reload here

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? {
        return null
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Python Backend Service",
                NotificationManager.IMPORTANCE_LOW
            )
            getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }
    }

    private fun createNotification() = NotificationCompat.Builder(this, CHANNEL_ID)
        .setContentTitle("Python Backend Running")
        .setContentText("Hosting local UI server on port 5000")
        .setSmallIcon(android.R.drawable.ic_dialog_info)
        .build()
}
