package com.hereliesaz.ideaz.ai.local

import android.content.Context
import com.hereliesaz.ideaz.ai.ChatMessage
import com.hereliesaz.ideaz.ai.ConversationalAiClient

/**
 * [ConversationalAiClient] backed by the user's selected on-device model. Flattens
 * the conversation into a prompt, dispatches to the model's runtime, and returns
 * its text.
 *
 * When it can't run, it returns a clear, actionable message rather than throwing —
 * no model selected, the file isn't downloaded, or the runtime backend isn't in
 * this build yet — so the UI shows guidance instead of a crash.
 */
class LocalLlmAdapter(
    private val context: Context,
    private val store: LocalModelStore,
    private val downloads: ModelDownloadManager,
) : ConversationalAiClient {

    override suspend fun chat(messages: List<ChatMessage>): String {
        val model = store.activeModel()
            ?: return "Error: No on-device model selected. Pick one in Settings → On-device models."
        if (!downloads.isDownloaded(model)) {
            return "Error: \"${model.name}\" isn't downloaded yet. Download it in Settings → On-device models."
        }
        val runtime = LocalModelRuntimes.byId(model.runtimeId)
            ?: return "Error: No runtime '${model.runtimeId}' is registered for ${model.name}."
        if (!runtime.isAvailable(context)) {
            return "Error: The ${runtime.displayName} backend isn't included in this build yet."
        }

        val prompt = buildString {
            messages.forEach { m ->
                val who = if (m.role == "user") "User" else "Assistant"
                append(who).append(": ").append(m.content).append('\n')
            }
            append("Assistant:")
        }

        // System-managed runtimes (AICore) ignore the file; pass a harmless path.
        val modelFile = if (model.systemManaged) context.filesDir else downloads.fileFor(model)
        return try {
            runtime.generate(context, modelFile, prompt)
        } catch (e: Exception) {
            "Error: On-device generation failed: ${e.message}"
        }
    }
}
