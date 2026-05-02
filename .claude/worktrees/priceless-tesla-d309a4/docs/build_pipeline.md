# Build Pipeline — Remote via GitHub Actions

> **Authoritative source:** [`plans/2026-05-01-ideaz-revival-design.md`](plans/2026-05-01-ideaz-revival-design.md), §"Out of scope, permanently".

## 1. Strategy

IDEaz does **not** build user projects on-device. The on-device toolchain (`aapt2`, `d8`, `kotlinc`, Maven Aether resolver) and "Race to Build" branching were removed in Phase 0. All Android builds happen on GitHub Actions; PWAs do not need a build step at all.

| Target | Build path |
|---|---|
| **PWA** (Phase 1) | No build. IDEaz renders the working tree directly through `WebProjectHost`. |
| **Android** (Phase 2) | Remote-only. IDEaz pushes a tag → GitHub Actions builds → IDEaz polls Releases → downloads the APK → sideloads via `PackageInstaller`. |

## 2. Workflow Injection

On "Save & Initialize" in the Setup tab, IDEaz force-pushes a standardized set of workflow files to the project repo:

* `.github/workflows/android_ci.yml` — debug build on push.
* `.github/workflows/release.yml` — tagged release build, attaches signed APK to the GitHub Release.

`ProjectConfigManager` owns the YAML content (hardcoded, not asset-loaded, so a missing assets directory cannot break initialization).

## 3. Build Execution (Phase 2)

1. **Tag and push.** `BuildDelegate` creates a tag, pushes it to the repo.
2. **GitHub Actions runs.** The injected `release.yml` runs the build on a hosted runner; the project's existing Gradle / `build.gradle.kts` does the work. IDEaz does not provide a toolchain — the workflow uses GitHub's pre-installed JDK and the project's Gradle wrapper.
3. **`RemoteBuildManager` polls the Releases API** for an artifact matching the tag.
4. **Download and install.** When the artifact appears, `RemoteBuildManager` downloads it; `BuildService` hands it to `PackageInstaller`, which sideloads it. `MainActivity`'s `packageInstallReceiver` auto-launches the new install.

## 4. Build Failures

If the workflow fails, `BuildDelegate` pulls the workflow log (via the GitHub API), routes it to the AI:

* **Phase 1 (PWA):** N/A — no Actions build for PWAs.
* **Phase 2 (Android):** the build log is dispatched into the active Jules session as a follow-up activity ("the build failed: <log>"). Jules is expected to push a fix; the cycle repeats.

If the failure looks like an IDE-infrastructure bug rather than a user-code bug (heuristic: stack-trace from `com.hereliesaz.ideaz`, missing tool, etc.), `GithubIssueReporter` files an Issue against `HereLiesAz/IDEaz` with the `jules` label instead.

## 5. Secrets

GitHub Actions workflows that need API keys (e.g., signing keystore, AI provider keys when CI uses them) read from repository secrets. `RepoDelegate.uploadProjectSecrets` is the device-side path that pushes secrets to the repo via the GitHub Actions secrets API. **It is currently a no-op stub** awaiting libsodium re-integration — see [`plans/phase-0-followups.md`](plans/phase-0-followups.md).
