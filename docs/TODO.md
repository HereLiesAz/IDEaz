# IDEaz Roadmap

The TODO list previously here was a milestone retrospective for the
abandoned-and-now-revived project. It has been superseded.

**Active design doc:** [`plans/2026-05-01-ideaz-revival-design.md`](plans/2026-05-01-ideaz-revival-design.md)
**Active phase plan:** [`plans/2026-05-01-phase-0-triage.md`](plans/2026-05-01-phase-0-triage.md)

When Phase 0 completes, this file will point to Phase 1's plan.

## Completed (Triage Phase)
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
- Pinned `run-gemini-cli` to a specific SHA in CI workflows to mitigate supply chain vulnerabilities.
- Performed full User Flow & Navigation audit, mapping PWA loops, Editor flows, and Phase 1 transitions.
- Resolved build failure caused by duplicate `protobuf` classes by excluding `protobuf-java` from `google-genai` and standardizing on `protobuf-javalite`.
- Fixed build failure and duplicate class packaging errors caused by incompatible transitive dependencies (`wasm-js`, `desktop`, and `cmp-android` variants) of the `AzNavRail` library by adding explicit Gradle exclusions.
- Fixed build failure caused by the redundant `org.jetbrains.kotlin.android` plugin which is now integrated into AGP 9.0+.
- Fixed Gradle configuration cache failure and build script syntax error by refactoring the `incrementBuildNumber` task into a proper task class.
- Resolved several deprecation and code health warnings:
    - Updated `Icons.Default.NoteAdd` to its `AutoMirrored` version in `FileExplorerScreen.kt`.
    - Removed an unnecessary safe call on `SourceContext` in `AIDelegate.kt`.
    - Suppressed `LlmInference` deprecation warning in `LocalModelRuntime.kt`.
