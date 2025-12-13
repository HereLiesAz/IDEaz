package com.hereliesaz.ideaz

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.core.content.ContextCompat
import com.hereliesaz.ideaz.services.IdeazOverlayService
import com.hereliesaz.ideaz.ui.theme.IDEazTheme

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (!Settings.canDrawOverlays(this)) {
            setContent {
                IDEazTheme {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text("Please grant overlay permission.")
                    }
                }
            }
            Toast.makeText(this, "Please grant overlay permission to use IDEaz", Toast.LENGTH_LONG).show()
            val intent = Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName"))
            startActivityForResult(intent, 1001)
        } else {
            launchOverlay()
            finish()
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == 1001) {
            if (Settings.canDrawOverlays(this)) {
                launchOverlay()
                finish()
            } else {
                Toast.makeText(this, "Permission denied. IDEaz cannot run.", Toast.LENGTH_SHORT).show()
                finish()
            }
        }
    }

    private fun launchOverlay() {
        val intent = Intent(this, IdeazOverlayService::class.java)
        ContextCompat.startForegroundService(this, intent)
    }
}
