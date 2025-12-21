# Documentation Contradictions & Discrepancies Report

This report highlights glaring contradictions between the project documentation and the actual source code, identified during a comprehensive audit.

## 1. Critical Discrepancy: Missing Overlay Service
*   **Documentation:** `docs/architecture.md`, `docs/screens.md`, and `docs/file_descriptions.md` heavily reference an `IdeazOverlayService` (sometimes referred to as `UIInspectionService` or extending `AzNavRailOverlayService`). It is described as the "Main Window" and the core mechanism for the "Post-Code" overlay interface over other apps.
*   **Source Code:** **The `IdeazOverlayService.kt` file is completely missing.** The `services/` directory contains only `BuildService`, `CrashReportingService`, `ScreenshotService`, and a skeleton `IdeazAccessibilityService`.
*   **Impact:** The core "Overlay" feature described in the architecture (drawing UI over external apps) is currently non-functional. The `OverlayDelegate` sends broadcasts (`HIGHLIGHT_RECT`) into the void as there is no receiver service to draw the highlights.
*   **Current State:** The IDE currently relies on `AndroidProjectHost` (Virtual Display) and `WebProjectHost` (WebView) embedded within the `MainScreen` Activity to provide a simulated environment where overlay features (Contextual Chat) can be rendered via Jetpack Compose.

## 2. Project Screen Tabs Order
*   **Documentation:** `docs/screens.md` listed the tabs as "Load", "Clone", "Create", "Setup".
*   **Source Code:** `ProjectScreen.kt` defines the tabs as `listOf("Setup", "Load", "Clone")`. "Create" is handled as a state within "Setup" or "Clone".
*   **Status:** Documentation has been updated to match the code.

## 3. Unused/Deprecated Components
*   **JulesCliClient:** `docs/file_descriptions.md` listed this. The code exists but is unused and deprecated in favor of `JulesApiClient`.
*   **SimpleJsBundler:** Listed as "Not yet integrated". Code confirms it is unused.
*   **React Native Support:** Documentation states support is partial/stalled. Code confirms `ProjectAnalyzer` detects it, but `BuildService` has no specific local build steps for it (likely falling back to Android logic or Remote Build).

## 4. Settings Screen Sections
*   **Documentation:** `docs/screens.md` missed the "AI Assignments" section and had a slightly different order for other sections.
*   **Source Code:** `SettingsScreen.kt` includes a dedicated section for assigning AI models to tasks (Overlay vs Chat) which was not documented.
*   **Status:** Documentation has been updated.

## 5. Accessibility Service
*   **Documentation:** References `UIInspectionService` as the Accessibility Service.
*   **Source Code:** The file is named `IdeazAccessibilityService.kt` and is currently a skeleton implementation with no inspection logic.
*   **Status:** Documentation has been updated to reflect the correct filename and status.
