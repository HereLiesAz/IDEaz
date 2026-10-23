package com.hereliesaz.ideaz.services

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.res.Configuration
import android.graphics.PixelFormat
import android.os.Build
import android.os.IBinder
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.TextView
import androidx.core.app.NotificationCompat
import com.hereliesaz.ideaz.BuildConfig
import com.hereliesaz.ideaz.MainActivity

/**
 * Draws IDEaz chrome *around* another app, not over its live content.
 *
 * Four independent TYPE_APPLICATION_OVERLAY panels leave a rectangular hole in
 * the middle. The external AI app is the real, touchable app underneath that
 * hole; only the surrounding frame belongs to IDEaz. This is intentionally not
 * a screenshot or VirtualDisplay trick, so typing, scrolling, selection and the
 * target app's own accessibility tree remain native.
 */
class ExternalAiOverlayService : Service() {
    private lateinit var windowManager: WindowManager
    private val panels = mutableListOf<View>()

    /** Params of the currently-shown frame, so a rotation can rebuild it at the new bounds. */
    private var currentFrame: Pair<String, Boolean>? = null

    override fun onCreate() {
        super.onCreate()
        windowManager = getSystemService(WindowManager::class.java)
        createNotificationChannel()
        startForeground(NOTIFICATION_ID, createNotification())
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (!BuildConfig.EXTERNAL_AI_OVERLAY) {
            stopSelf()
            return START_NOT_STICKY
        }
        when (intent?.action) {
            ACTION_HIDE -> stopSelf()
            ACTION_SHOW, null -> showFrame(
                label = intent?.getStringExtra(EXTRA_LABEL).orEmpty().ifBlank { "External AI" },
                compact = intent?.getBooleanExtra(EXTRA_COMPACT, false) == true,
            )
        }
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        panels.toList().forEach(::removePanel)
        panels.clear()
        currentFrame = null
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    /**
     * A rotated device is never recreated the way an Activity is, so without
     * this the four overlay panels keep the bounds computed for the old
     * orientation - the touchable "hole" stops lining up with the app
     * underneath. Rebuild at the new bounds whenever the frame is showing.
     */
    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        currentFrame?.let { (label, compact) -> showFrame(label, compact) }
    }

    private fun showFrame(label: String, compact: Boolean) {
        currentFrame = label to compact
        panels.toList().forEach(::removePanel)
        panels.clear()

        val bounds = windowManager.maximumWindowMetrics.bounds
        val density = resources.displayMetrics.density
        val horizontalInset = if (compact) (bounds.width() * 0.17f).toInt() else (12 * density).toInt()
        val topInset = if (compact) (bounds.height() * 0.18f).toInt() else (52 * density).toInt()
        val bottomInset = if (compact) (bounds.height() * 0.18f).toInt() else (18 * density).toInt()

        val holeLeft = bounds.left + horizontalInset
        val holeTop = bounds.top + topInset
        val holeRight = bounds.right - horizontalInset
        val holeBottom = bounds.bottom - bottomInset

        addPanel(
            makeHeader(label),
            x = bounds.left,
            y = bounds.top,
            width = bounds.width(),
            height = (holeTop - bounds.top).coerceAtLeast(1),
        )
        addPanel(
            makePanel(),
            x = bounds.left,
            y = holeBottom,
            width = bounds.width(),
            height = (bounds.bottom - holeBottom).coerceAtLeast(1),
        )
        addPanel(
            makePanel(),
            x = bounds.left,
            y = holeTop,
            width = (holeLeft - bounds.left).coerceAtLeast(1),
            height = (holeBottom - holeTop).coerceAtLeast(1),
        )
        addPanel(
            makePanel(),
            x = holeRight,
            y = holeTop,
            width = (bounds.right - holeRight).coerceAtLeast(1),
            height = (holeBottom - holeTop).coerceAtLeast(1),
        )
    }

    private fun makePanel(): View = FrameLayout(this).apply {
        setBackgroundColor(0xF5121518.toInt())
    }

    private fun makeHeader(label: String): View = FrameLayout(this).apply {
        setBackgroundColor(0xF5121518.toInt())
        val density = resources.displayMetrics.density
        addView(
            TextView(context).apply {
                text = "IDEaz  /  $label"
                setTextColor(0xFFE6E6E6.toInt())
                textSize = 15f
                gravity = Gravity.CENTER_VERTICAL
                setPadding((16 * density).toInt(), 0, (16 * density).toInt(), 0)
            },
            FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT,
            )
        )
    }

    private fun addPanel(view: View, x: Int, y: Int, width: Int, height: Int) {
        val params = WindowManager.LayoutParams(
            width,
            height,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            this.x = x
            this.y = y
        }
        runCatching {
            windowManager.addView(view, params)
            panels += view
        }
    }

    private fun removePanel(view: View) {
        runCatching { windowManager.removeView(view) }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            getSystemService(NotificationManager::class.java).createNotificationChannel(
                NotificationChannel(
                    CHANNEL_ID,
                    "External AI window",
                    NotificationManager.IMPORTANCE_LOW,
                )
            )
        }
    }

    private fun createNotification(): Notification {
        val openIdeaz = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_menu_view)
            .setContentTitle("IDEaz external AI")
            .setContentText("An installed AI app is open inside the IDEaz frame")
            .setContentIntent(openIdeaz)
            .setOngoing(true)
            .build()
    }

    companion object {
        const val ACTION_SHOW = "com.hereliesaz.ideaz.externalai.SHOW"
        const val ACTION_HIDE = "com.hereliesaz.ideaz.externalai.HIDE"
        const val EXTRA_LABEL = "label"
        const val EXTRA_COMPACT = "compact"
        private const val CHANNEL_ID = "external_ai_window"
        private const val NOTIFICATION_ID = 1207
    }
}
