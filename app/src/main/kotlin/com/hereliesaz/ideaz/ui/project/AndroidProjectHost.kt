package com.hereliesaz.ideaz.ui.project

import android.app.ActivityOptions
import android.content.Context
import android.content.Intent
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.view.SurfaceHolder
import android.view.SurfaceView
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView

@Composable
fun AndroidProjectHost(
    packageName: String,
    modifier: Modifier = Modifier
) {
    var virtualDisplay by remember { mutableStateOf<VirtualDisplay?>(null) }
    var surfaceView by remember { mutableStateOf<SurfaceView?>(null) }

    DisposableEffect(Unit) {
        onDispose {
            virtualDisplay?.release()
            virtualDisplay = null
        }
    }

    AndroidView(
        modifier = modifier.fillMaxSize(),
        factory = { ctx ->
            SurfaceView(ctx).apply {
                surfaceView = this
                holder.addCallback(object : SurfaceHolder.Callback {
                    override fun surfaceCreated(holder: SurfaceHolder) {
                        // Will create in surfaceChanged to ensure dimensions
                    }

                    override fun surfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) {
                        if (virtualDisplay == null) {
                            virtualDisplay = createVirtualDisplay(ctx, holder, width, height)
                            launchTargetApp(ctx, packageName, virtualDisplay)
                        } else {
                            virtualDisplay?.resize(width, height, resources.displayMetrics.densityDpi)
                        }
                    }

                    override fun surfaceDestroyed(holder: SurfaceHolder) {
                        virtualDisplay?.release()
                        virtualDisplay = null
                    }
                })
            }
        }
    )
}

private fun createVirtualDisplay(context: Context, holder: SurfaceHolder, width: Int, height: Int): VirtualDisplay? {
    val dm = context.getSystemService(Context.DISPLAY_SERVICE) as DisplayManager
    val metrics = context.resources.displayMetrics

    if (width <= 0 || height <= 0) return null

    return try {
        // Try creating with standard flags. Private display (0) is safest but restrict cross-app launch.
        // Public (1) requires permission.
        // We attempt 0 for now. If target app shares UID or security allows, it works.
        // Otherwise it might launch on main display.
        dm.createVirtualDisplay(
            "IDEaz-Virtual",
            width,
            height,
            metrics.densityDpi,
            holder.surface,
            0
        )
    } catch (e: Exception) {
        e.printStackTrace()
        null
    }
}

private fun launchTargetApp(context: Context, packageName: String, virtualDisplay: VirtualDisplay?) {
    if (virtualDisplay == null) return
    try {
        val intent = context.packageManager.getLaunchIntentForPackage(packageName)
        if (intent != null) {
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            val options = ActivityOptions.makeBasic()
            options.setLaunchDisplayId(virtualDisplay.display.displayId)
            context.startActivity(intent, options.toBundle())
        }
    } catch (e: Exception) {
        e.printStackTrace()
    }
}
