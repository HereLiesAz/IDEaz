package com.hereliesaz.ideaz.ai.bridge

import com.hereliesaz.ideaz.ai.AiEditApproval
import com.hereliesaz.ideaz.ai.AiEditApprovalRequiredException
import com.hereliesaz.ideaz.ai.ChatMessage
import com.hereliesaz.ideaz.ai.ChatPart
import com.hereliesaz.ideaz.ai.IdeTools
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext

/**
 * Edits for backends that answer in plain text instead of tool calls (the installed Gemini app,
 * The Aive): the conversation goes out as one transcript, a unified diff comes back, and IDEaz
 * applies it behind the same checkpoint and review gate tool-calling adapters use.
 */
internal object TextPatchEdits {

    /** Instructions asking a text-only backend to answer with at most one unified diff. */
    const val DIFF_PROTOCOL = """
If NO file change is required, answer normally.
If file changes ARE required, end your answer with ONE complete unified
diff in a ```diff fenced block. Use paths relative to the project root,
with standard --- a/path and +++ b/path headers. Do not omit unchanged
context needed for the patch to apply."""

    fun serializeConversation(messages: List<ChatMessage>): String =
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

    fun extractUnifiedDiff(response: String): String? {
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

    /**
     * Applies [patch] behind a checkpoint. Returns [response] (with a note) when nothing changed or
     * the patch failed and was restored; throws [AiEditApprovalRequiredException] when changes await
     * review. [source] labels the approval, [checkpointLabel] the checkpoint, and
     * [senderPossessive] the failure note ("Gemini's").
     */
    suspend fun applyForReview(
        tools: IdeTools,
        patch: String,
        response: String,
        source: String,
        checkpointLabel: String,
        senderPossessive: String,
    ): String = withContext(Dispatchers.IO + NonCancellable) {
        val checkpoint = tools.createEditCheckpoint("IDEaz: checkpoint before $checkpointLabel edit")
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
                return@withContext "$response\n\nIDEaz could not apply $senderPossessive patch: $result"
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
                AiEditApproval(review = review, response = response, source = source)
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
}
