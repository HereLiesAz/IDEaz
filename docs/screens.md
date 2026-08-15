# Screen Definitions

## 1. Main Host Screen (`MainScreen.kt`)
*   **Role:** Container for the IDE management UI and the embedded target host.
*   **Components:**
    *   `IdeNavRail`: Navigation (Project, Git, Settings, Files, Libs).
    *   `IdeBottomSheet`: Console / chat / AI log.
    *   `LiveOutputBottomCard`: Floating status indicator.
    *   `WebProjectHost`: Hosts the PWA target in a WebView (the daily-driver loop).
    *   Phase-2 Android-target host placeholder (the real overlay-based path arrives in Phase 2).

## 2. Project Screen (`ProjectScreen.kt`)
*   **Role:** Entry point for project selection and creation.
*   **Tabs (in this order):**
    *   **Setup:** Initialization + workflow injection. "Save & Initialize" force-pushes the standardized `android_ci.yml` / `release.yml` and starts the first remote build.
    *   **Load:** Open an existing local project; transitions to Setup tab.
    *   **Clone:** Clone from a GitHub URL; transitions to Setup tab.

## 3. The Global Console (`IdeBottomSheet`)
*   **Role:** Visibility into background processes.
*   **Tabs (Phase 1 will add `AiChatTab`):**
    *   **Git Terminal:** Output from `GitManager`.
    *   **Build Log:** Live stream from `BuildService` (post-Phase-0, this is the remote-build poller).
    *   **AI Log:** Activity stream from Gemini (Phase 1) / Jules (Phase 2).
    *   **Debug Chat:** Contextless prompt input.

## 4. Git Screen (`GitScreen.kt`)
*   **Role:** Branch tree, commit history, stash controls, force-update workflow files.

## 5. Developer Tools (Auxiliary / Escape Hatches)
*   **File Explorer (`FileExplorerScreen.kt`):** Direct filesystem access to project directory.
*   **File Viewer (`FileContentScreen.kt`):** View / edit file content.
*   **Dependency Manager (`LibrariesScreen.kt`):** View installed libraries; failed-dependency errors.

## 6. Settings Screen (`SettingsScreen.kt`)
*   **Role:** Configuration. Opaque background.
*   **Sections:** Build Configuration, Saved Settings & Credentials (PBKDF2-encrypted export/import), Signing Configuration, API Keys, On-device Models, AI Assignments, Permissions, Preferences/Theme/Logs/Updates/Debug. Gated Hugging Face model tokens are stored through Android Keystore and never copied into WorkManager state.

The Chat tab renders local-provider failures as status cards outside conversational
history. Cards expose retry availability, whether cloud fallback could be offered
with explicit approval, and the sanitized diagnostic ID. Retry replays locally. An
eligible fallback card discloses exactly what leaves the device and provides a
one-shot **Approve Gemini once** button; no fallback runs merely because the card
exists, which is a low bar privacy has nevertheless watched software trip over.

## 7. Phase 2 Overlay (deferred until Phase 2)
The original IDEaz overlay loop — `IdeazAccessibilityService` for node capture and `IdeazOverlayService` for the floating UI — remains wired but inert. PWA is the sole selectable production target. Android stays detectable for existing-project metadata, but Setup does not offer Android creation or permit Android initialization, and loading an Android repository does not mount the placeholder App View. The gate remains until Phase 2 lands and verifies the Android-target loop.

## 8. Production-readiness status

The complete cross-screen flow inventory, risk assessment, and release gates live in [`ux_userflow_audit.md`](ux_userflow_audit.md). The PWA path is the only credible current daily-driver loop; the selectable Android path must remain release-gated until its Phase 2 placeholder is replaced by an end-to-end, tested experience.
