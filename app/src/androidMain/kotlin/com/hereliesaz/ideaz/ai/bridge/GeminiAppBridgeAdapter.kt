package com.hereliesaz.ideaz.ai.bridge

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.core.content.FileProvider
import com.hereliesaz.ideaz.BuildConfig
import com.hereliesaz.ideaz.ai.AiEditApproval
import com.hereliesaz.ideaz.ai.AiEditApprovalRequiredException
import com.hereliesaz.ideaz.ai.ChatMessage
import com.hereliesaz.ideaz.ai.ChatPart
import com.hereliesaz.ideaz.ai.ConversationalAiClient
import com.hereliesaz.ideaz.ai.IdeTools
import com.hereliesaz.ideaz.utils.RepoSnapshot
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import java.io.File

/**
 * Uses the installed Gemini consumer app as an IDEaz AI backend.
 *
 * The app gets a redacted `project.txt` snapshot and the user's current request.
 * The accessibility driver types/submits the prompt and captures the reply. When
 * the reply contains a unified diff, it enters the exact same checkpoint/review/
 * explicit-approval path as the API adapters; external-app automation never gets
 * a private back door around edit review.
 */
class GeminiAppBridgeAdapter(
    private val context: Context,
    private val tools: IdeTools,
) : ConversationalAiClient {

    override suspend fun chat(messages: List<ChatMessage>): String {
        check(BuildConfig.EXTERNAL_AI_AUTOMATION) {
            "Installed-app automation is not available in this IDEaz distribution."
        }
        check(isAccessibilityServiceEnabled(context)) {
            "IDEaz External AI accessibility service is not enabled."
        }

        val packageName = resolveGeminiPackage(context)
            ?: error("Gemini app is not installed.")
        val lastUser = messages.lastOrNull { it.role == "user" }
            ?: error("No user message to send to Gemini.")
        val projectDir = File(tools.projectPath())
        val snapshot = withContext(Dispatchers.IO) { RepoSnapshot.build(projectDir) }

        val protocol = """
            You are acting as IDEaz's coding agent for the attached project.txt.
            Study the project and answer the user's request.

            If NO file change is required, answer normally.
            If file changes ARE required, end your answer with ONE complete unified
            diff in a ```diff fenced block. Use paths relative to the project root,
            with standard --- a/path and +++ b/path headers. Do not omit unchanged
            context needed for the patch to apply.

            User request:
            ${lastUser.content.ifBlank { "(see attached image/file)" }}
        """.trimIndent()

        val staged = mutableListOf<File>()
        fun stage(name: String, bytes: ByteArray): Uri = with(File(context.cacheDir, "gemini-bridge")) {
            mkdirs()
            val file = resolve(name).also {
                it.writeBytes(bytes)
                staged += it
            }
            FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        }

        val projectUri = withContext(Dispatchers.IO) {
            stage("project.txt", snapshot.text.toByteArray(Charsets.UTF_8))
        }
        val userAttachment = lastUser.parts.firstNotNullOfOrNull { part ->
            when (part) {
                is ChatPart.Image -> part.mimeType to part.bytes
                is ChatPart.FileBlob -> part.mimeType to part.bytes
                is ChatPart.Text -> null
            }
        }
        val attachmentUri = userAttachment?.let { (mime, bytes) ->
            withContext(Dispatchers.IO) { stage("reference.${extensionFor(mime)}", bytes) }
        }

        val streams = arrayListOf(projectUri).apply { attachmentUri?.let(::add) }
        val share = Intent(if (streams.size > 1) Intent.ACTION_SEND_MULTIPLE else Intent.ACTION_SEND).apply {
            type = "*/*"
            setPackage(packageName)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            if (streams.size > 1) putParcelableArrayListExtra(Intent.EXTRA_STREAM, streams)
            else putExtra(Intent.EXTRA_STREAM, streams.first())
            putExtra(Intent.EXTRA_TEXT, protocol)
        }

        GeminiAppBridge.reset()
        GeminiAppBridge.pendingPrompt = protocol
        GeminiAppBridge.targetPackage = packageName
        GeminiAppBridge.phase = GeminiAppBridge.BridgePhase.INPUT
        GeminiAppBridge.isWaiting = true

        val response = try {
            withContext(Dispatchers.Main) {
                ExternalAiWindowHost.launch(context, share, label = "Gemini")
            }
            withTimeout(RESPONSE_TIMEOUT_MS) { GeminiAppBridge.channel.receive() }
        } finally {
            GeminiAppBridge.isWaiting = false
            GeminiAppBridge.phase = GeminiAppBridge.BridgePhase.IDLE
            withContext(Dispatchers.Main) { ExternalAiWindowHost.stopShell(context) }
            withContext(Dispatchers.IO) { staged.forEach { it.delete() } }
        }

        val patch = extractUnifiedDiff(response) ?: return response
        return applyForReview(patch, response)
    }

    private suspend fun applyForReview(patch: String, response: String): String =
        withContext(Dispatchers.IO + NonCancellable) {
            val checkpoint = tools.createEditCheckpoint("IDEaz: checkpoint before installed Gemini edit")
            var expectedFingerprint: String? = null
            try {
                tools.captureToolEdit(checkpoint, "apply_patch", mapOf("patch" to patch))
                expectedFingerprint = tools.reviewEdits(checkpoint).contentFingerprint.also {
                    tools.updateEditCheckpointFingerprint(checkpoint, it)
                }
                tools.markEditMutationStarted(checkpoint)

                val result = tools.applyPatch(patch)
                val review = tools.reviewEdits(checkpoint)
                expectedFingerprint = review.contentFingerprint
                tools.updateEditCheckpointFingerprint(checkpoint, review.contentFingerprint)
                tools.markEditAwaitingReview(checkpoint)

                if (result.startsWith("Error:")) {
                    tools.restoreEditCheckpoint(checkpoint, review.contentFingerprint)
                    return@withContext "$response\n\nIDEaz could not apply Gemini's patch: $result"
                }
                if (review.validationErrors.isNotEmpty()) {
                    tools.restoreEditCheckpoint(checkpoint, review.contentFingerprint)
                    return@withContext "$response\n\nIDEaz rejected and restored the patch: ${review.validationErrors.joinToString()}"
                }
                if (review.changedFiles.isEmpty()) {
                    tools.discardEditCheckpoint(checkpoint)
                    return@withContext response
                }

                throw AiEditApprovalRequiredException(
                    AiEditApproval(review = review, response = response, source = "Gemini app")
                )
            } catch (e: AiEditApprovalRequiredException) {
                throw e
            } catch (e: Exception) {
                expectedFingerprint?.let { fingerprint ->
                    runCatching { tools.restoreEditCheckpoint(checkpoint, fingerprint) }
                }
                "$response\n\nIDEaz could not safely apply the patch: ${e.message}"
            }
        }

    companion object {
        private const val RESPONSE_TIMEOUT_MS = 120_000L
        private val GEMINI_PACKAGES = listOf(
            "com.google.android.apps.bard",
            "com.google.android.googlequicksearchbox",
        )

        fun isAccessibilityServiceEnabled(context: Context): Boolean {
            val enabled = Settings.Secure.getString(
                context.contentResolver,
                Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES,
            ).orEmpty()
            val className = "com.hereliesaz.ideaz.services.IdeazAccessibilityService"
            return enabled.split(':').any { component ->
                component.endsWith("/$className", ignoreCase = true) ||
                    component.endsWith("/.services.IdeazAccessibilityService", ignoreCase = true)
            }
        }

        fun resolveGeminiPackage(context: Context): String? {
            val pm = context.packageManager
            return GEMINI_PACKAGES.firstOrNull { pkg ->
                runCatching { pm.getApplicationInfo(pkg, 0) }.isSuccess
            }
        }

        private fun extractUnifiedDiff(response: String): String? {
            val fenced = Regex("```(?:diff|patch)\\s*([\\s\\S]*?)```", RegexOption.IGNORE_CASE)
                .find(response)
                ?.groupValues
                ?.getOrNull(1)
                ?.trim()
            if (!fenced.isNullOrBlank() &&
                fenced.contains("--- ") && fenced.contains("+++ ") && fenced.contains("@@")
            ) return fenced

            val start = response.lineSequence().indexOfFirst { it.startsWith("--- ") }
            if (start < 0) return null
            val raw = response.lineSequence().drop(start).joinToString("\n").trim()
            return raw.takeIf { it.contains("+++ ") && it.contains("@@") }
        }

        private fun extensionFor(mime: String): String = when (mime.lowercase()) {
            "image/png" -> "png"
            "image/jpeg", "image/jpg" -> "jpg"
            "image/gif" -> "gif"
            "image/webp" -> "webp"
            "application/pdf" -> "pdf"
            else -> "bin"
        }
    }
}

class FallbackAdapter(
    private val primary: ConversationalAiClient,
    private val fallback: ConversationalAiClient,
) : ConversationalAiClient {
    override suspend fun chat(messages: List<ChatMessage>): String = try {
        primary.chat(messages)
    } catch (_: TimeoutCancellationException) {
        fallback.chat(messages)
    } catch (e: CancellationException) {
        throw e
    } catch (e: AiEditApprovalRequiredException) {
        throw e
    } catch (_: Exception) {
        fallback.chat(messages)
    }
}
