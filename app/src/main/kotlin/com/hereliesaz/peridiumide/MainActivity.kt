package com.hereliesaz.peridiumide

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.tooling.preview.Preview
import android.content.Intent
import android.net.Uri
import com.hereliesaz.peridiumide.ui.theme.PeridiumIDETheme
import androidx.core.content.FileProvider
import java.io.File

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            PeridiumIDETheme {
                MainScreen()
            }
        }
        if (intent.action == "INSTALL_APK") {
            intent.getStringExtra("apk_path")?.let {
                installApk(it)
            }
        }
    }

    fun installApk(apkPath: String) {
        val apkFile = File(apkPath)
        val apkUri: Uri = FileProvider.getUriForFile(
            this,
            "${applicationContext.packageName}.provider",
            apkFile
        )
        val installIntent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(apkUri, "application/vnd.android.package-archive")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        startActivity(installIntent)
    }
}

@Composable
fun MainScreen() {
    Box(modifier = Modifier.fillMaxSize()) {
        Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
            Greeting(
                name = "Android",
                modifier = Modifier.padding(innerPadding)
            )
        }
        // Overlay() // Commented out as Overlay is not defined
    }
}

@Composable
fun Greeting(name: String, modifier: Modifier = Modifier) {
    Text(
        text = "Hello $name!",
        modifier = modifier.onGloballyPositioned { coordinates ->
            // val details = ComposableDetails(
            //     id = "greeting",
            //     bounds = coordinates.boundsInWindow()
            // )
            // ComposableRegistry.register(details)
        }
    )
}

@Preview(showBackground = true)
@Composable
fun GreetingPreview() {
    PeridiumIDETheme {
        Greeting("Android")
    }
}
