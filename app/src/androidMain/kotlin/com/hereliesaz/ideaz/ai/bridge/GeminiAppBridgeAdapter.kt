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
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import java.io.File

/**
 * Uses the installed Gemini consumer app as an IDEaz AI backend.
 *
 * The app gets a redacted `project.txt` snapshot, the full IDEaz conversation,
 * and every binary reference attached to the current user turn. Accessibility
 * automation is serialized process-wide because the consumer app exposes one
 * foreground composer, not independently addressable sessions.
 */
class GeminiAppBridgeAdapter(
    private val context: Context,
    private val tools: IdeTools,
) : ConversationalAiClient {

    override suspend fun chat(messages: List<ChatMessage>): String = BRIDGE_MUTEX.withLock {
        chatLocked(messages)
    }

    private suspend fun chatLocked(messages: List<ChatMessage>): String {
        check(BuildConfig.EXTERNAL_AI_AUTOMATION) {
            "Installed-app automation is not available in this IDEaz distribution."
        }
        check(isAccessibilityServiceEnabled(context)) {
            "IDEaz External AI accessibility service is not enabled."
        }

        val lastUser = messages.lastOrNull { it.role == "user" }
            ?: error("No user message to send to Gemini.")
        val projectDir = File(tools.projectPath())
        val snapshot = withContext(Dispatchers.IO) { RepoSnapshot.build(projectDir) }
        val transcript = serializeConversation(messages)

        val protocol = """
            You are acting as IDEaz's coding agent for the attached project.txt.
            Study the project and continue the conversation below. Earlier turns
            may have come from another provider; treat them as authoritative
            conversation history rather than relying on the consumer app's own chat.

            If NO file change is required, answer normally.
            If file changes ARE required, end your answer with ONE complete unified
            diff in a ```diff fenced block. Use paths relative to the project root,
            with standard --- a/path and +++ b/path headers. Do not omit unchanged
            context needed for the patch to apply.

            IDEAZ CONVERSATION:
            $transcript
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
        val attachmentUris = withContext(Dispatchers.IO) {
            lastUser.parts.mapIndexedNotNull { index, part ->
                when (part) {
                    is ChatPart.Image -> stage("reference-$index.${extensionFor(part.mimeType)}", part.bytes)
                    is ChatPart.FileBlob -> stage(
                        part.fileName?.takeIf { safeAttachmentName(it) }
                            ?: "reference-$index.${extensionFor(part.mimeType)}",
                        part.bytes,
                    )
                    is ChatPart.Text -> null
                }
            }
        }

        val streams = arrayListOf(projectUri).apply { addAll(attachmentUris) }
        val share = Intent(if (streams.size > 1) Intent.ACTION_SEND_MULTIPLE else Intent.ACTION_SEND).apply {
            type = "*/*"
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            if (streams.size > 1) putParcelableArrayListExtra(Intent.EXTRA_STREAM, streams)
            else putExtra(Intent.EXTRA_STREAM, streams.first())
            putExtra(Intent.EXTRA_TEXT, protocol)
        }
        val packageName = resolveGeminiPackage(context, share)
            ?: error("No installed Gemini-capable activity can accept this request.")
        share.setPackage(packageName)

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
            withContext(NonCancellable + Dispatchers.Main) {
                ExternalAiWindowHost.stopShell(context)
                ExternalAiWindowHost.returnToIdeaz(context)
            }
            withContext(NonCancellable + Dispatchers.IO) { staged.forEach { it.delete() } }
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
        private val BRIDGE_MUTEX = Mutex()
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

        /** True only when the installed package exposes an activity for our handoff. */
        fun isBridgeAvailable(context: Context): Boolean {
            if (!BuildConfig.EXTERNAL_AI_AUTOMATION || !isAccessibilityServiceEnabled(context)) return false
            val probe = Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_TEXT, "IDEaz availability check")
            }
            return resolveGeminiPackage(context, probe) != null
        }

        fun resolveGeminiPackage(context: Context, shareIntent: Intent): String? {
            val pm = context.packageManager
            return GEMINI_PACKAGES.firstOrNull { pkg ->
                Intent(shareIntent).setPackage(pkg).resolveActivity(pm) != null
            }
        }

        private fun serializeConversation(messages: List<ChatMessage>): String =
            messages.joinToString("\n\n") { message ->
                val role = when (message.role) {
                    "model", "assistant" -> "ASSISTANT"
                    else -> "USER"
                }
                val body = message.parts.joinToString("\n") { part ->
                    when (part) {
                        is ChatPart.Text -> part.text
                        is ChatPart.Image -> "[attached image: ${part.mimeType}]"
                        is ChatPart.FileBlob -> "[attached file: ${part.fileName ?: part.mimeType}]"
                    }
                }.ifBlank { "(no text)" }
                "$role:\n$body"
            }

        private fun safeAttachmentName(name: String): Boolean =
            name.isNotBlank() && name == File(name).name && '/' !in name && '\\' !in name

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
