package com.hereliesaz.ideaz.ui

import android.annotation.SuppressLint
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.hereliesaz.ideaz.ai.AndroidAiModeBrowser

/**
 * Shows Google's human check or consent page when Google AI Mode hits one, so the user can pass
 * it here instead of in another browser. Closing the dialog (Done or back) re-asks the question.
 */
@SuppressLint("SetJavaScriptEnabled")
@Composable
fun GoogleAiModeCheckDialog() {
    val browser = AndroidAiModeBrowser.get(LocalContext.current)
    // Tells the browser a screen can show a check; without one it reports an error instead of waiting.
    DisposableEffect(browser) {
        val watching = browser.watch()
        onDispose { watching.close() }
    }
    val check by browser.humanCheck.collectAsState()
    val current = check ?: return

    Dialog(
        onDismissRequest = { browser.finishHumanCheck(current) },
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Card(modifier = Modifier.fillMaxSize().padding(16.dp)) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("Google wants to check that you're a person, or asks about cookies. Finish it below, then tap Done.")
                Spacer(modifier = Modifier.height(8.dp))
                AndroidView(
                    modifier = Modifier.fillMaxWidth().weight(1f),
                    factory = { ctx ->
                        WebView(ctx).apply {
                            // Same profile as the hidden view, so the passed check is in its cookies.
                            AndroidAiModeBrowser.isolate(this)
                            settings.javaScriptEnabled = true
                            settings.domStorageEnabled = true
                            webViewClient = WebViewClient()
                            loadUrl(current.url)
                        }
                    },
                    onRelease = { it.destroy() },
                )
                Spacer(modifier = Modifier.height(8.dp))
                Button(onClick = { browser.finishHumanCheck(current) }, modifier = Modifier.fillMaxWidth()) {
                    Text("Done")
                }
            }
        }
    }
}
