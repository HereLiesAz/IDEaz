package com.hereliesaz.ideaz.services

import android.accessibilityservice.AccessibilityService
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.PorterDuff
import android.graphics.Rect
import android.graphics.drawable.Icon
import android.os.Build
import android.util.Log
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import androidx.annotation.RequiresApi
import androidx.core.content.ContextCompat
import com.hereliesaz.ideaz.BubbleActivity
import com.hereliesaz.ideaz.R
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

class UIInspectionService : AccessibilityService() {

    private val TAG = "UIInspectionService"
    private val CHANNEL_ID = "ideaz_bubble_channel"
    private val NOTIFICATION_ID = 1001

    // Guard flag to prevent calls before onServiceConnected
    private var isConnected = false

    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "UIInspectionService.onCreate")
        registerBroadcastReceivers()
        createBubbleChannel()
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        Log.d(TAG, "Service Connected - Launching Bubble")
        isConnected = true
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            showBubbleNotification()
        }
    }

    override fun onDestroy() {
        isConnected = false
        try {
            unregisterReceiver(commandReceiver)
        } catch (e: Exception) {
        }
        super.onDestroy()
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {}
    override fun onInterrupt() {}

    // --- 1. Bubble Notification Logic ---

    private fun createBubbleChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(CHANNEL_ID, "IDEaz Overlay", NotificationManager.IMPORTANCE_HIGH)
            channel.description = "Floating IDE bubble"
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                channel.setAllowBubbles(true)
            }
            getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }
    }

    @RequiresApi(Build.VERSION_CODES.R)
    private fun showBubbleNotification() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return

        // Create target intent
        val target = Intent(this, BubbleActivity::class.java)
        // Bubbles require Mutable PendingIntent on Android 12+ (API 31)
        // We use FLAG_UPDATE_CURRENT to ensure we have the latest intent
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            target,
            PendingIntent.FLAG_MUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        // Create Bubble Metadata
        val bubbleData = Notification.BubbleMetadata.Builder(pendingIntent, Icon.createWithResource(this, R.mipmap.ic_launcher))
            .setDesiredHeight(600)
            .setAutoExpandBubble(true)
            .setSuppressNotification(true)
            .build()

        // Create Person/Shortcut (Required for bubbles)
        val person = android.app.Person.Builder().setName("IDEaz").build()

        val builder = Notification.Builder(this, CHANNEL_ID)
            .setContentTitle("IDEaz")
            .setSmallIcon(R.mipmap.ic_launcher)
            .setBubbleMetadata(bubbleData)
            .addPerson(person)

        getSystemService(NotificationManager::class.java).notify(NOTIFICATION_ID, builder.build())
    }

    // --- 2. Node Detection ---

    private val commandReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == "com.hereliesaz.ideaz.INSPECT_AT_COORDINATES") {
                val x = intent.getIntExtra("X", -1)
                val y = intent.getIntExtra("Y", -1)
                if (x != -1 && y != -1) {
                    inspectAt(x, y)
                }
            }
        }
    }

    private fun registerBroadcastReceivers() {
        val filter = IntentFilter("com.hereliesaz.ideaz.INSPECT_AT_COORDINATES")
        ContextCompat.registerReceiver(this, commandReceiver, filter, ContextCompat.RECEIVER_NOT_EXPORTED)
    }

    private fun inspectAt(x: Int, y: Int) {
        val root = rootInActiveWindow
        val node = findNodeAt(root, x, y)
        if (node != null) {
            val rect = Rect()
            node.getBoundsInScreen(rect)
            val id = node.viewIdResourceName?.substringAfterLast(":id/") ?: node.text?.toString()?.take(20) ?: "Unknown"

            val intent = Intent("com.hereliesaz.ideaz.PROMPT_SUBMITTED_NODE")
                .putExtra("RESOURCE_ID", id)
                .putExtra("BOUNDS", rect)
                .setPackage(packageName)

            sendBroadcast(intent)
        }
    }

    private fun findNodeAt(root: AccessibilityNodeInfo?, x: Int, y: Int): AccessibilityNodeInfo? {
        if (root == null) return null
        val rect = Rect()
        root.getBoundsInScreen(rect)
        if (!rect.contains(x, y)) return null

        for (i in 0 until root.childCount) {
            val child = findNodeAt(root.getChild(i), x, y)
            if (child != null) return child
        }
        return root
    }
}