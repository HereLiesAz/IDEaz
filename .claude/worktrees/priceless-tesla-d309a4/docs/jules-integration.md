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

`AgenticAiClient.dispatchTask(prompt, context): Flow<TaskEvent>` is the Phase 2 abstraction; `JulesAdapter` will implement it on top of `JulesApiClient`. The chat UI is target-agnostic — the same `Flow<TaskEvent>` shape works for both PWA-loop (Gemini) and Android-loop (Jules) targets.

## Jules CLI — REMOVED

`JulesCliClient.kt` and the bundled `libjules.so` Node runtime have been **deleted** in Phase 0. On-device execution of the CLI was unstable across Android API levels (seccomp / signal handling). All Jules interaction goes through HTTP via `JulesApiClient`.
