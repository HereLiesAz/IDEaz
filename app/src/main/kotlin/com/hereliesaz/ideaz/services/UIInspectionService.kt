package com.hereliesaz.ideaz.services

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.view.accessibility.AccessibilityEvent
import android.graphics.PixelFormat
import android.graphics.Paint
import android.view.Gravity
import android.view.WindowManager
import android.widget.FrameLayout
import android.content.BroadcastReceiver
import android.content.Context
import android.content.IntentFilter
import android.view.accessibility.AccessibilityNodeInfo
import android.graphics.Rect
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.util.Log
import android.view.ContextThemeWrapper
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.widget.Button
import android.widget.EditText
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import com.hereliesaz.ideaz.R
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.PorterDuff
import androidx.core.os.postDelayed
import com.hereliesaz.ideaz.ui.SettingsViewModel
import kotlin.math.abs
import kotlin.math.min
import kotlin.math.max

class UIInspectionService : AccessibilityService() {

    private var touchInterceptor: SelectionView? = null
    private var overlayView: View? = null
    private var logContainer: View? = null
    private var promptContainer: View? = null

    private var windowManager: WindowManager? = null
    private lateinit var settingsViewModel: SettingsViewModel
    private var mainHandler: Handler? = null
    private var debugTextView: TextView? = null

    // Dedicated view for reliable drawing
    private inner class SelectionView(context: Context) : View(context) {
        private val selectionPaint = Paint().apply {
            color = Color.argb(100, 255, 0, 0)
            style = Paint.Style.FILL
        }
        var startX = 0f
        var startY = 0f
        var endX = 0f
        var endY = 0f
        var isDragging = false
        var currentSelectionRect = Rect()
        private val touchSlop = ViewConfiguration.get(context).scaledTouchSlop

        override fun onDraw(canvas: Canvas) {
            super.onDraw(canvas)
            canvas.drawColor(Color.TRANSPARENT, PorterDuff.Mode.CLEAR)
            if (isDragging) {
                currentSelectionRect.set(
                    min(startX, endX).toInt(),
                    min(startY, endY).toInt(),
                    max(startX, endX).toInt(),
                    max(startY, endY).toInt()
                )
                canvas.drawRect(currentSelectionRect, selectionPaint)
            }
        }

        override fun onTouchEvent(event: MotionEvent): Boolean {
            if (overlayView != null) return false

            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    startX = event.rawX
                    startY = event.rawY
                    endX = startX
                    endY = startY
                    isDragging = false
                    return true
                }
                MotionEvent.ACTION_MOVE -> {
                    if (!isDragging && (abs(event.rawX - startX) > touchSlop || abs(event.rawY - startY) > touchSlop)) {
                        isDragging = true
                    }
                    if (isDragging) {
                        endX = event.rawX
                        endY = event.rawY
                        invalidate()
                    }
                    return true
                }
                MotionEvent.ACTION_UP -> {
                    val wasDragging = isDragging
                    isDragging = false
                    invalidate()

                    if (wasDragging) {
                        val rect = Rect(
                            min(startX, endX).toInt(),
                            min(startY, endY).toInt(),
                            max(startX, endX).toInt(),
                            max(startY, endY).toInt()
                        )
                        if (rect.width() > 10 && rect.height() > 10) {
                            showPromptUI(rect, null)
                        }
                    } else {
                        val rootNode = rootInActiveWindow
                        if (rootNode != null) {
                            val node = findNodeAt(rootNode, startX.toInt(), startY.toInt())
                            val bounds = Rect()
                            var resourceId: String? = null
                            if (node != null) {
                                var targetNode: AccessibilityNodeInfo? = node
                                var depth = 0
                                val maxDepth = 20
                                var foundResourceId: String? = null

                                while (targetNode != null && depth < maxDepth) {
                                    val desc = targetNode.contentDescription?.toString()
                                    if (desc != null && desc.startsWith("__source:")) {
                                        resourceId = desc
                                        break
                                    }
                                    if (foundResourceId == null) {
                                        foundResourceId = targetNode.viewIdResourceName
                                    }

                                    val parent = targetNode.parent
                                    if (targetNode != node) {
                                        targetNode.recycle()
                                    }
                                    targetNode = parent
                                    depth++
                                }

                                if (resourceId == null) {
                                    resourceId = foundResourceId
                                }

                                node.getBoundsInScreen(bounds)
                                showPromptUI(bounds, resourceId)
                            }
                        }
                    }
                    return true
                }
            }
            return super.onTouchEvent(event)
        }
    }

    private val commandReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            when (intent?.action) {
                "com.hereliesaz.ideaz.AI_LOG" -> {
                    val message = intent.getStringExtra("MESSAGE") ?: "..."
                    mainHandler?.post { updateLogUI(message) }
                }
                "com.hereliesaz.ideaz.TASK_FINISHED" -> {
                    mainHandler?.post { hideOverlayUI() }
                }
                "com.hereliesaz.ideaz.SHOW_LOG_UI" -> {
                    val rect = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        intent.getParcelableExtra("RECT", Rect::class.java)
                    } else {
                        @Suppress("DEPRECATION")
                        intent.getParcelableExtra("RECT")
                    }
                    rect?.let { mainHandler?.post { showLogUI(it) } }
                }
            }
        }
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {}
    override fun onInterrupt() {}

    override fun onServiceConnected() {
        super.onServiceConnected()
        windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        mainHandler = Handler(Looper.getMainLooper())
        settingsViewModel = SettingsViewModel(application)

        val filter = IntentFilter().apply {
            addAction("com.hereliesaz.ideaz.AI_LOG")
            addAction("com.hereliesaz.ideaz.TASK_FINISHED")
            addAction("com.hereliesaz.ideaz.SHOW_LOG_UI")
        }
        registerReceiver(commandReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        mainHandler?.post { if (touchInterceptor == null) showTouchInterceptor() }
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        hideTouchInterceptor()
        hideOverlayUI()
        unregisterReceiver(commandReceiver)
        super.onDestroy()
    }

    private fun showTouchInterceptor() {
        if (touchInterceptor != null) return
        touchInterceptor = SelectionView(this)
        val lp = WindowManager.LayoutParams().apply {
            type = WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY
            format = PixelFormat.TRANSLUCENT
            flags = WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
            width = WindowManager.LayoutParams.MATCH_PARENT
            height = WindowManager.LayoutParams.MATCH_PARENT
        }
        windowManager?.addView(touchInterceptor, lp)
    }

    private fun hideTouchInterceptor() {
        touchInterceptor?.let { windowManager?.removeView(it) }
        touchInterceptor = null
    }

    private fun showLogUI(rect: Rect) {
        touchInterceptor?.visibility = View.GONE
        if (overlayView == null) {
            val themedContext = ContextThemeWrapper(this, R.style.Theme_IDEaz)
            overlayView = LayoutInflater.from(themedContext).inflate(R.layout.inspection_overlay, null)
        }

        val lp = WindowManager.LayoutParams().apply {
            type = WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY
            format = PixelFormat.TRANSLUCENT
            flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
            gravity = Gravity.TOP or Gravity.LEFT
            x = rect.left
            y = rect.top
            width = rect.width()
            height = rect.height()
        }
        logContainer = overlayView?.findViewById(R.id.log_container)
        promptContainer = overlayView?.findViewById(R.id.prompt_container)
        val cancelButton = overlayView?.findViewById<ImageButton>(R.id.cancel_button)

        logContainer?.visibility = View.VISIBLE
        promptContainer?.visibility = View.GONE

        cancelButton?.setOnClickListener { sendBroadcast(Intent("com.hereliesaz.ideaz.CANCEL_TASK_REQUESTED").setPackage(packageName)) }

        if (overlayView?.windowToken == null) windowManager?.addView(overlayView, lp)
        else windowManager?.updateViewLayout(overlayView, lp)
    }

    private fun showPromptUI(rect: Rect, resourceId: String?) {
        hideOverlayUI()
        touchInterceptor?.visibility = View.GONE

        val themedContext = ContextThemeWrapper(this, R.style.Theme_IDEaz)
        overlayView = LayoutInflater.from(themedContext).inflate(R.layout.inspection_overlay, null)
        val lp = WindowManager.LayoutParams().apply {
            type = WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY
            format = PixelFormat.TRANSLUCENT
            flags = WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
            width = WindowManager.LayoutParams.WRAP_CONTENT
            height = WindowManager.LayoutParams.WRAP_CONTENT
            gravity = Gravity.TOP or Gravity.LEFT
            x = rect.left
            y = rect.bottom + 8
        }

        logContainer = overlayView?.findViewById(R.id.log_container)
        promptContainer = overlayView?.findViewById(R.id.prompt_container)
        val promptInput = overlayView?.findViewById<EditText>(R.id.prompt_input)
        val submitButton = overlayView?.findViewById<Button>(R.id.submit_button)
        val cancelButton = overlayView?.findViewById<ImageButton>(R.id.cancel_button)

        logContainer?.visibility = View.GONE
        promptContainer?.visibility = View.VISIBLE
        promptInput?.hint = resourceId?.let { "Prompt for $it..." } ?: "Prompt for area..."

        submitButton?.setOnClickListener {
            val prompt = promptInput?.text.toString()
            if (prompt.isNotBlank()) {
                hidePromptUI()
                val intent = if (resourceId != null) {
                    Intent("com.hereliesaz.ideaz.PROMPT_SUBMITTED_NODE").apply {
                        putExtra("RESOURCE_ID", resourceId)
                        putExtra("PROMPT", prompt)
                        putExtra("BOUNDS", rect)
                    }
                } else {
                    Intent("com.hereliesaz.ideaz.PROMPT_SUBMITTED_RECT").apply {
                        putExtra("RECT", rect)
                        putExtra("PROMPT", prompt)
                    }
                }
                sendBroadcast(intent.setPackage(packageName))
            }
        }
        cancelButton?.setOnClickListener { sendBroadcast(Intent("com.hereliesaz.ideaz.CANCEL_TASK_REQUESTED").setPackage(packageName)) }
        windowManager?.addView(overlayView, lp)
    }

    private fun updateLogUI(message: String) {
        overlayView?.findViewById<TextView>(R.id.log_text)?.append("\n> $message")
        overlayView?.findViewById<ScrollView>(R.id.log_scroll_view)?.post {
            overlayView?.findViewById<ScrollView>(R.id.log_scroll_view)?.fullScroll(View.FOCUS_DOWN)
        }
    }

    private fun hidePromptUI() {
        promptContainer?.visibility = View.GONE
        if (logContainer?.visibility == View.GONE) hideOverlayUI()
    }

    private fun hideOverlayUI() {
        overlayView?.let { windowManager?.removeView(it) }
        overlayView = null
        logContainer = null
        promptContainer = null
        touchInterceptor?.visibility = View.VISIBLE
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