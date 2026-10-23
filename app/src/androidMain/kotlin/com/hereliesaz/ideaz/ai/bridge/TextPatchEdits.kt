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

    /**
     * The unified diff in [response]: every ```diff / ```patch fenced block that
     * holds one, joined (a reply may split its edits across blocks; JGit applies
     * a multi-file patch in one go), or else everything from the first `--- `
     * line on.
     *
     * A fence only closes on a line that is exactly ``` - a diff of a Markdown
     * file carries ``` inside its content lines, which always start with
     * ' ', '+' or '-'. Only blank lines are trimmed from the ends: `trim()` used
     * to strip a final context line that is a lone space, leaving the last hunk
     * a line short so JGit rejected it.
     */
    fun extractUnifiedDiff(response: String): String? {
        val lines = response.replace("\r\n", "\n").lines()
        val blocks = mutableListOf<String>()
        var i = 0
        while (i < lines.size) {
            if (FENCE_OPEN.matches(lines[i])) {
                val end = (i + 1 until lines.size).firstOrNull { lines[it].trimEnd() == "```" } ?: lines.size
                val body = trimBlankLines(lines.subList(i + 1, end))
                if (looksLikeDiff(body)) blocks += body
                i = end + 1
            } else {
                i++
            }
        }
        if (blocks.isNotEmpty()) return blocks.joinToString("\n")

        val start = lines.indexOfFirst { it.startsWith("--- ") }
        if (start < 0) return null
        return trimBlankLines(lines.drop(start)).takeIf { looksLikeDiff(it) }
    }

    private val FENCE_OPEN = Regex("""^\s*```\s*(?:diff|patch)\s*$""", RegexOption.IGNORE_CASE)

    private fun looksLikeDiff(text: String) =
        text.contains("--- ") && text.contains("+++ ") && text.contains("@@")

    private fun trimBlankLines(lines: List<String>): String =
        lines.dropWhile { it.isEmpty() }.dropLastWhile { it.isEmpty() }.joinToString("\n")

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
            val fingerprint = expectedFingerprint
            var restoreFailure: Exception? = null
            if (fingerprint != null) {
                try {
                    tools.restoreEditCheckpoint(checkpoint, fingerprint)
                } catch (r: Exception) {
                    restoreFailure = r
                }
            } else {
                // Failed before any file could change (no fingerprint was ever recorded), so the
                // checkpoint is an orphan nothing would restore or discard later.
                runCatching { tools.discardEditCheckpoint(checkpoint) }
            }
            failureNote(response, e.message, restoreFailure?.message, restoreFailed = restoreFailure != null)
        }
    }

    /** The reply shown when applying failed; says so plainly when the restore failed too. */
    internal fun failureNote(
        response: String,
        applyError: String?,
        restoreError: String?,
        restoreFailed: Boolean,
    ): String {
        val base = "$response\n\nIDEaz could not safely apply the patch: $applyError"
        return if (restoreFailed) {
            "$base\n\nIDEaz also could not restore the files (${restoreError ?: "unknown error"}), " +
                "so some project files may have been left modified. Review the project files " +
                "(or use Git) before continuing."
        } else {
            base
        }
    }
}
