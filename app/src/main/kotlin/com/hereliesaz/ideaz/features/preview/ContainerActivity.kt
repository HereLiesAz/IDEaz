package com.hereliesaz.ideaz.features.preview

import android.app.Activity
import android.app.ActivityOptions
import android.content.Context
import android.content.Intent
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.os.Bundle
import android.util.Log
import android.view.SurfaceHolder
import android.view.SurfaceView
import android.view.ViewGroup
import android.widget.FrameLayout

class ContainerActivity : Activity(), SurfaceHolder.Callback {

    companion object {
        const val EXTRA_PACKAGE_NAME = "extra_package_name"
        private const val TAG = "ContainerActivity"
        private const val VIRTUAL_DISPLAY_NAME = "IDEazVirtualDisplay"
    }

    private lateinit var surfaceView: SurfaceView
    private var virtualDisplay: VirtualDisplay? = null
    private var targetPackageName: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        targetPackageName = intent.getStringExtra(EXTRA_PACKAGE_NAME)
        if (targetPackageName == null) {
            Log.e(TAG, "No package name provided")
            finish()
            return
        }

        surfaceView = SurfaceView(this)
        surfaceView.layoutParams = ViewGroup.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT
        )
        surfaceView.holder.addCallback(this)

        val layout = FrameLayout(this)
        layout.addView(surfaceView)
        setContentView(layout)
    }

    override fun surfaceCreated(holder: SurfaceHolder) {
        createVirtualDisplay(holder)
        launchTargetApp()
    }

    override fun surfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) {
        // Resize virtual display if necessary
        virtualDisplay?.resize(width, height, resources.displayMetrics.densityDpi)
    }

    override fun surfaceDestroyed(holder: SurfaceHolder) {
        releaseVirtualDisplay()
    }

    override fun onDestroy() {
        super.onDestroy()
        releaseVirtualDisplay()
    }

    private fun createVirtualDisplay(holder: SurfaceHolder) {
        val displayManager = getSystemService(Context.DISPLAY_SERVICE) as DisplayManager
        val width = surfaceView.width
        val height = surfaceView.height
        val density = resources.displayMetrics.densityDpi

        virtualDisplay = displayManager.createVirtualDisplay(
            VIRTUAL_DISPLAY_NAME,
            width,
            height,
            density,
            holder.surface,
            DisplayManager.VIRTUAL_DISPLAY_FLAG_PUBLIC or DisplayManager.VIRTUAL_DISPLAY_FLAG_PRESENTATION
        )
        Log.d(TAG, "Virtual Display created: $virtualDisplay")
    }

    private fun releaseVirtualDisplay() {
        virtualDisplay?.release()
        virtualDisplay = null
    }

    private fun launchTargetApp() {
        val display = virtualDisplay?.display ?: return

        try {
            val launchIntent = packageManager.getLaunchIntentForPackage(targetPackageName!!)
            if (launchIntent != null) {
                launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_MULTIPLE_TASK)

                val options = ActivityOptions.makeBasic()
                options.launchDisplayId = display.displayId

                startActivity(launchIntent, options.toBundle())
                Log.d(TAG, "Launched $targetPackageName on display ${display.displayId}")
            } else {
                Log.e(TAG, "Could not find launch intent for $targetPackageName")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to launch app", e)
        }
    }
}
