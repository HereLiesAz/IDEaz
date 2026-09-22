# Privacy and telemetry review

Scope: what IDEaz collects and transmits by default. Findings are cited
against the actual code/config, not asserted.

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

## Crash / error reporting — the one real default-on data flow

`CrashHandler.handleCrash` and a build-failure heuristic in
`MainViewModel.kt:~1650` both call `GithubIssueReporter.reportError`, which —
when a GitHub token is configured — files an issue directly on the **public**
`HereLiesAz/IDEaz` repository containing: a context message, device
manufacturer/model/SDK version, and a `LogSanitizer`-redacted stack trace or
build-log excerpt (capped at 4000 characters). It never includes prompts,
source files, or project content — confirmed by reading the `bodyContent`
template in `GithubIssueReporter.kt`; the `logContent` parameter it accepts is
never actually interpolated into the body (dead parameter, not a leak).

**The finding:** `SettingsViewModel.isReportIdeErrorsEnabled()` defaulted to
`true` with no first-run disclosure — only a post-hoc, discoverable Settings
toggle ("Report IDE errors to HereLiesAz/IDEaz (Issues)"). A user who never
opens Settings is opted into automatic public issue-filing on their first
crash without ever having agreed to it. `LogSanitizer` mitigates the *content*
risk but doesn't address the *consent* gap — sanitization catches known secret
patterns, not the general question of whether data should leave the device by
default at all.

**Fixed this session:** added a one-time, unconditional first-run disclosure
dialog (`MainScreen.kt`, mirroring the existing Gemini-bridge first-run
pattern) that states exactly what's collected and where it goes, before the
default can ever take effect silently. "Allow" keeps the default; "Don't
allow" flips it off. Backed by `hasShownCrashReportingFirstRun()` /
`markCrashReportingFirstRunShown()` in `SettingsViewModel.kt`, with a unit
test. Sequenced to appear before (not stacked with) the pre-existing
Gemini-bridge dialog on a genuinely first launch.

## What this review does not and cannot cover

Real device telemetry from the Android OS itself (crash reports Google Play
may collect independently of this app, ordinary system diagnostics) is
outside this app's control and outside this review's scope — it covers only
what *this codebase* actively does.
