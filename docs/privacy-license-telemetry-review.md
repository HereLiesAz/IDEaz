# Privacy and telemetry review

Effective 2026-10-23. Policy text: [`../PRIVACY.md`](../PRIVACY.md).

Scope: what IDEaz collects and transmits by default. Findings are cited
against the actual code/config, not asserted.

**Summary: IDEaz sends no automatic error, crash or build-log reports. The only data
leaving the device is what the user sends to the AI providers and the GitHub /
Cloudflare services they configure.**

## Telemetry / analytics

**No analytics or telemetry SDK is present in the app.** Checked
`app/build.gradle.kts` and `gradle/libs.versions.toml` for Firebase Analytics,
Crashlytics, Mixpanel, Amplitude, Segment, Sentry, PostHog, or any equivalent
dependency, and grepped the source tree for calls to their known ingest
endpoints (`google-analytics.com`, `sentry.io`, `amplitude.com`, `mixpanel.com`,
`segment.io`, `posthog`). Zero matches. Nothing in this codebase phones home
usage data, screens, or events to any third party.

**Prompts, source, and model output are sent only to the AI provider the user
configured**, via that provider's own API — never to any IDEaz-operated
endpoint. There is no local buffering, queuing, or store of prompt/response
content outside the in-memory chat history the UI already shows.

**Automatic provider switching.** When a provider reports an exhausted quota or rate
limit, `FailoverClient` (`ai/ProviderFailover.kt`) resends the same request to the next
provider: keyless ones first, then any provider the user saved a key for. Each provider is
tried at most once per request, and the chat shows a notice. So a prompt can reach a
provider other than the one assigned in Settings; `PRIVACY.md` discloses this. The only
state kept is which providers are cooling down and until when (`provider_cooldowns`, device
local, not part of a settings import).

Two choices route prompts elsewhere, and only when the user selects them:

- **Keyless models** (Kilo Gateway, LLM7, OVHcloud) are called without a key.
  These anonymous free tiers may log prompts.
- **The Aive** receives the redacted project snapshot and the conversation, and
  forwards them to whichever provider the user linked in The Aive
  (`docs/aive-integration.md`).
- **The Aive on another device**, when the user pastes a pairing code in
  Settings, receives the same snapshot and conversation through a Cloudflare
  relay. Frames are AES-256-GCM encrypted with a key that is in the pairing
  code, so the relay sees ciphertext and metadata (room id, sizes, timing,
  online state) only. The code is stored in the credential store with the API
  keys.

## Crash / error reporting - removed

Earlier versions had one default-on data flow: `CrashHandler` and a build-failure heuristic
in `MainViewModel` called `GithubIssueReporter.reportError`, which, when a GitHub token was
configured, filed an issue on the public `HereLiesAz/IDEaz` repository with device
manufacturer/model/SDK and a `LogSanitizer`-redacted stack trace or build-log excerpt. A
non-fatal `ErrorCollector` batched errors to the same place on screen changes, and a
first-run dialog and a Settings toggle (`KEY_REPORT_IDE_ERRORS`, default on) governed it.

**All of it is deleted.** Removed: `CrashHandler` and its installation in `MainApplication`,
`CrashReportingService` (and its `:crash_reporter` manifest entry), `ErrorCollector` and its
flush on navigation, `MainViewModel.handleBuildFailure`, `GithubIssueReporter` (and the
`createIssue` call and its request/response types in `GithubApiClient`), the first-run
consent dialog, `hasShownCrashReportingFirstRun`, `KEY_REPORT_IDE_ERRORS` and the Settings
row, and their tests. The "Auto-send build failures" Settings row was removed with
`handleBuildFailure`, the only code that read it. `SettingsViewModel` deletes the leftover
preference keys and the `bug_report_prefs` file on startup.

Verified by grep over `app/src`: no remaining reference to any of those symbols, and no
call that posts an error, crash or log to any endpoint. `LogSanitizer` stays: it redacts the
console's "Copy Log" output and the project snapshot (`RepoSnapshot`) that goes to the AI
provider the user chose. No user-initiated "report a problem" action exists; if one is added
it must be explicit per use and show what will be sent first.

## What this review does not and cannot cover

Real device telemetry from the Android OS itself (crash reports Google Play
may collect independently of this app, ordinary system diagnostics) is
outside this app's control and outside this review's scope — it covers only
what *this codebase* actively does.
