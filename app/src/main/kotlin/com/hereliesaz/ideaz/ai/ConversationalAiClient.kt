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
     */
    suspend fun chat(messages: List<ChatMessage>): String
}

/** A single turn in a conversation. Role is "user" or "model". */
data class ChatMessage(val role: String, val content: String)
