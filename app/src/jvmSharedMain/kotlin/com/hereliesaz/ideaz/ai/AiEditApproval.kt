package com.hereliesaz.ideaz.ai

/**
 * A pending, validated AI edit awaiting an explicit user decision.
 *
 * Every provider produces these — Gemini, Anthropic, and every OpenAI-compatible
 * backend share one checkpoint/review/approve/undo contract built on [IdeTools].
 * The AI writes into the working tree behind an out-of-tree checkpoint; nothing
 * reaches the preview until the user says yes.
 *
 * (These types previously lived in an `ai.local` package and were named
 * `LocalEditApproval*`, which implied they were on-device-specific. They never
 * were — only [source] varies by provider.)
 */
data class AiEditApproval(
    val review: IdeEditReview,
    val response: String,
    /** Which provider produced the edit, for the review card's title. */
    val source: String,
)

/**
 * Control-flow boundary that stops an AI edit from being reloaded before the user
 * has approved it. Adapters throw this at the end of any turn that changed files;
 * the response text rides along so it can be shown once the edit is accepted.
 */
class AiEditApprovalRequiredException(
    val approval: AiEditApproval,
) : Exception("AI edits require approval")
