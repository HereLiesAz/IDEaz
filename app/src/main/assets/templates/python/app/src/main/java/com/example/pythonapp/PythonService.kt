package com.example.pythonapp

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import com.chaquo.python.Python
import java.io.File
import java.io.FileOutputStream
import kotlin.concurrent.thread

class PythonService : Service() {

    companion object {
        private const val TAG = "PythonService"
        private const val CHANNEL_ID = "PythonServiceChannel"
        private const val NOTIFICATION_ID = 101
        private const val ACTION_RELOAD = "com.ideaz.ACTION_RELOAD_PYTHON"
    }

    private val hotReloadReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (intent.action == ACTION_RELOAD) {
                val path = intent.getStringExtra("path")
                val content = intent.getStringExtra("content")
                if (path != null && content != null) {
                    handleHotReload(path, content)
                }
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        startForeground(NOTIFICATION_ID, createNotification())

        // Register Hot Reload Receiver
        val filter = IntentFilter(ACTION_RELOAD)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(hotReloadReceiver, filter, Context.RECEIVER_EXPORTED)
        } else {
            registerReceiver(hotReloadReceiver, filter)
        }

        // Initialize Python and start server
        try {
            PythonBootstrapper.initialize(this)
            startPythonServer()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start Python", e)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        unregisterReceiver(hotReloadReceiver)
    }

    private fun startPythonServer() {
        thread {
            try {
                Log.d(TAG, "Starting main.py...")
                val python = Python.getInstance()
                val mainModule = python.getModule("main")
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

    private fun handleHotReload(relPath: String, content: String) {
        try {
            Log.d(TAG, "Hot Reload received for: $relPath")
            val pythonDir = File(filesDir, "python")
            val targetFile = File(pythonDir, relPath)

            // Security check: ensure we don't write outside filesDir
            if (!targetFile.canonicalPath.startsWith(pythonDir.canonicalPath)) {
                Log.w(TAG, "Security: Attempt to write outside python dir: $relPath")
                return
            }

            targetFile.parentFile?.mkdirs()
            FileOutputStream(targetFile).use { it.write(content.toByteArray()) }

            // Trigger Python Reload
            val python = Python.getInstance()
            val mainModule = python.getModule("main")
            if (mainModule.containsKey("reload_modules")) {
                val result = mainModule.callAttr("reload_modules").toString()
                Log.d(TAG, "Reload result: $result")
            }

        } catch (e: Exception) {
            Log.e(TAG, "Failed to apply hot reload", e)
        }
    }

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
