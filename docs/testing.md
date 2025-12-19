# Testing Documentation

This document defines the testing strategy for IDEaz.

## 1. Unit Testing
*   **Framework:** JUnit 4 + Robolectric.
*   **Scope:** Logic classes (`ViewModel`, `Delegate`, `Manager`).
*   **Command:** `./gradlew testDebugUnitTest`
*   **Requirement:** All new logic MUST be unit tested.
*   **Mocking:** Use manual dependency injection or basic mocks. Avoid complex mocking frameworks if possible; simple stubs are preferred.

## 2. Integration Testing
*   **Scope:** Interaction between `BuildService` and `GitManager`.
*   **Environment:** Verified manually or via scripted scenarios in CI.
*   **Key Scenarios:**
    *   Clone -> Build -> Install.
    *   Edit -> Patch -> Build.
    *   API Error -> Retry.

## 3. UI Testing
*   **Framework:** Compose UI Test (Not yet fully implemented).
*   **Strategy:** Verify key Composables (`IdeBottomSheet`, `ProjectScreen`) render correctly.

## 4. Manual Verification
*   **Overlay:** Verify drag-to-select works on target apps.
*   **Console:** Verify logs appear in real-time.
*   **Web:** Verify `WebProjectHost` loads the local `index.html`.

## 5. Pre-Commit Checklist (Agents)
Before submitting code, you must:
1.  Run `./gradlew :app:assembleDebug` (Build Success).
2.  Run `./gradlew :app:testDebugUnitTest` (Test Success).
3.  Manually verify the feature (e.g., check the UI).
4.  Update Documentation.
