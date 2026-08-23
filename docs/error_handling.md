# Error Handling & Crash Reporting

## Overview
IDEaz implements a robust, automatic error handling workflow for both the IDE itself and the user projects it creates. This ensures that failures are immediately reported to the AI Agent (Jules) with full context, enabling rapid self-repair.

## Mechanisms

### 1. Fatal Crashes
- **Component:** `CrashHandler` (JVM UncaughtExceptionHandler).
- **Behavior:** Intercepts fatal exceptions.
- **Action:** Starts `CrashReportingService` in a separate process (`:crash_reporter`).
- **Reporting:**
    - Creates a new Jules Session (Source: `HereLiesAz/IDEaz` or Project Repo).
    - Prompt: "CRASH REPORT from {User}: {StackTrace}".
    - Includes **Mandatory Instruction** for code quality.

### 2. Non-Fatal Errors
- **Component:** `ErrorCollector` (Singleton).
- **Behavior:** Collects exceptions from ViewModels, Coroutines, and other logic.
- **Deduplication:** Repeats of the same error are capped at 3 times.
- **Batching:** Errors are flushed and reported when the user navigates between screens (`IdeNavHost`).
- **Filtering:** "Noise" errors (Cancellation, etc.) are ignored.

### 3. User Project Injection
Removed. `ProjectInitializer` wrote a `CrashReporter.kt` into the user's own source tree with their API key and repository baked into a `Secrets.kt` beside it, and needed a hook in their `MainActivity.onCreate` to do anything. It only ever applied to the Android edit target, which no longer exists.

### 4. On-device Provider Failures
- **Boundary:** `LocalLlmAdapter` throws `LocalProviderException`; it does not return strings prefixed with `Error:`.
- **Policy:** `LocalProviderFailure` carries a stable kind, retryability, cloud-fallback eligibility, and diagnostic ID. Cloud fallback is disallowed after tool execution begins because local edits may already exist.
- **Diagnostics:** `LocalProviderDiagnostics` retains a process-local maximum of 32 sanitized records containing model/runtime IDs and exception class only. User prompts, source, arguments, outputs, and exception messages are excluded.
- **Presentation:** `MainViewModel` and `AIDelegate` render safe failure text and diagnostic IDs without appending failures to the model's conversational history. Coroutine cancellation is rethrown.
- **Recovery:** Retry requires the currently displayed diagnostic and replays locally without duplicating the user turn. Gemini fallback additionally requires an eligible pre-tool failure, configured credential, and a one-shot approval button whose disclosure names the conversation/project context and Google cloud destination. Consent is never persisted.

### 5. Credential Storage Failures
- **Component:** `SettingsViewModel` (`getApiKey`/`saveString`/`saveSigningCredentials`) wrapping `AndroidKeystoreCredentialStore`.
- **Diagnostic surface:** `SettingsViewModel.lastCredentialError` is set to the underlying exception's message (or class name) on every secure-credential read, write, or migration failure, and surfaced directly in the relevant Settings save Toast (e.g. "GitHub Token Save Failed: <reason>"). Most devices this ships to have no adb/logcat access, so this Toast is often the only diagnostic available for a real on-device failure — see `docs/auth.md` §2 for the full storage/migration model.
- **Independent success reporting:** `saveString` used to wrap both the real secure-credential write and an unrelated, best-effort legacy-plaintext-cleanup step in one `runCatching` block, so a cleanup hiccup (e.g. removing a key that was never there in plaintext) could report an otherwise-successful save as failed. The two outcomes are now tracked independently — cleanup failures are logged but never flip a successful write to a reported failure.
- **Known miss class:** a prior production bug had `AndroidKeystoreCredentialStore`'s encrypt path pass a caller-generated IV into `Cipher.init`, which every AndroidKeyStore-backed key rejects outright — every secure-credential save failed on real devices for as long as the bug shipped. It was invisible to unit tests because Robolectric doesn't simulate AndroidKeyStore's runtime restrictions; see `docs/testing.md` §2 for the coverage-gap writeup.

## Mandatory Instruction
All error reports include this instruction to the Agent:
> "You are required to get a perfect code review, no blocks or nitpicks allowed in it. Then you must get a passing build with tests. Once you have it, you must take the time to go get all of the documentation up to date before committing."
