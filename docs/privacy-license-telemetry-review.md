# Privacy, model-license, and telemetry review

Closes the last unchecked item in the "On-device Model Production Coverage"
checklist in `docs/TODO.md`. Scope: what IDEaz collects and transmits by
default, and what redistribution terms govern every model in the on-device
catalog. Findings are cited against the actual code/config, not asserted.

## Telemetry / analytics

**No analytics or telemetry SDK is present in the app.** Checked
`app/build.gradle.kts` and `gradle/libs.versions.toml` for Firebase Analytics,
Crashlytics, Mixpanel, Amplitude, Segment, Sentry, PostHog, or any equivalent
dependency, and grepped the source tree for calls to their known ingest
endpoints (`google-analytics.com`, `sentry.io`, `amplitude.com`, `mixpanel.com`,
`segment.io`, `posthog`). Zero matches. Nothing in this codebase phones home
usage data, screens, or events to any third party.

**Prompts, source, and model output are never collected by default** —
verified directly, not just absent-of-evidence:
- `LocalProviderDiagnostics` (`ai/local/LocalLlmAdapter.kt`) — the only
  in-process store of on-device failure history — retains at most 32 entries
  containing only a diagnostic kind, model/runtime ID, and exception class
  name. No prompt, tool argument, tool output, or exception message is ever
  stored in it (confirmed by reading every `record(...)` call site).
- Cloud fallback (Gemini) only ever transmits after an explicit, per-failure
  user tap on "Approve Gemini once" (`MainViewModel.approveCloudFallback`);
  nothing is queued or auto-retried into the cloud.
- The one-shot `ask_cloud` local-tool-loop consultation
  (`createLocalCloudConsultRequest`) requires the same kind of explicit,
  per-call consent with an exact byte-count/hash-bound preview shown before
  transmission.

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

**Minor, non-blocking finding:** `KEY_AUTO_REPORT_BUGS` /
`getAutoReportBugs()` / `setAutoReportBugs()` exist in `SettingsViewModel.kt`
but have zero other callers — an orphaned setting with no behavior attached to
it. Not a privacy issue (it does nothing), just dead code worth removing or
wiring up in a future pass.

## On-device model catalog — license audit

Every downloadable entry in `LocalModelCatalog.kt`, checked against the
source repository's own declared license (Hugging Face Hub API `tags`field,
`license:*`), not a README claim or assumption:

| Catalog entry | License | Source | Notes |
| --- | --- | --- | --- |
| `aicore-gemini-nano` | Google AICore terms | system service | Not a redistributed file — provided and updated by the device's AICore/Play services, governed by Google's service terms rather than a model-redistribution license. |
| `qwen2_5-0_5b-instruct-q4-gguf` | Apache-2.0 | `Qwen/Qwen2.5-0.5B-Instruct-GGUF` | Verified via Hub API tags. |
| `gemma-3n-e2b-it-q4-gguf` | Gemma | `lmstudio-community/gemma-3n-E2B-it-text-GGUF` | Gemma Terms of Use; repo itself is **not** HF-gated (LFS metadata fetched without auth). |
| `gemma-3n-e4b-it-q4-gguf` | Gemma | `unsloth/gemma-3n-E4B-it-GGUF` | Same as above. |
| `gemma-4-e2b-it-q4-gguf` | Apache-2.0 | `unsloth/gemma-4-E2B-it-GGUF` | Current-generation replacement for the retired, dead-URL Gemma 2 2B GGUF entry (see below). Not gated. |
| `phi3_5-mini-onnx` | MIT | `microsoft/Phi-3.5-mini-instruct-onnx` | Verified via Hub API tags. |
| `gemma2-2b-it-mediapipe` | Gemma | `litert-community/Gemma2-2B-IT` | Gemma Terms of Use; this repo **is** HF-gated (`requiresAuth = true` in the catalog, matching the app's existing HF-token flow). |

**Finding:** three catalog entries are Gemma-licensed, and the Gemma Terms of
Use (https://ai.google.dev/gemma/terms) condition redistribution on the
license being surfaced to the end user, not merely linked from an upstream
README the app never shows. The app had no license information anywhere in
its UI for any model.

**Fixed this session:** `LocalModel` gained `license`/`licenseUrl` fields,
populated for every entry from the table above. `OnDeviceModelsSection.kt`
now shows a "License: <name> ↗" line under every model, opening the license's
full text on tap.

## Dead catalog URLs found and fixed (see `docs/TODO.md` for the on-device
model audit bullets)

Two catalog entries had upstream URLs that no longer resolve, discovered
while cross-referencing the license table above:
- `unsloth/gemma-2-2b-it-GGUF` (401 at the Hub API, no successor with the same
  name — the quantizer ecosystem has moved on to Gemma 4). Replaced with the
  actively-maintained `unsloth/gemma-4-E2B-it-GGUF`.
- `google/gemma-2-2b-it` (the old MediaPipe `.task` entry's source) ships only
  raw `.safetensors` weights, never had a `.task` bundle. Repointed to
  `litert-community/Gemma2-2B-IT`, Google's own org for MediaPipe/LiteRT
  conversions, which genuinely contains one.

Both replacements are logged in the code comments at their `LocalModel`
declarations with the specific evidence (HTTP status, Hub API tree contents)
that motivated the change.

## What this review does not and cannot cover

Real device telemetry from the Android OS itself (crash reports Google Play
may collect independently of this app, ordinary system diagnostics) is
outside this app's control and outside this review's scope — it covers only
what *this codebase* actively does.
