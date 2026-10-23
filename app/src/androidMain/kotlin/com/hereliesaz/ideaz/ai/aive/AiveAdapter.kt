package com.hereliesaz.ideaz.ai.aive

import com.hereliesaz.ideaz.ai.ChatMessage
import com.hereliesaz.ideaz.ai.ConversationalAiClient
import com.hereliesaz.ideaz.ai.IdeTools
import com.hereliesaz.ideaz.ai.bridge.TextPatchEdits
import com.hereliesaz.ideaz.utils.RepoSnapshot
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * The Aive as an IDEaz chat model. The prompt runs on whichever provider is linked in The Aive.
 * Aive returns text, not tool calls, so the reply comes back as a unified diff, which goes through
 * the same review gate as the installed-Gemini bridge. The project travels as a redacted snapshot.
 * [client] is the local bound service or the relay to another device ([AiveTransports]).
 */
class AiveAdapter(
    private val client: AiveTransport,
    private val tools: IdeTools,
) : ConversationalAiClient {

    override suspend fun chat(messages: List<ChatMessage>): String {
        val snapshot = withContext(Dispatchers.IO) {
            RepoSnapshot.build(
                File(tools.projectPath()),
                maxBytes = if (client.isRemote) AiveRelayClient.MAX_SNAPSHOT_BYTES else SNAPSHOT_BYTES,
            )
        }
        val conversation = TextPatchEdits.serializeConversation(messages)
        val prompt = buildString {
            appendLine("You are acting as IDEaz's coding agent for the project below.")
            appendLine("Study the project and continue the conversation that follows it.")
            appendLine(TextPatchEdits.DIFF_PROTOCOL.trim())
            appendLine()
            appendLine("PROJECT:")
            appendLine(snapshot.text)
            appendLine()
            appendLine("IDEAZ CONVERSATION:")
            append(conversation)
        }
        if (client.isRemote) {
            overflowMessage(
                promptBytes = prompt.toByteArray(Charsets.UTF_8).size,
                conversationBytes = conversation.toByteArray(Charsets.UTF_8).size,
                snapshotBytes = snapshot.text.toByteArray(Charsets.UTF_8).size,
                limitBytes = AiveRelayClient.MAX_PROMPT_BYTES,
            )?.let { error(it) }
        }
        val response = client.prompt(prompt)
        val patch = TextPatchEdits.extractUnifiedDiff(response) ?: return response
        return TextPatchEdits.applyForReview(
            tools, patch, response,
            source = "The Aive",
            checkpointLabel = "The Aive",
            senderPossessive = "The Aive's",
        )
    }

    companion object {
        /**
         * Why a prompt of [promptBytes] cannot go to the relay, or null when it fits. The
         * conversation alone being over the limit is the only case a new chat fixes; otherwise
         * the project snapshot is what tips it over, and the message says so.
         */
        internal fun overflowMessage(
            promptBytes: Int,
            conversationBytes: Int,
            snapshotBytes: Int,
            limitBytes: Int,
        ): String? = when {
            promptBytes <= limitBytes -> null
            conversationBytes > limitBytes ->
                "This conversation is too long to send to your Aive device (${conversationBytes / 1000} KB, " +
                    "limit ${limitBytes / 1000} KB). Start a new chat or shorten it."
            else ->
                "The project plus this conversation is too large to send to your Aive device " +
                    "(project ${snapshotBytes / 1000} KB + conversation ${conversationBytes / 1000} KB, " +
                    "limit ${limitBytes / 1000} KB). Remove large files from the project, or start a new chat."
        }

        /** Keeps prompts inside the context window of the smaller hosted models Aive may use. */
        private const val SNAPSHOT_BYTES = 240_000
    }
}
