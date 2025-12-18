package com.hereliesaz.ideaz.features.preview

import android.app.Activity
import android.app.ActivityOptions
import android.content.Context
import android.content.Intent
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.os.Bundle
import android.util.DisplayMetrics
import android.view.SurfaceHolder
import android.view.SurfaceView
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.Toast
import com.hereliesaz.ideaz.R

class ContainerActivity : Activity(), SurfaceHolder.Callback {

    companion object {
        const val EXTRA_PACKAGE_NAME = "extra_package_name"
        private const val VIRTUAL_DISPLAY_NAME = "IDEaz_Preview_Display"
    }

    private lateinit var surfaceView: SurfaceView
    private var virtualDisplay: VirtualDisplay? = null
    private var targetPackageName: String? = null
    private var displayManager: DisplayManager? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        targetPackageName = intent.getStringExtra(EXTRA_PACKAGE_NAME)
        displayManager = getSystemService(Context.DISPLAY_SERVICE) as DisplayManager

        // Create a full-screen SurfaceView to render the other app
        val frameLayout = FrameLayout(this)
        frameLayout.layoutParams = ViewGroup.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT
        )

        surfaceView = SurfaceView(this)
        surfaceView.holder.addCallback(this)
        frameLayout.addView(surfaceView)
        setContentView(frameLayout)
    }

    override fun surfaceCreated(holder: SurfaceHolder) {
        createVirtualDisplay(holder)
        launchTargetApp()
    }

    override fun surfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) {
        // In a more complex implementation, we would resize the VirtualDisplay here.
    }

    override fun surfaceDestroyed(holder: SurfaceHolder) {
        releaseVirtualDisplay()
    }

    private fun createVirtualDisplay(holder: SurfaceHolder) {
        releaseVirtualDisplay()
        val metrics = DisplayMetrics()
        windowManager.defaultDisplay.getMetrics(metrics)

        // VIRTUAL_DISPLAY_FLAG_OWN_CONTENT_ONLY allows us to create a private display
        // without requiring the SYSTEM_ALERT_WINDOW permission for the display itself,
        // though we need to be the owner or have permissions to launch onto it.
        val flags = DisplayManager.VIRTUAL_DISPLAY_FLAG_OWN_CONTENT_ONLY or
                DisplayManager.VIRTUAL_DISPLAY_FLAG_PRESENTATION

        virtualDisplay = displayManager?.createVirtualDisplay(
            VIRTUAL_DISPLAY_NAME,
            surfaceView.width,
            surfaceView.height,
            metrics.densityDpi,
            holder.surface,
            flags
        )
    }

    private fun launchTargetApp() {
        val display = virtualDisplay?.display ?: return
        val packageName = targetPackageName ?: return

        try {
            val launchIntent = packageManager.getLaunchIntentForPackage(packageName)
            if (launchIntent == null) {
                Toast.makeText(this, "App installed, but no launcher activity found.", Toast.LENGTH_LONG).show()
                return
            }

            // The Magic: Force the app onto our private display
            val options = ActivityOptions.makeBasic()
            options.launchDisplayId = display.displayId

            launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_MULTIPLE_TASK)
            startActivity(launchIntent, options.toBundle())

        } catch (e: Exception) {
            e.printStackTrace()
            Toast.makeText(this, "Failed to launch in container: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    private fun releaseVirtualDisplay() {
        virtualDisplay?.release()
        virtualDisplay = null
    }

    override fun onDestroy() {
        super.onDestroy()
        releaseVirtualDisplay()
    }
}