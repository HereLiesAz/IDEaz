# IDEaz Roadmap

The TODO list previously here was a milestone retrospective for the
abandoned-and-now-revived project. It has been superseded.

**Active design doc:** [`plans/2026-05-01-ideaz-revival-design.md`](plans/2026-05-01-ideaz-revival-design.md)
**Active phase plan:** [`plans/2026-05-01-phase-0-triage.md`](plans/2026-05-01-phase-0-triage.md)

When Phase 0 completes, this file will point to Phase 1's plan.

**Production-readiness UX audit:** [`ux_userflow_audit.md`](ux_userflow_audit.md)

## Production Readiness
- Close all P0 release blockers in the UX user-flow audit before production release.
- Execute and retain the device, accessibility, failure-injection, privacy, and store-policy evidence required by the audit.
- Completed the first P0 mitigation: PWA is the sole selectable production target; other repository types remain detectable but creation, initialization, and unfinished App View mounting are release-gated until their loops are complete.
- Corrected dependency submission to inventory only release-runtime configurations, preventing CI-only Gradle/AGP/test/lint dependency instances from being conflated with the shipped graph while retaining the pinned runtime Jackson and Bouncy Castle dependencies.
- Added bounded on-device tool use: local models can now read, list, write, and patch project files through the same sandbox as cloud providers, with strict round limits and plain-text fallback.

## On-device Model Production Coverage

The on-device provider is not production-complete until every unchecked item below has device evidence. A model producing one charming sentence on the maintainer's phone is a demo, not a subsystem.

- [x] Provide a unified runtime registry with hardware/backend availability filtering.
- [x] Provide download, HTTP-range resume, progress, selection, and deletion controls.
- [x] Connect the selected local model to `ConversationalAiClient` assignments.
- [x] Add bounded `read_file`, `write_file`, `list_files`, and `apply_patch` tool use with malformed-output fallback.
- [x] Implement staged exact-size/SHA-256 verification, corrupt-payload deletion, atomic activation, and manifest-bound verification markers.
- [ ] Reduce the production catalog to models whose URL, immutable revision, exact files, license, and redistribution terms have been manually verified.
- [ ] Populate and require an exact byte count and SHA-256 for every downloadable production catalog file.
- [x] Preflight available storage, including existing partial/final bytes, download staging space, and a 256 MiB safety reserve, before network work begins.
- [x] Move downloads to unique WorkManager jobs with connected-network constraints, foreground notification progress, cancellation, bounded retry/backoff, and process-death restoration.
- [ ] Persist download state and reconcile interrupted `.part` files, server range behavior, catalog changes, and user deletion.
- [ ] Move gated-provider tokens from plain preferences into the encrypted credential path; redact them from logs and exports unless explicitly requested.
- [ ] Cache inference engines safely, serialize access per backend, unload on memory pressure/model change, and bound context/output tokens per device tier.
- [ ] Replace assistant-text `Error:` responses with structured local-provider failures supporting retry, fallback, and retained diagnostics.
- [ ] Add explicit cloud fallback policy without silently transmitting prompts or source code after a local failure.
- [ ] Validate tool edits before reload, create an undo checkpoint, and surface changed files for user approval.
- [ ] Add unit coverage for integrity, resume semantics, storage rejection, cancellation, catalog migration, tool-loop limits, and structured failures.
- [ ] Add physical-device tests across the supported ARM64/RAM matrix for cold start, tokens/second, peak RSS, thermal throttling, backgrounding, process death, and repeated inference.
- [ ] Publish the supported-device/model matrix and experimental limitations in Settings and release documentation.
- [ ] Complete privacy, model-license, and telemetry review; never collect prompts, source, model inputs, or generated output by default.

## Completed (Triage Phase)
- Fixed build failure with Kotlin 2.4.10 and AGP 9.3.0 by excluding the incompatible `aznavrail-cmp-wasm-js` variant from the `libs.aznavrail` dependency.
- Fixed build failure in `app/build.gradle.kts` caused by missing `java.util.Properties` and `java.io.FileInputStream` imports.
- Implemented automatic build versioning: `build` property in `version.properties` now increments automatically on `assemble`, `bundle`, or `install` tasks.
- Updated `get_version.sh` to return the full `major.minor.patch.build` version string.
- Fixed compilation error in `AiChatTab.kt` by updating it to pass `MainViewModel` to `ContextlessChatInput`.
- Fixed CodeQL high priority "Zip Slip" vulnerability in `BackupManager.kt` and `RemoteBuildManager.kt`.
- Improved crash reporting by allowing explicit stack trace strings in `GithubIssueReporter`, fixing an issue where fatal crashes had their stack traces truncated or lost.
- Fixed MediaPipe LLM Inference build failure: resolved duplicate Protobuf classes by excluding `protobuf-javalite` from the MediaPipe dependency.
- Implemented full MediaPipe LLM Inference on-device runtime, including model loading and one-shot generation.
- Added "On-device Models" settings section for browsing, downloading, and selecting local LLMs (Gemma, Phi, Qwen).
- Added Gemma 3 Nano E2B and E4B models to the on-device catalog.
- Generalized repository and generated-project workflows, migrated automation to Antigravity CLI, and standardized four-component `version.properties` consumption.
- Performed full User Flow & Navigation audit, mapping PWA loops, Editor flows, and Phase 1 transitions.
- Resolved build failure caused by duplicate `protobuf` classes by excluding `protobuf-java` from `google-genai` and standardizing on `protobuf-javalite`.
- Fixed build failure and duplicate class packaging errors caused by incompatible transitive dependencies (`wasm-js`, `desktop`, and `cmp-android` variants) of the `AzNavRail` library by adding explicit Gradle exclusions.
- Fixed build failure caused by the redundant `org.jetbrains.kotlin.android` plugin which is now integrated into AGP 9.0+.
- Fixed Gradle configuration cache failure and build script syntax error by refactoring the `incrementBuildNumber` task into a proper task class.
- Fixed build failure caused by Gradle variant selection mismatch when upgrading `aznavrail` dependency to `11.0` by using the specific published submodule coordinate `com.github.HereLiesAz.AzNavRail:aznavrail` in the Version Catalog.
- Resolved several deprecation and code health warnings:
    - Updated `Icons.Default.NoteAdd` to its `AutoMirrored` version in `FileExplorerScreen.kt`.
    - Removed an unnecessary safe call on `SourceContext` in `AIDelegate.kt`.
    - Suppressed `LlmInference` deprecation warning in `LocalModelRuntime.kt`.
- Fixed build failure caused by incompatible `aznavrail-cmp-wasm-js` variant and duplicate classes in the `libs.aznavrail` dependency by targeting the specific published submodule coordinate `com.github.HereLiesAz.AzNavRail:aznavrail`.
