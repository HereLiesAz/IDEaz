package com.hereliesaz.ideaz.ai.bridge

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.core.content.FileProvider
import com.hereliesaz.ideaz.ai.AiEditApplier
import com.hereliesaz.ideaz.ai.ChatMessage
import com.hereliesaz.ideaz.ai.ChatPart
import com.hereliesaz.ideaz.ai.ConversationalAiClient
import com.hereliesaz.ideaz.ai.IdeTools
import com.hereliesaz.ideaz.utils.RepoSnapshot
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.selects.select
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.io.File

/**
 * Routes prompts through the user's installed Gemini app, giving a backend that
 * can't call IDEaz's tools the same two-way access the API providers get:
 *
 *  - READ: the whole project is delivered as `project.txt` (attached via
 *    [FileProvider], or inlined into the prompt if attachment isn't viable),
 *    with secrets withheld/redacted by [RepoSnapshot].
 *  - WRITE: the model is told to reply with CODE ONLY — complete `file:` blocks —
 *    and [AiEditApplier] turns that reply back into real file writes.
 *
 * The response is scraped off the Gemini app's UI by
 * [GeminiAppBridgeAccessibilityService]. The user must have granted the
 * accessibility service; if granted but the Gemini app isn't installed, [chat]
 * throws and the factory's [FallbackAdapter] falls through to the Gemini API.
 *
 * Conversation history is not replayed (the Gemini app keeps its own session);
 * each call forwards the last user turn plus a fresh project snapshot so the
 * model always edits against current code.
 */
class GeminiAppBridgeAdapter(
    private val context: Context,
    private val tools: IdeTools? = null,
    private val projectDir: File? = null,
    private val appName: String = "this project",
    private val projectType: String = "",
) : ConversationalAiClient {

    override suspend fun chat(messages: List<ChatMessage>): String = withContext(Dispatchers.Main) {
        if (!isAccessibilityServiceEnabled(context)) {
            error("Gemini app bridge: accessibility service not enabled.")
        }
        val geminiPackage = resolveGeminiPackage(context)
            ?: error("Gemini app bridge: Gemini app not installed.")

        val lastUser = messages.lastOrNull { it.role == "user" }
            ?: error("Gemini app bridge: no user message to forward.")
        val userText = lastUser.content.ifBlank { "(image-only prompt)" }

        // Flatten the project (secrets withheld + redacted) so the tool-less app
        // can see the code it's editing.
        val snapshot = projectDir?.takeIf { it.isDirectory }?.let {
            withContext(Dispatchers.IO) { RepoSnapshot.build(it) }
        }

        val promptText = buildString {
            append(PROTOCOL)
            append("\n\nProject: ").append(appName)
            if (projectType.isNotBlank()) append(" (").append(projectType).append(')')
            append("\n\nUser request:\n").append(userText)
        }

        // Stage attachments to the cache and share both via ACTION_SEND_MULTIPLE:
        // the project as project.txt, and the user's screenshot (with their
        // highlighted selection). Either may be absent.
        val stagedFiles = mutableListOf<File>()
        fun stage(name: String, bytes: ByteArray): Uri? = runCatching {
            val dir = File(context.cacheDir, "gemini-bridge").apply { mkdirs() }
            val f = File(dir, name)
            f.writeBytes(bytes)
            stagedFiles.add(f)
            FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", f)
        }.getOrNull()

        val projectUri = snapshot?.let { stage("project.txt", it.text.toByteArray()) }
        val firstImage = lastUser.parts.filterIsInstance<ChatPart.Image>().firstOrNull()
        val imageUri = firstImage?.let { stage("selection.${mimeToExt(it.mimeType)}", it.bytes) }

        // If the project couldn't be attached, inline a capped copy so the model
        // still sees the repo.
        val outboundText = if (projectUri == null && snapshot != null) {
            promptText + "\n\n===== PROJECT (project.txt) =====\n" + snapshot.text.take(INLINE_CAP)
        } else {
            promptText
        }

        GeminiAppBridge.reset()
        GeminiAppBridge.pendingPrompt = outboundText
        // The accessibility service types the prompt + taps Send (Gemini drops
        // EXTRA_TEXT when a file is attached), then flips to AWAIT_RESPONSE itself.
        GeminiAppBridge.phase = GeminiAppBridge.BridgePhase.INPUT
        GeminiAppBridge.promptSubmitted = false
        GeminiAppBridge.isWaiting = true

        val streams = ArrayList(listOfNotNull(projectUri, imageUri))
        val sendIntent = when {
            streams.size > 1 -> Intent(Intent.ACTION_SEND_MULTIPLE).apply {
                type = "*/*"
                putParcelableArrayListExtra(Intent.EXTRA_STREAM, streams)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            streams.size == 1 -> Intent(Intent.ACTION_SEND).apply {
                type = if (streams[0] == imageUri && firstImage != null) firstImage.mimeType else "text/plain"
                putExtra(Intent.EXTRA_STREAM, streams[0])
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            else -> Intent(Intent.ACTION_SEND).apply { type = "text/plain" }
        }.apply {
            putExtra(Intent.EXTRA_TEXT, outboundText)
            setPackage(geminiPackage)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }

        setUserBlock("wait")
        context.startActivity(sendIntent)

        val response = try {
            awaitResponse()
        } finally {
            setUserBlock("off")
            stagedFiles.forEach { it.delete() }
        }

        // Turn the code-only reply into real file writes — but checkpoint first
        // so a bad apply is one `git reset` away.
        val applier = tools
        if (applier != null) {
            val outcomes = withContext(Dispatchers.IO) {
                if (AiEditApplier.parse(response).isNotEmpty()) {
                    applier.checkpoint("IDEaz: checkpoint before AI edit")
                }
                AiEditApplier.apply(response, applier)
            }
            if (outcomes.isNotEmpty()) {
                val applied = outcomes.count { it.ok }
                val summary = outcomes.joinToString("\n") {
                    (if (it.ok) "✓ " else "✗ ") + it.label + (if (it.ok) "" else ": ${it.message}")
                }
                return@withContext "Applied $applied/${outcomes.size} file(s):\n$summary\n\n---\n$response"
            }
        }
        response
    }

    /**
     * Wait for the scraped response, but instead of hard-cancelling at a timeout,
     * show the scrim's Wait/Cancel prompt once a window elapses and let the user
     * decide. Returns the response text, or a "cancelled" notice if they cancel.
     */
    private suspend fun awaitResponse(): String {
        while (true) {
            val quick = withTimeoutOrNull(WINDOW_MS) { GeminiAppBridge.channel.receive() }
            if (quick != null) return quick

            setUserBlock("ask")
            val decision = select<Decision> {
                GeminiAppBridge.channel.onReceive { Decision.Got(it) }
                GeminiAppBridge.decisionChannel.onReceive { if (it) Decision.Wait else Decision.Cancel }
            }
            when (decision) {
                is Decision.Got -> return decision.text
                Decision.Wait -> setUserBlock("wait")
                Decision.Cancel -> {
                    GeminiAppBridge.isWaiting = false
                    return "Cancelled."
                }
            }
        }
    }

    private sealed interface Decision {
        data class Got(val text: String) : Decision
        object Wait : Decision
        object Cancel : Decision
    }

    /** Set the touch-block scrim state: "wait", "ask", or "off". Best-effort. */
    private fun setUserBlock(state: String) {
        runCatching {
            if (state == "wait") {
                if (!Settings.canDrawOverlays(context)) return
                context.startForegroundService(
                    Intent(context, com.hereliesaz.ideaz.services.IdeazOverlayService::class.java)
                        .putExtra("STATE", "wait")
                )
            } else {
                context.sendBroadcast(
                    Intent("com.hereliesaz.ideaz.BRIDGE_BLOCK").apply {
                        setPackage(context.packageName)
                        putExtra("STATE", state)
                    }
                )
            }
        }
    }

    private fun mimeToExt(mime: String): String = when (mime.lowercase()) {
        "image/png" -> "png"
        "image/jpeg", "image/jpg" -> "jpg"
        "image/gif" -> "gif"
        "image/webp" -> "webp"
        "image/heic" -> "heic"
        else -> "bin"
    }

    companion object {
        /** How long to wait before showing the Wait/Cancel prompt. */
        private const val WINDOW_MS = 60_000L

        /** Max chars of project text inlined when attachment isn't possible. */
        private const val INLINE_CAP = 120_000

        /** Strict code-only output contract so replies are deterministically applyable. */
        private val PROTOCOL = """
            You are a coding agent editing a project through a one-way text channel.

            OUTPUT RULES — follow exactly:
            - Reply with CODE ONLY. No explanations, no prose, no commentary, no
              greetings — nothing but the file blocks below.
            - For EVERY file you create or modify, output its COMPLETE final
              contents. Never a diff, never a fragment, never "// ... unchanged".
            - Use this exact block format, one block per file:

            ```file:relative/path/from/project/root.ext
            <entire file contents here>
            ```

            - Output nothing before the first block or after the last block.
            - The full project is provided to you as project.txt (attached to this
              message, or inlined below). Base every change on it.
        """.trimIndent()

        private val SERVICE_NAME =
            "com.hereliesaz.ideaz/com.hereliesaz.ideaz.ai.bridge.GeminiAppBridgeAccessibilityService"

        private val GEMINI_PACKAGES = listOf(
            "com.google.android.apps.bard",
            "com.google.android.googlequicksearchbox",
        )

        fun isAccessibilityServiceEnabled(context: Context): Boolean {
            val setting = Settings.Secure.getString(
                context.contentResolver,
                Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES,
            ) ?: return false
            return setting.split(':').any { it.equals(SERVICE_NAME, ignoreCase = true) }
        }

        fun resolveGeminiPackage(context: Context): String? {
            val pm = context.packageManager
            for (pkg in GEMINI_PACKAGES) {
                runCatching {
                    pm.getPackageInfo(pkg, 0)
                    return pkg
                }
            }
            return null
        }

        fun openAccessibilitySettingsIntent(): Intent =
            Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
}

/**
 * Thin decorator: runs [primary], falls back to [fallback] on any
 * non-cancellation exception. Used by the factory to make the bridge silently
 * fall through to the Gemini API when the bridge can't operate.
 */
class FallbackAdapter(
    private val primary: ConversationalAiClient,
    private val fallback: ConversationalAiClient,
    private val onFallback: (Throwable) -> Unit = {},
) : ConversationalAiClient {
    override suspend fun chat(messages: List<ChatMessage>): String = try {
        primary.chat(messages)
    } catch (e: kotlinx.coroutines.CancellationException) {
        throw e
    } catch (e: Throwable) {
        onFallback(e)
        fallback.chat(messages)
    }
}
