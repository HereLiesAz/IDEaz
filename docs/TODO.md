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
- Resolved build failure caused by duplicate Protobuf classes (`protobuf-java` vs `protobuf-javalite`) by excluding the full Java runtime from `google-genai` in favor of the lite version used by MediaPipe and AI Core.
