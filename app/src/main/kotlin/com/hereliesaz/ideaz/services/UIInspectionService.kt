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
import android.content.pm.ShortcutInfo
import android.content.pm.ShortcutManager
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.PorterDuff
import android.graphics.Rect
import android.graphics.drawable.Icon
import android.os.Build
import android.util.Log
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import androidx.core.content.ContextCompat
import androidx.core.content.pm.ShortcutInfoCompat
import androidx.core.content.pm.ShortcutManagerCompat
import androidx.core.graphics.drawable.IconCompat
import com.hereliesaz.ideaz.R
import com.hereliesaz.ideaz.ui.BubbleActivity
import kotlin.math.abs
import kotlin.math.min
import kotlin.math.max

class UIInspectionService : AccessibilityService() {

    private val TAG = "UIInspectionService"
    private val CHANNEL_ID = "ideaz_bubble_channel"
    private val NOTIFICATION_ID = 1001

    private var windowManager: WindowManager? = null
    private var selectionOverlayView: SelectionView? = null

    // State
    private var isSelectMode = false
    private var pendingSelectionRect: Rect? = null
    private var currentHighlightRect: Rect? = null

    override fun onCreate() {
        super.onCreate()
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager

        setupSelectionOverlay()
        registerBroadcastReceivers()
        createBubbleChannel()
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        Log.d(TAG, "Service Connected - Launching Bubble")
        showBubbleNotification()
    }

    override fun onDestroy() {
        if (selectionOverlayView != null) windowManager?.removeView(selectionOverlayView)
        try { unregisterReceiver(commandReceiver) } catch (e: Exception) {}
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

    private fun showBubbleNotification() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return

        // Create target intent
        val target = Intent(this, BubbleActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(this, 0, target, PendingIntent.FLAG_MUTABLE)

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
            .setStyle(Notification.MessagingStyle(person).setConversationTitle("IDEaz"))

        getSystemService(NotificationManager::class.java).notify(NOTIFICATION_ID, builder.build())
    }

    // --- 2. Selection Overlay Logic (Red Box) ---

    private fun setupSelectionOverlay() {
        selectionOverlayView = SelectionView(this)
        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        )
        selectionOverlayView?.visibility = View.GONE
        windowManager?.addView(selectionOverlayView, params)
    }

    private fun toggleSelectionMode(enabled: Boolean) {
        isSelectMode = enabled
        selectionOverlayView?.visibility = if (enabled) View.VISIBLE else View.GONE
    }

    private val commandReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            when (intent?.action) {
                "com.hereliesaz.ideaz.START_INSPECTION" -> toggleSelectionMode(true)
                "com.hereliesaz.ideaz.STOP_INSPECTION" -> toggleSelectionMode(false)
                "com.hereliesaz.ideaz.RESTORE_OVERLAYS" -> toggleSelectionMode(false)
            }
        }
    }

    private fun registerBroadcastReceivers() {
        val filter = IntentFilter().apply {
            addAction("com.hereliesaz.ideaz.START_INSPECTION")
            addAction("com.hereliesaz.ideaz.STOP_INSPECTION")
            addAction("com.hereliesaz.ideaz.RESTORE_OVERLAYS")
        }
        ContextCompat.registerReceiver(this, commandReceiver, filter, ContextCompat.RECEIVER_NOT_EXPORTED)
    }

    // --- Node / Rect Detection ---
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

    // --- Custom View ---
    private inner class SelectionView(context: Context) : View(context) {
        private val paint = Paint().apply {
            color = 0x5500FF00 // Translucent Green
            style = Paint.Style.FILL
        }
        private val border = Paint().apply {
            color = 0xFF00FF00.toInt()
            style = Paint.Style.STROKE
            strokeWidth = 6f
        }

        private var startX = 0f
        private var startY = 0f
        private var endX = 0f
        private var endY = 0f
        private var isDragging = false
        private val touchSlop = ViewConfiguration.get(context).scaledTouchSlop

        override fun onDraw(canvas: Canvas) {
            canvas.drawColor(0, PorterDuff.Mode.CLEAR)
            if (isDragging) {
                val rect = getRect()
                canvas.drawRect(rect, paint)
                canvas.drawRect(rect, border)
            } else {
                currentHighlightRect?.let { rect ->
                    canvas.drawRect(rect, paint)
                    canvas.drawRect(rect, border)
                }
            }
        }

        private fun getRect(): Rect {
            return Rect(
                min(startX, endX).toInt(),
                min(startY, endY).toInt(),
                max(startX, endX).toInt(),
                max(startY, endY).toInt()
            )
        }

        override fun onTouchEvent(event: MotionEvent): Boolean {
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    startX = event.rawX
                    startY = event.rawY
                    endX = startX
                    endY = startY
                    isDragging = false
                    updateHighlightAt(startX.toInt(), startY.toInt())
                    return true
                }
                MotionEvent.ACTION_MOVE -> {
                    if (!isDragging && (abs(event.rawX - startX) > touchSlop || abs(event.rawY - startY) > touchSlop)) {
                        isDragging = true
                        currentHighlightRect = null // Clear tap highlight when dragging starts
                    }
                    if (isDragging) {
                        endX = event.rawX
                        endY = event.rawY
                        invalidate()
                    } else {
                        updateHighlightAt(event.rawX.toInt(), event.rawY.toInt())
                    }
                    return true
                }
                MotionEvent.ACTION_UP -> {
                    if (isDragging) {
                        // Drag Finished
                        val rect = getRect()
                        isDragging = false
                        invalidate()
                        if (rect.width() > 20 && rect.height() > 20) {
                            sendBroadcast(Intent("com.hereliesaz.ideaz.PROMPT_SUBMITTED_RECT")
                                .putExtra("RECT", rect)
                                .setPackage(packageName))
                            // Keep overlay visible for screenshot
                            showBubbleNotification()
                        }
                    } else {
                        // Tap Finished
                        val root = rootInActiveWindow
                        val node = findNodeAt(root, startX.toInt(), startY.toInt())
                        if (node != null) {
                            val rect = Rect()
                            node.getBoundsInScreen(rect)
                            currentHighlightRect = rect // Ensure it's drawn
                            invalidate()

                            var id = node.viewIdResourceName?.substringAfterLast(":id/")
                                ?: node.text?.toString()?.take(20)
                                ?: "Unknown"

                            sendBroadcast(Intent("com.hereliesaz.ideaz.PROMPT_SUBMITTED_NODE")
                                .putExtra("RESOURCE_ID", id)
                                .putExtra("BOUNDS", rect)
                                .setPackage(packageName))
                            // Keep overlay visible for screenshot
                            showBubbleNotification()
                        }
                    }
                    return true
                }
            }
            return super.onTouchEvent(event)
        }

        private fun updateHighlightAt(x: Int, y: Int) {
            val root = rootInActiveWindow
            val node = findNodeAt(root, x, y)
            if (node != null) {
                val rect = Rect()
                node.getBoundsInScreen(rect)
                if (currentHighlightRect != rect) {
                    currentHighlightRect = rect
                    invalidate()
                }
            } else {
                if (currentHighlightRect != null) {
                    currentHighlightRect = null
                    invalidate()
                }
            }
        }
    }
}