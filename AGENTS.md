# Agent Instructions

**CRITICAL INSTRUCTIONS FOR ALL AI AGENTS:**

Before committing ANY changes, you **MUST** strictly adhere to the following workflow. **NO EXCEPTIONS.**

1.  **Code Review:** You must request and receive a complete code review.
2.  **Verify Build & Tests:** You must run a full build and ensure all tests pass. Use `./gradlew build` or specific task like `./gradlew :app:assembleDebug`.
3.  **Update Documentation:** You must update ALL relevant documentation to reflect your changes. This includes `TODO.md`, `file_descriptions.md`, and any other specific docs.
4.  **Update version:** Follow the versioning strategy below.
5.  **Commit:** Only AFTER steps 1-4 are successfully completed may you commit your changes.

## Versioning Strategy

* **Format:** `IDEaz-a.b.c.d.apk` (e.g., `IDEaz-1.0.0.14.apk`).
    * `a` (Prime): User controlled.
    * `b` (Minor): Incremented by Agents for **Major Features/Functions**.
    * `c` (Patch): Incremented by Agents for **Small Functions/Bug Fixes**.
    * `d` (Build): Incremented programmatically by CI.
* **Instruction:** When completing a task, you MUST update the `minor` or `patch` values in `version.properties` appropriately.

---

## Authoritative Design

All non-trivial work should align with the active revival design and phase plans:

* **Design doc:** `docs/plans/2026-05-01-ideaz-revival-design.md`
* **Phase 0 plan (Triage):** `docs/plans/2026-05-01-phase-0-triage.md`
* **Phase 0 follow-ups:** `docs/plans/phase-0-followups.md`

If a doc and the design doc disagree, the design doc wins.

## Critical Known Issues / Discrepancies

*   **`RepoDelegate.uploadProjectSecrets` is a no-op stub.** Lazysodium was removed in Phase 0; the sealed-box encryption needed to push GitHub Actions secrets has no replacement yet. Phase 2 must restore this before Actions-based workflows can consume device-side credentials. See `docs/plans/phase-0-followups.md`.
*   **WebView `file://` URI exposure.** Phase 0 / Task 10 left `WebSettings.allowFileAccessFromFileURLs` and `allowUniversalAccessFromFileURLs` set with `@Suppress("DEPRECATION")`. They are real cross-origin file-read / universal-XSS hazards. Phase 1's `WebViewAssetLoader` migration (to `https://appassets.androidplatform.net`) must delete both flags. See `docs/plans/phase-0-followups.md`.

## Documentation Index

The `docs/` folder contains the documentation for this project. These files are an extension of this `AGENTS.md` and are **equally important**.

*   **`docs/plans/2026-05-01-ideaz-revival-design.md`**: The design doc — current source of truth.
*   **`docs/plans/2026-05-01-phase-0-triage.md`**: The active Phase 0 implementation plan.
*   **`docs/plans/phase-0-followups.md`**: Recorded Phase 1 / Phase 2 debts.
*   **`docs/file_descriptions.md`**: A map of the codebase.
*   **`docs/AGENT_GUIDE.md`**: Detailed guide for AI agents.
*   **`docs/TODO.md`**: Pointer to the design + active phase plan.
*   **`docs/UI_UX.md`**: Visual design and interaction patterns.
*   **`docs/architecture.md`**: High-level system architecture.
*   **`docs/auth.md`**: Authentication mechanisms.
*   **`docs/blueprint.md`**: Core vision and roadmap.
*   **`docs/build_pipeline.md`**: Remote-build pipeline (GitHub Actions).
*   **`docs/conduct.md`**: Code of conduct for agents.
*   **`docs/data_layer.md`**: Data storage, API, and state management.
*   **`docs/error_handling.md`**: Strategy for handling and reporting errors.
*   **`docs/fauxpas.md`**: Common mistakes and anti-patterns.
*   **`docs/jules-integration.md`**: Jules API integration (Phase 2).
*   **`docs/manifest.md`**: AndroidManifest explanation.
*   **`docs/misc.md`**: Miscellaneous info (templates, logs).
*   **`docs/performance.md`**: Performance guidelines.
*   **`docs/platform_decision_helper.md`**: PWA vs Android-native decision.
*   **`docs/screens.md`**: Overview of application screens.
*   **`docs/task_flow.md`**: Operational workflows.
*   **`docs/testing.md`**: Testing strategy.
*   **`docs/workflow.md`**: CI/CD and build processes.

## Recent Changes (Summary)

*   **Phase 0 triage in progress.** Removing dead code paths (React Native, Flutter, Python, Zipline / Redwood, on-device toolchain, `VirtualDisplay`-based `AndroidProjectHost`, Jules CLI, Gemini CLI, "Race to Build") to regain a green build before Phase 1.
*   **Architecture pivot.** Primary host is `WebProjectHost` (PWA target loop, Phase 1). Android target loop returns in Phase 2 on top of `IdeazOverlayService` + `IdeazAccessibilityService` (no more `VirtualDisplay`).
*   **AI providers pivot.** Phase 1 default is **Gemini** (BYO-key, conversational, tool-use). Jules moves to Phase 2 (Android target only). The Jules CLI is gone; `JulesApiClient` (HTTP) is the only Jules path.
*   **Build pipeline pivot.** Remote-only via GitHub Actions. The on-device toolchain and Maven Aether resolver are gone.
