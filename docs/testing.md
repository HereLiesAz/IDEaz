# Testing Strategy

> **Authoritative source:** [`plans/2026-05-01-ideaz-revival-design.md`](plans/2026-05-01-ideaz-revival-design.md) §6 ("Testing strategy").

## 1. Where TDD pays off (unit tests)

* `OperationState`/`OperationController<T>` — the shared long-running-operation state machine (`models/OperationState.kt`, `utils/OperationController.kt`): `isRetryable`/`isTerminal` predicates, `OperationProgress` fraction bounds, Queued→Running→Succeeded/Failed/Cancelled transitions, progress reporting through the `report` callback, the "one operation at a time" guard, retryable-vs-`NonRetryableOperationException` classification, and cancellation. Existing delegates (Git fetch/pull/push/stash, clone, build, deploy, download, update) are migrated onto this incrementally, not all at once — see the type's own doc comment for why it exists.
* `ConversationalAiClient` adapters (Phase 1 `GeminiAdapter`) — mock HTTP.
* Local-model structured-response parsing — fenced tool calls, final responses, and malformed-output fallback.
* Local-model file integrity — independently calculated valid digest and known-invalid digest rejection.
* Local-model storage preflight — exact boundary acceptance and one-byte-short rejection.
* Local-model WorkManager policy — stable unique naming and transient/permanent retry classification; Android integration tests for constraints, progress, cancellation, and restoration remain on the production checklist.
* Local-model partial reconciliation — catalog-bound identity retention/deletion and strict HTTP `Content-Range` offset/total validation.
* Provider credential storage (`SettingsViewModelTest`, via a `FakeCredentialStore`, not real Android Keystore crypto — see the coverage-gap note in §2) — Gemini/Hugging Face/GitHub-token/six-provider-key default-preference exclusion, legacy-plaintext migration after successful secure persistence, retention of the legacy value on a failed secure migration, signing-password secure storage (alias stays plaintext), AES-GCM round trip, and tamper rejection. Also covers `lastCredentialError` (populated on every secure-credential read/write/migration failure, cleared on success) and that `saveString`'s real secure write and its best-effort legacy-plaintext cleanup report success/failure independently — a cleanup hiccup must never mask a write that actually succeeded.
* Default AI model selection (`SettingsViewModelTest`) — `AiModels.defaultRanking` walk order, falling back to Gemini when no provider key is saved, and an explicit "Default" AI assignment overriding the ranked auto-pick.
* Local inference resource policy — exact RAM-tier boundaries and prompt truncation that retains protocol instructions, response suffix, and newest transcript content.
* Structured local failures — retry/fallback metadata, safe presentation, bounded diagnostic eviction, and exclusion of cause messages from retained records.
* Local recovery policy — current-diagnostic matching, retry eligibility, cloud safety, credential gating, and post-tool fallback denial.
* Local cloud consultation — local-only schema isolation, exact payload/project/conversation hashing, stale/tampered consent rejection, and strict question/context ceilings.
* Local tool coordinator — exact six-call exhaustion, structured round-limit signaling, cancellation propagation, and one restoration callback.
* Model download streaming — chunk-boundary cancellation, retained resumable bytes/progress, exact-size rejection, and URL/size/digest catalog migration.
* `IdeTools` tool executors (`read_file`, `write_file`, `list_files`, `apply_patch`) — mock filesystem.
* `IdeTools` edit review — changed-file inventory, malformed JSON rejection, drift refusal, patch deletion capture, process-death review recovery (including ambiguous-write rollback denial), and checkpoint restoration that preserves pre-existing work.
* `ProjectAnalyzer` — synthetic project directories built with JUnit's `TemporaryFolder` rule (no fixture files under `src/test/resources`).
* `WebViewBridge` message marshaling.
* `JulesApiClient` (Phase 2) — `MockWebServer`.
* Existing `Delegate` unit tests (`StateDelegate`, `AIDelegate`, etc.).

**Framework:** JUnit 4 + Robolectric + Mockito (or Mockk). Manual DI / simple stubs preferred over heavy mocking frameworks.

**Command:** `./gradlew :app:testDebugUnitTest`

## 2. Where TDD does *not* pay off (hand-test only)

* WebView rendering correctness.
* Overlay / AccessibilityService tap capture (Phase 2).
* The end-to-end loop.
* Real AndroidKeyStore-backed crypto behavior. Robolectric doesn't simulate AndroidKeyStore's runtime restrictions, so a unit test built on a plain software key (`KeyGenerator.getInstance("AES")`, as `SettingsViewModelTest`'s credential-envelope test does) cannot see failures specific to a Keystore-backed key. Concrete example: `AndroidKeystoreCredentialStore`'s encrypt path once called `Cipher.init(ENCRYPT_MODE, key, GCMParameterSpec(...))` with a caller-generated IV; an AndroidKeyStore key created with `setRandomizedEncryptionRequired(true)` (the default) rejects that outright with `InvalidAlgorithmParameterException`, so *every* secure-credential save failed on real devices for the entire time the bug existed — invisible to the full unit-test suite. Fixed by letting the provider generate the IV and reading it back via `cipher.iv` (see `encryptCredential` in `AndroidKeystoreCredentialStore.kt`). Anything that depends on AndroidKeyStore's actual behavior (not just the crypto math) needs on-device or instrumented testing.

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
