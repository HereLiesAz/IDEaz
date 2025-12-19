package com.hereliesaz.ideaz.features.preview

import android.app.Activity
import android.app.ActivityOptions
import android.content.Context
import android.content.Intent
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.os.Bundle
import android.view.SurfaceHolder
import android.view.SurfaceView
import android.widget.FrameLayout
import android.util.DisplayMetrics

class ContainerActivity : Activity() {

    private lateinit var surfaceView: SurfaceView
    private var virtualDisplay: VirtualDisplay? = null
    private var targetPackage: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        targetPackage = intent.getStringExtra("TARGET_PACKAGE")

        surfaceView = SurfaceView(this)
        surfaceView.holder.addCallback(object : SurfaceHolder.Callback {
            override fun surfaceCreated(holder: SurfaceHolder) {
                createVirtualDisplay(holder)
            }

            override fun surfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) {
                virtualDisplay?.resize(width, height, resources.displayMetrics.densityDpi)
            }

            override fun surfaceDestroyed(holder: SurfaceHolder) {
                virtualDisplay?.release()
                virtualDisplay = null
            }
        })

        setContentView(surfaceView, FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT,
            FrameLayout.LayoutParams.MATCH_PARENT
        ))
    }

    private fun createVirtualDisplay(holder: SurfaceHolder) {
        if (virtualDisplay != null) return

        val dm = getSystemService(Context.DISPLAY_SERVICE) as DisplayManager
        val metrics = resources.displayMetrics

        val width = surfaceView.width
        val height = surfaceView.height

        if (width <= 0 || height <= 0) return

        // VIRTUAL_DISPLAY_FLAG_PUBLIC is often needed to launch other apps on it,
        // unless we have special permissions or same UID.
        // Try with PUBLIC | OWN_CONTENT_ONLY to keep it private but usable.
        // If that fails, might need just PUBLIC or modify flags.
        // For standard apps, this might be restricted on newer Android versions.
        // But assuming we are running as a platform app or have permissions.
        // Note: Generic apps cannot create public virtual displays easily.
        // But let's try.

        try {
            // Use private display flags (0) or OWN_CONTENT_ONLY to avoid permission issues.
            // Target app must handle resizing/display logic.
            virtualDisplay = dm.createVirtualDisplay(
                "IDEaz-Container",
                width,
                height,
                metrics.densityDpi,
                holder.surface,
                0
            )

            launchTargetApp()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun launchTargetApp() {
        val pkg = targetPackage ?: return
        val display = virtualDisplay?.display ?: return

        try {
            val intent = packageManager.getLaunchIntentForPackage(pkg)
            if (intent != null) {
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                // Remove FLAG_ACTIVITY_CLEAR_TOP if it causes issues, but usually fine.

                val options = ActivityOptions.makeBasic()
                options.setLaunchDisplayId(display.displayId)
                startActivity(intent, options.toBundle())
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        virtualDisplay?.release()
    }
}
