# Jules Integration

> **Status: Phase 2.** Jules is the agentic AI provider for the Android target loop.
> The Phase 1 daily-driver loop uses **Gemini** (conversational, BYO-key, tool-use)
> via `ConversationalAiClient`. Jules call sites are stubbed in Phase 0 to compile;
> they are restored when Phase 2 starts.

## Jules API Client (`JulesApiClient`)

Retrofit-based Kotlin client. Mirrors the official TypeScript SDK shape.

### Key Features
*   **Singleton, lazy-initialized Retrofit client.**
*   **Testable `baseUrl` (`@VisibleForTesting`)** for `MockWebServer`.
*   **Session management:** `createSession(prompt, source)`.
*   **Activity polling:** `listActivities`, `sendMessage` — used to retrieve plans, patches, messages.
*   **Source listing:** `listSources` — fetches available repositories from the user's account.
*   **Robustness:** `AuthInterceptor` for auth, `RetryInterceptor` for backoff, standard HTTP error handling.

### Phase 2 usage in app
Owned by `AIDelegate`:

*   Fetch the list of owned sources for the Setup tab.
*   Create new sessions for prompts captured from element-tap context.
*   Poll for "Patch" activities and auto-merge resulting PRs.

`AgenticAiClient.dispatchTask(prompt, sourceContext, existingSessionId): Flow<TaskEvent>`
is the Phase 2 abstraction (`ai/AgenticAiClient.kt`); **`JulesAdapter` (`jules/JulesAdapter.kt`)
implements it** on top of `JulesApiClient`, owning the create/resume-session +
activity-poll loop and emitting `TaskEvent`s (`SessionStarted`/`Message`/`Patch`/`TimedOut`).
`AIDelegate.runJulesTask` just collects the flow — wiring events to the overlay log and
the patch-apply callback. The chat UI is target-agnostic — the same event shape works
for both the PWA-loop (Gemini) and Android-loop (Jules) targets.

**Provider default is project-type-aware** (`AIDelegate.defaultOverlayModel`): an
explicit Settings → AI Assignments choice always wins; otherwise **Android targets
default to Jules**, web-like projects default to Gemini.

**Output model:** the current loop applies Jules' returned unidiff patches to the
working tree. The Phase-2 target (per the design) is **PR-based** — Jules opens a PR,
IDEaz auto-merges, GitHub Actions rebuilds the APK, and `ApkInstaller` re-sideloads.
That PR/auto-merge/build loop is the next increment (adding a `PullRequest` `TaskEvent`
and consuming `Session.outputs[].pullRequest`).

## Jules CLI — REMOVED

`JulesCliClient.kt` and the bundled `libjules.so` Node runtime have been **deleted** in Phase 0. On-device execution of the CLI was unstable across Android API levels (seccomp / signal handling). All Jules interaction goes through HTTP via `JulesApiClient`.
