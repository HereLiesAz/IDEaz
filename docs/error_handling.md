# Error Handling & Crash Reporting

## Overview
IDEaz reports its own fatal crashes and batched non-fatal errors as GitHub
issues on the configured repository, gated by an explicit first-run consent
dialog and a Settings toggle (`KEY_REPORT_IDE_ERRORS`). Stack traces are
sanitized ([`LogSanitizer`](../app/src/jvmSharedMain/kotlin/com/hereliesaz/ideaz/utils/LogSanitizer.kt))
before they leave the device.

## Mechanisms

### 1. Fatal Crashes
- **Component:** `CrashHandler` (JVM UncaughtExceptionHandler).
- **Behavior:** Intercepts fatal exceptions.
- **Action:** Starts `CrashReportingService` in a separate process (`:crash_reporter`).
- **Reporting:** Files a GitHub issue via `GithubIssueReporter.reportError` (context message, device manufacturer/model/SDK version, and the sanitized stack trace), gated on the user's `KEY_REPORT_IDE_ERRORS` consent and a configured GitHub token.

### 2. Non-Fatal Errors
- **Component:** `ErrorCollector` (Singleton).
- **Behavior:** Collects exceptions from ViewModels, Coroutines, and other logic.
- **Deduplication:** Repeats of the same error are capped at 3 times.
- **Batching:** Errors are flushed and reported when the user navigates between screens (`IdeNavHost`).
- **Filtering:** "Noise" errors (Cancellation, etc.) are ignored.

### 3. User Project Injection
Removed. `ProjectInitializer` wrote a `CrashReporter.kt` into the user's own source tree with their API key and repository baked into a `Secrets.kt` beside it, and needed a hook in their `MainActivity.onCreate` to do anything. It only ever applied to the Android edit target, which no longer exists.

### 4. AI Provider Failures
- **Boundary:** each `ConversationalAiClient.chat()` implementation (`GeminiAdapter`, `AnthropicAdapter`, `OpenAiCompatibleAdapter`) signals a handled failure by returning a string prefixed with `Error: ` rather than throwing; `MainViewModel.sendChatMessage` renders that as an error bubble instead of an ordinary assistant reply. `CancellationException` is rethrown, not swallowed.
- **Recovery:** the user retries by sending another message; there is no separate retry affordance.

### 5. Credential Storage Failures
- **Component:** `SettingsViewModel` (`getApiKey`/`saveString`/`saveSigningCredentials`) wrapping `AndroidKeystoreCredentialStore`.
- **Diagnostic surface:** `SettingsViewModel.lastCredentialError` is set to the underlying exception's message (or class name) on every secure-credential read, write, or migration failure, and surfaced directly in the relevant Settings save Toast (e.g. "GitHub Token Save Failed: <reason>"). Most devices this ships to have no adb/logcat access, so this Toast is often the only diagnostic available for a real on-device failure — see `docs/auth.md` §2 for the full storage/migration model.
- **Independent success reporting:** `saveString` used to wrap both the real secure-credential write and an unrelated, best-effort legacy-plaintext-cleanup step in one `runCatching` block, so a cleanup hiccup (e.g. removing a key that was never there in plaintext) could report an otherwise-successful save as failed. The two outcomes are now tracked independently — cleanup failures are logged but never flip a successful write to a reported failure.
- **Known miss class:** a prior production bug had `AndroidKeystoreCredentialStore`'s encrypt path pass a caller-generated IV into `Cipher.init`, which every AndroidKeyStore-backed key rejects outright — every secure-credential save failed on real devices for as long as the bug shipped. It was invisible to unit tests because Robolectric doesn't simulate AndroidKeyStore's runtime restrictions; see `docs/testing.md` §2 for the coverage-gap writeup.
