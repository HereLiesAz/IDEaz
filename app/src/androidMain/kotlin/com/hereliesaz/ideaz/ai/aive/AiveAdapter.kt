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
        val prompt = buildString {
            appendLine("You are acting as IDEaz's coding agent for the project below.")
            appendLine("Study the project and continue the conversation that follows it.")
            appendLine(TextPatchEdits.DIFF_PROTOCOL.trim())
            appendLine()
            appendLine("PROJECT:")
            appendLine(snapshot.text)
            appendLine()
            appendLine("IDEAZ CONVERSATION:")
            append(TextPatchEdits.serializeConversation(messages))
        }
        val promptBytes = prompt.toByteArray(Charsets.UTF_8).size
        if (client.isRemote && promptBytes > AiveRelayClient.MAX_PROMPT_BYTES) {
            // The conversation alone is too long for the relay; shrinking the snapshot cannot fix that.
            error(
                "This conversation is too long to send to your Aive device (${promptBytes / 1000} KB, " +
                    "limit ${AiveRelayClient.MAX_PROMPT_BYTES / 1000} KB). Start a new chat or shorten it."
            )
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
        /** Keeps prompts inside the context window of the smaller hosted models Aive may use. */
        private const val SNAPSHOT_BYTES = 240_000
    }
}
