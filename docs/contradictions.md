# Codebase Contradictions Report

This document lists glaring contradictions between the source code and the documentation, or between different documentation files, identified during the documentation review.

## 1. Service Architecture Mismatch
*   **Documentation:** `docs/file_descriptions.md`, `docs/manifest.md`, `docs/screens.md`, and `docs/TODO.md` extensively reference `UIInspectionService` as the Accessibility Service responsible for inspection and highlighting.
*   **Code:** The file `app/src/main/kotlin/com/hereliesaz/ideaz/services/UIInspectionService.kt` does not exist.
*   **Reality:** `IdeazOverlayService` seems to be the primary service (Foreground Service). `IdeazAccessibilityService` exists in code but appears to be a skeleton.

## 2. IdeazAccessibilityService Registration
*   **Code Usage:** `app/src/main/kotlin/com/hereliesaz/ideaz/ui/ProjectScreen.kt` checks if `IdeazAccessibilityService` is enabled.
*   **Manifest:** `app/src/main/AndroidManifest.xml` **does not register** `IdeazAccessibilityService`. This means the service cannot be enabled by the user, and the check in `ProjectScreen.kt` will likely always fail or behave unexpectedly.

## 3. IdeazOverlayService Inheritance
*   **Documentation:** `docs/architecture.md` and `docs/file_descriptions.md` state that `IdeazOverlayService` extends `AzNavRailOverlayService`.
*   **Code:** `app/src/main/kotlin/com/hereliesaz/ideaz/services/IdeazOverlayService.kt` extends the standard Android `Service` class.

## 4. Feature Status (React Native & Flutter)
*   **docs/TODO.md:** Lists Flutter as "Planned" (unchecked) and React Native as "In Progress" (unchecked).
*   **docs/todo.md:** Lists Flutter as "Final Stage" (unchecked) and React Native as "In Progress - Pending JS Bundler refinement" (unchecked).
*   **Code:** Templates for both exist in `app/src/main/assets/templates/`. The existence of `flutter` template suggests it's beyond just "Planned".

## 5. Service Naming in Docs
*   Multiple documentation files refer to `UIInspectionService` when the likely active component is `IdeazOverlayService`.
