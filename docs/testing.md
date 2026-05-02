# Testing Strategy

> **Authoritative source:** [`plans/2026-05-01-ideaz-revival-design.md`](plans/2026-05-01-ideaz-revival-design.md) §6 ("Testing strategy").

## 1. Where TDD pays off (unit tests)

* `ConversationalAiClient` adapters (Phase 1 `GeminiAdapter`) — mock HTTP.
* `AiTools` executors (`read_file`, `write_file`, `list_files`, `apply_patch`) — mock filesystem.
* `PwaProjectDetector` — fixture projects under `src/test/resources`.
* `WebViewBridge` message marshaling.
* `JulesApiClient` (Phase 2) — `MockWebServer`.
* Existing `Delegate` unit tests (`StateDelegate`, `AIDelegate`, etc.).

**Framework:** JUnit 4 + Robolectric + Mockito (or Mockk). Manual DI / simple stubs preferred over heavy mocking frameworks.

**Command:** `./gradlew :app:testDebugUnitTest`

## 2. Where TDD does *not* pay off (hand-test only)

* WebView rendering correctness.
* Overlay / AccessibilityService tap capture (Phase 2).
* The end-to-end loop.

Per-milestone smoke tests live in `docs/plans/<phase>-smoke-test.md`, ≤5 minutes each.

## 3. The floor (every PR)

1. `./gradlew :app:assembleDebug` green.
2. `./gradlew :app:testDebugUnitTest` green.
3. `./gradlew :app:lintDebug` green against the regenerated baseline.

## 4. Coverage targets

**None.** Coverage on a solo project burns time without preventing real bugs. Focus on the unit-test surface above.

## 5. Pre-commit checklist for AI agents

Per `AGENTS.md`:
1. Build green.
2. Tests green.
3. Manual smoke test of the touched path.
4. Docs updated.
5. `version.properties` bumped per the versioning strategy.

## 6. Stress testing
* **Polling:** Verify Jules polling does not time out even after 20+ minutes of inactivity.
* **Background:** Minimize the app while a remote build is polling. Verify the persistent notification still ticks log lines.
