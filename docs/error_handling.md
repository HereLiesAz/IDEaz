# Error Handling

## Overview
IDEaz does **not** report errors, crashes or build logs anywhere. There is no crash handler that
posts, no error collector, no reporting service, no first-run consent dialog and no reporting
toggle in Settings. A failure is shown to the user and written to the on-device log, and that is
where it stays. The only data that leaves the device is what the user sends to the AI providers
and the GitHub / Cloudflare services they configure (see [`../PRIVACY.md`](../PRIVACY.md)).

If the user wants to hand a log to someone, they do it themselves: the console's "Copy log"
action puts a [`LogSanitizer`](../app/src/jvmSharedMain/kotlin/com/hereliesaz/ideaz/utils/LogSanitizer.kt)-redacted
copy on the clipboard, and `RepoSnapshot` uses the same sanitizer for the project snapshot it
shares. Nothing sends it for them.

## Mechanisms

### 1. Fatal Crashes
Android's default handler runs and the process ends. IDEaz installs no uncaught-exception handler
of its own and starts no service, so nothing is sent.

### 2. Non-Fatal Errors
Surfaced where they happen: an error bubble in the chat, a toast, or a line in the console. They
are written to logcat (`Log.*`) at the level chosen in Settings → Log Level. There is no
collection, batching or flush on navigation.

### 3. User Project Injection
Removed. `ProjectInitializer` wrote a `CrashReporter.kt` into the user's own source tree with their API key and repository baked into a `Secrets.kt` beside it, and needed a hook in their `MainActivity.onCreate` to do anything. It only ever applied to the Android edit target, which no longer exists.

### 4. AI Provider Failures
- **Boundary:** each `ConversationalAiClient.chat()` implementation (`GeminiAdapter`, `AnthropicAdapter`, `OpenAiCompatibleAdapter`) signals a handled failure by returning a string prefixed with `Error: ` rather than throwing; `MainViewModel.sendChatMessage` renders that as an error bubble instead of an ordinary assistant reply. `CancellationException` is rethrown, not swallowed.
- **Recovery:** the user retries by sending another message; there is no separate retry affordance.
- **Quota and rate limits (automatic failover):** an adapter that sees HTTP 429/402, a named quota error (`insufficient_quota`, `quota_exceeded`, `RESOURCE_EXHAUSTED`) or a `Retry-After` longer than 60 s throws `ProviderQuotaException` instead of returning an `Error: ` string (after restoring any half-applied edit). `FailoverClient` ([`ProviderFailover.kt`](../app/src/jvmSharedMain/kotlin/com/hereliesaz/ideaz/ai/ProviderFailover.kt)) marks that provider as cooling down until the reset time and retries the same request on the next one: keyless providers first (Kilo, LLM7, OVHcloud), then any provider the user has a key for, skipping those cooling down. Each provider is tried at most once per request and the total is bounded. Cooldowns live in memory and are persisted in `KEY_PROVIDER_COOLDOWNS` so a restart does not forget them. A short `Retry-After` (60 s or less) keeps `OpenAiCompatibleAdapter`'s ordinary retry policy; only when those retries run out does the provider cool down. On a switch the chat shows one line ("<A> hit its daily limit. Switched to <B>."), and the reply is labelled with the provider that answered. When every provider is cooling down the chat shows a plain line with the earliest reset in hours and minutes; the raw provider error is behind a "Details" toggle (`ChatMessage.details`). `RetryInterceptor` (GitHub and other idempotent calls) likewise no longer sleeps through a `Retry-After` beyond 60 s.

### 5. Credential Storage Failures
- **Component:** `SettingsViewModel` (`getApiKey`/`saveString`/`saveSigningCredentials`) wrapping `AndroidKeystoreCredentialStore`.
- **Diagnostic surface:** `SettingsViewModel.lastCredentialError` is set to the underlying exception's message (or class name) on every secure-credential read, write, or migration failure, and surfaced directly in the relevant Settings save Toast (e.g. "GitHub Token Save Failed: <reason>"). Most devices this ships to have no adb/logcat access, so this Toast is often the only diagnostic available for a real on-device failure — see `docs/auth.md` §2 for the full storage/migration model.
- **Independent success reporting:** `saveString` used to wrap both the real secure-credential write and an unrelated, best-effort legacy-plaintext-cleanup step in one `runCatching` block, so a cleanup hiccup (e.g. removing a key that was never there in plaintext) could report an otherwise-successful save as failed. The two outcomes are now tracked independently — cleanup failures are logged but never flip a successful write to a reported failure.
- **Known miss class:** a prior production bug had `AndroidKeystoreCredentialStore`'s encrypt path pass a caller-generated IV into `Cipher.init`, which every AndroidKeyStore-backed key rejects outright — every secure-credential save failed on real devices for as long as the bug shipped. It was invisible to unit tests because Robolectric doesn't simulate AndroidKeyStore's runtime restrictions; see `docs/testing.md` §2 for the coverage-gap writeup.
