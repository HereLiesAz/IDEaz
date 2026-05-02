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
 * A single turn in a conversational AI exchange. Role is `"user"` or `"model"`.
 *
 * Note: See also [com.hereliesaz.ideaz.ui.delegates.Message] which serves the same purpose
 * for the Jules provider. These will be unified in Phase 3.
 */
data class ChatMessage(val role: String, val content: String)
