package com.hereliesaz.ideaz

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState

/**
 * Desktop entry point. `./gradlew :app:run`.
 *
 * This target exists for one reason: to make IDEaz runnable without a handset.
 *
 * The architecture audit's deepest finding was that nothing in this codebase had
 * ever been executed on a device — no instrumented tests, no emulator in CI, no
 * recorded session — and that the cost had already been paid once, when a
 * released build shipped with every single credential save silently failing
 * because a caller-generated IV was passed to an AndroidKeyStore key. That bug
 * was found by reading the code. Nobody had launched the app.
 *
 * A desktop window makes "launch it and click the loop" something a developer or
 * a CI job can do in seconds. Both targets are JVM, so the shared code — JGit,
 * OkHttp, the AI adapters, IdeTools' checkpoint machinery — is the same code
 * running in both places, not a parallel implementation.
 */
fun main() = application {
    Window(
        onCloseRequest = ::exitApplication,
        title = "IDEaz",
        state = rememberWindowState(width = 420.dp, height = 880.dp),
    ) {
        MaterialTheme {
            Surface {
                // The shared UI is being lifted into commonMain incrementally.
                // Everything still living in androidMain — the WebView host, the
                // preference and credential stores, the Sora editor — needs an
                // expect/actual seam before it can render here.
                Text("IDEaz desktop target is wired. Shared UI lands as commonMain grows.")
            }
        }
    }
}
