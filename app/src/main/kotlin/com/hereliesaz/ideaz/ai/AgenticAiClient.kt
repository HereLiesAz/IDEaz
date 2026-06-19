package com.hereliesaz.ideaz.ai

import com.hereliesaz.ideaz.api.Session
import com.hereliesaz.ideaz.api.SourceContext
import kotlinx.coroutines.flow.Flow

/**
 * One thing that happens while an agentic provider works a task. The chat UI is
 * target-agnostic: the Android (Jules) loop emits these just as the PWA (Gemini)
 * loop drives [ConversationalAiClient], so both render through the same overlay.
 */
sealed interface TaskEvent {
    /** A session was created for this dispatch (carries the API [Session] for the UI). */
    data class SessionStarted(val session: Session) : TaskEvent

    /** A human-readable message from the agent. */
    data class Message(val text: String) : TaskEvent

    /** A unidiff patch the agent produced, to apply to the working tree. */
    data class Patch(val unidiff: String) : TaskEvent

    /** The agent didn't reach a terminal state within the poll window. */
    object TimedOut : TaskEvent
    // PullRequest(url) is added with the PR-based loop increment (auto-merge + rebuild).
}

/**
 * Agentic (async, PR/patch-producing) AI provider — the Phase-2 counterpart to the
 * conversational [ConversationalAiClient]. Currently implemented by
 * [com.hereliesaz.ideaz.jules.JulesAdapter] over the Jules REST API.
 */
interface AgenticAiClient {
    /**
     * Dispatch [prompt] against [sourceContext] (the GitHub repo anchor). When
     * [existingSessionId] is null a new session is created; otherwise the prompt is
     * sent to that session. Returns a cold [Flow] of [TaskEvent]s; collect it to
     * drive the UI and apply patches.
     */
    fun dispatchTask(
        prompt: String,
        sourceContext: SourceContext,
        existingSessionId: String? = null,
    ): Flow<TaskEvent>
}
