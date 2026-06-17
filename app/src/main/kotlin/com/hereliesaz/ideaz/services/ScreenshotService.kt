package com.hereliesaz.ideaz.services

import android.app.Activity
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.Rect
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.ImageReader
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.util.Base64
import android.util.DisplayMetrics
import android.view.WindowManager
import com.hereliesaz.ideaz.R
import java.io.ByteArrayOutputStream

class ScreenshotService : Service() {

    private var mediaProjectionManager: MediaProjectionManager? = null
    private var mediaProjection: MediaProjection? = null
    private var virtualDisplay: VirtualDisplay? = null
    private var imageReader: ImageReader? = null
    private var windowManager: WindowManager? = null

    private val highlightPaint = Paint().apply {
        color = android.graphics.Color.argb(100, 255, 0, 0) // Semi-transparent red
        style = Paint.Style.FILL
    }

    private val handler = Handler(Looper.getMainLooper())

    override fun onCreate() {
        super.onCreate()
        mediaProjectionManager = getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager

        createNotificationChannel()
    }

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            "screenshot_service",
            "Screenshot Service",
            NotificationManager.IMPORTANCE_LOW
        )
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(channel)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val resultCode = intent?.getIntExtra(EXTRA_RESULT_CODE, Activity.RESULT_CANCELED)
            ?: Activity.RESULT_CANCELED

        val data: Intent? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            intent?.getParcelableExtra(EXTRA_DATA, Intent::class.java)
        } else {
            @Suppress("DEPRECATION")
            intent?.getParcelableExtra(EXTRA_DATA)
        }
        val rect: Rect? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            intent?.getParcelableExtra(EXTRA_RECT, Rect::class.java)
        } else {
            @Suppress("DEPRECATION")
            intent?.getParcelableExtra(EXTRA_RECT)
        }

        // A mediaProjection foreground service may only be started while the app
        // holds a fresh MediaProjection consent (the project_media app-op). Starting
        // it without one throws SecurityException on Android 14+, so bail BEFORE
        // calling startForeground if the consent token is missing/invalid.
        if (resultCode != Activity.RESULT_OK || data == null || rect == null) {
            stopSelf()
            return START_NOT_STICKY
        }

        val notification: Notification = Notification.Builder(this, "screenshot_service")
            .setContentTitle("IDEaz")
            .setContentText("Selection service is active.")
            .setSmallIcon(R.mipmap.ic_launcher) // Make sure you have this
            .build()

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                startForeground(SERVICE_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION)
            } else {
                startForeground(SERVICE_ID, notification)
            }
            startCapture(resultCode, data, rect)
        } catch (e: Exception) {
            // e.g. SecurityException if the consent isn't actually held, or the
            // projection token was already consumed. Fail cleanly, don't crash.
            reportError("Screen capture unavailable: ${e.message}")
            stopCapture()
        }

        return START_NOT_STICKY
    }

    /** Tell the UI a capture failed so it isn't left waiting silently. */
    private fun reportError(message: String) {
        val intent = Intent("com.hereliesaz.ideaz.SCREENSHOT_FAILED").apply {
            setPackage(packageName)
            putExtra("MESSAGE", message)
        }
        sendBroadcast(intent)
    }

    private fun startCapture(resultCode: Int, data: Intent, rect: Rect) {
        mediaProjection = mediaProjectionManager?.getMediaProjection(resultCode, data)

        val width: Int
        val height: Int
        val densityDpi: Int

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val metrics = windowManager?.currentWindowMetrics
            width = metrics?.bounds?.width() ?: 0
            height = metrics?.bounds?.height() ?: 0
            densityDpi = resources.configuration.densityDpi
        } else {
            val metrics = DisplayMetrics()
            @Suppress("DEPRECATION")
            windowManager?.defaultDisplay?.getMetrics(metrics)
            width = metrics.widthPixels
            height = metrics.heightPixels
            densityDpi = metrics.densityDpi
        }

        if (width == 0 || height == 0) {
            stopSelf()
            return
        }

        mediaProjection?.registerCallback(object : MediaProjection.Callback() {
            override fun onStop() {
                super.onStop()
                stopCapture()
            }
        }, handler)

        imageReader = ImageReader.newInstance(width, height, PixelFormat.RGBA_8888, 2)

        virtualDisplay = mediaProjection?.createVirtualDisplay(
            "Screenshot",
            width, height, densityDpi,
            DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
            imageReader?.surface, null, handler
        )

        // Wait for the image
        handler.postDelayed({
            val image = imageReader?.acquireLatestImage()
            if (image != null) {
                val planes = image.planes
                val buffer = planes[0].buffer
                val pixelStride = planes[0].pixelStride
                val rowStride = planes[0].rowStride
                val rowPadding = rowStride - pixelStride * width

                val bitmap = Bitmap.createBitmap(
                    width + rowPadding / pixelStride,
                    height,
                    Bitmap.Config.ARGB_8888
                )
                bitmap.copyPixelsFromBuffer(buffer)
                image.close()

                // Crop and draw (Draw skipped as UIInspectionService handles highlight)
                // val finalBitmap = processBitmap(bitmap, rect)
                val base64String = bitmapToBase64(bitmap)
                bitmap.recycle()

                // Send broadcast back to MainViewModel
                val resultIntent = Intent("com.hereliesaz.ideaz.SCREENSHOT_TAKEN").apply {
                    setPackage(packageName)
                    putExtra("BASE64_SCREENSHOT", base64String)
                }
                sendBroadcast(resultIntent)

            }
            stopCapture()
        }, 500) // Delay to ensure capture
    }

    private fun bitmapToBase64(bitmap: Bitmap): String {
        val byteArrayOutputStream = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.PNG, 80, byteArrayOutputStream)
        val byteArray = byteArrayOutputStream.toByteArray()
        return Base64.encodeToString(byteArray, Base64.DEFAULT)
    }

    private fun stopCapture() {
        virtualDisplay?.release()
        imageReader?.close()
        mediaProjection?.stop()
        stopSelf()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        private const val SERVICE_ID = 123
        const val EXTRA_RESULT_CODE = "RESULT_CODE"
        const val EXTRA_DATA = "DATA"
        const val EXTRA_RECT = "RECT"
    }
}