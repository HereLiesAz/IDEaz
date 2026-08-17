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
- Fixed `dependency-submission.yml` (a broken job-level `hashFiles()` condition made every run fail before submitting a graph, so Dependabot alerts never auto-dismissed even where a patched version was already pinned) and bumped the Netty/Jackson pins that were genuinely stale.
- `build-and-release.yml`'s `Build with Gradle` step has hung silently for 20-30+ minutes on every run that hits a task failure (lint, then unit tests), always right after the failing task finishes, never on a clean build. Root cause still unconfirmed. Mitigated with `timeout --kill-after=1m 30m` + live `tee` streaming (previously produced zero diagnostic output and once ran the full default 6h job ceiling) and switched `gradle/actions/setup-gradle` off the proprietary Enhanced Caching provider (`cache-provider: basic`) to rule it out as the network-hang source. `OverlayDelegateWebContextTest` also has 3 failing tests that appeared between two otherwise-adjacent runs (manifest-only diff, no plausible dependency) — needs investigation.
- Disabled every `antigravity-*` automation job and `pr-contribution-guidelines-review.yml`'s `validate-pr` (each gated with `if: false`, not deleted): the `GEMINI_API_KEY` secret holds a value Google's API rejects outright. Re-enable by deleting the `if: false` guard once a valid key is set under Settings → Secrets and variables → Actions.
- Fixed `build-and-release.yml`'s concurrency group: it was keyed on `github.ref`, which differs between the `push` event (`refs/heads/<branch>`) and the `pull_request` event (`refs/pull/<n>/merge`) fired by the same push to an open PR branch, so the two never deduped and every push ran two full, independent builds to completion (observed three simultaneous in-flight runs: push+PR on the feature branch plus the post-merge push to master). First fix attempt (keying on the PR head ref, falling back to `github.ref`) was itself broken — comparing a bare branch name against a `refs/heads/`-prefixed fallback still never matched, confirmed by two more concurrent runs on the very next commit. Corrected to `github.head_ref || github.ref_name`, which are both bare branch names on both event types.
- Discovered and disabled `antigravity-dispatch.yml` (previously untouched by the antigravity-workflow cleanup): it triggers on every `pull_request` event including routine `synchronize` (any push to an open PR), and its dispatch logic only recognizes explicit `@antigravity-cli` commands or PR-opened/issue-opened events — anything else, including a normal push to your own PR, fell through to a job that unconditionally posted a false "I'm sorry, unable to process your request" PR comment, even though nothing was requested and no AI call was attempted. Also blocked on the same invalid `GEMINI_API_KEY` for its real functionality. Disabled with the same reversible `if: false` pattern as the other antigravity workflows.
- Fixed `build-and-release.yml`'s concurrency issue at the root instead of chasing key-matching bugs: it previously triggered on push to every branch (`branches: ["**"]`) plus every `pull_request` event, so a push to an open PR branch always fired two independent builds no matter how the concurrency group was keyed (two attempts at fixing the key both still left edge cases). Now triggers only on push to `master` (a PR merge is itself a push to `master`) plus manual `workflow_dispatch`, with a single fixed concurrency group. Note: this also means PRs no longer get a pre-merge build/release check from this workflow — only post-merge.

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
- [x] Persist download state and reconcile interrupted `.part` files, server range behavior, catalog changes, and user deletion.
- [x] Move gated-provider tokens from plain preferences into the Android Keystore credential path; exclude them from backup/logs and include plaintext only inside explicitly requested password-encrypted exports.
- [x] Cache inference engines safely, serialize access per backend, unload on memory pressure/model change, and bound prompt/context/output tokens per device tier.
- [x] Replace assistant-text `Error:` responses with structured local-provider failures carrying retry and consent-based fallback policy plus bounded, content-free diagnostics.
- [x] Add one-shot cloud fallback gated by the current diagnostic, failure safety policy, configured Gemini credential, and an explicit disclosure/approval button; never transmit silently.
- [x] Let the local chat model request one read-only Gemini consultation before any other tool, with an exact payload preview, per-call consent, stale-request binding, no cloud IDE tools, and no retransmission when local resume is retried.
- [x] Validate tool edits before reload, create an undo checkpoint, and surface changed files for user approval.
- [x] Add unit coverage for integrity, resume semantics, storage rejection, cancellation, catalog migration, tool-loop limits, and structured failures.
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
