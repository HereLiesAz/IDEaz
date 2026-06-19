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

**Output model — PR-based loop (implemented):** when Jules opens a pull request it
surfaces in `Session.outputs[].pullRequest`. `JulesAdapter` polls `getSession` and,
when a PR appears, emits a terminal `TaskEvent.PullRequest(url, title)`. `AIDelegate`
forwards the URL via its `onAgentPullRequest` callback to
`BuildDelegate.installFromMergedPr`, which:

1. `PullRequestCoordinator.mergeAndGetSha(url)` — parse owner/repo/number, auto-merge
   (squash) via `GitHubApi.mergePullRequest`, return the merge commit SHA (idempotent:
   reuses the existing merge if the PR is already merged).
2. `RemoteBuildManager.pollAndDownload(mergeSha)` — poll GitHub Actions for the rebuilt
   APK on the merge commit, download it, and `ApkInstaller` sideloads it.

Unidiff `Patch` events are still applied to the working tree (the non-PR path); the two
are additive. Auto-launching the freshly installed APK is a follow-up — it needs the
async `PackageInstaller` result callback, so for now install lands via the system
installer prompt as before.

## Jules CLI — REMOVED

`JulesCliClient.kt` and the bundled `libjules.so` Node runtime have been **deleted** in Phase 0. On-device execution of the CLI was unstable across Android API levels (seccomp / signal handling). All Jules interaction goes through HTTP via `JulesApiClient`.
