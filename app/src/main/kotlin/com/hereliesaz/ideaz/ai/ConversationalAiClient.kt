package com.hereliesaz.ideaz.ai

/**
 * Provider-agnostic interface for conversational AI with tool use.
 *
 * Callers pass the full conversation history as [messages] so implementations
 * can maintain multi-turn context. The implementation is responsible for any
 * internal tool-use loops.
 */
interface ConversationalAiClient {
    /**
     * Send the conversation history; return the AI's next text response.
     * Must be called from a coroutine (network I/O).
     *
     * @param messages The full ordered conversation history (user + model turns).
     * @return The AI's text response after any internal tool-use loops complete.
     * @throws Exception (implementation-specific) on network failure or API error.
     *   Callers are responsible for catching and surfacing errors to the user.
     */
    suspend fun chat(messages: List<ChatMessage>): String
}

/**
 * A single part of a chat message. Messages can mix text, images, and other
 * binary blobs (e.g. PDFs for Gemini). Each adapter translates the part list
 * into its provider's native shape.
 *
 * Note: [Image] and [FileBlob] carry `ByteArray`s. Kotlin's data class
 * equality is reference-based for arrays — do not rely on `==` to compare
 * two messages for "same content." We append rather than dedupe in the
 * tool-use loops, so this is fine in practice.
 */
sealed interface ChatPart {
    data class Text(val text: String) : ChatPart
    data class Image(val bytes: ByteArray, val mimeType: String) : ChatPart
    /**
     * Binary file blob (PDF, etc.). Adapters that don't support the mime type
     * either drop it (with a note) or surface a clear error.
     */
    data class FileBlob(val bytes: ByteArray, val mimeType: String, val fileName: String? = null) : ChatPart
}

/**
 * A single turn in a conversational AI exchange. Role is `"user"` or `"model"`.
 *
 * The canonical constructor takes a [parts] list so messages can be multimodal.
 * The companion-invoke shim `ChatMessage(role, content: String)` keeps existing
 * call sites — `ChatMessage("user", "hello")` — working unchanged; it wraps the
 * string in a single [ChatPart.Text].
 *
 * Note: See also [com.hereliesaz.ideaz.ui.delegates.Message] which serves the same purpose
 * for the Jules provider. These will be unified in Phase 3.
 */
data class ChatMessage(val role: String, val parts: List<ChatPart>) {
    /** Joined text from every [ChatPart.Text] in [parts]. Empty if no text parts. */
    val content: String
        get() = parts.filterIsInstance<ChatPart.Text>().joinToString("\n") { it.text }

    companion object {
        /**
         * Source-compat shim: `ChatMessage("user", "hello")` keeps working. New
         * call sites that need multimodal pass a `List<ChatPart>` directly to
         * the primary constructor.
         */
        operator fun invoke(role: String, content: String): ChatMessage =
            ChatMessage(role, listOf(ChatPart.Text(content)))
    }
}
