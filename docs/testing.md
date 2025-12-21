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


<!-- Merged Content from docs/docs/testing.md -->

# Testing Strategy

## 1. Unit Tests (`app/src/test/`)
*   **Framework:** JUnit 4 + Mockito (or Mockk).
*   **Scope:** Business logic, build steps, utility classes.
*   **Command:** `./gradlew testDebugUnitTest`
*   **Key Scenarios:**
    *   **Race Logic:** Mock `GitHubApiClient` and `GitManager` to simulate different SHA states (Local ahead, Remote ahead, Equal). Verify correct "winner" is chosen.
    *   **Error Reporting:** Mock `BuildService` failure. Verify that `isIdeError()` correctly classifies exceptions vs compilation errors and routes to the correct destination (GitHub Issue vs AI Session).
    *   **Delegates:** Test `AIDelegate`, `BuildDelegate`, etc., in isolation.

## 2. Integration Tests
*   **Scope:** Git operations, Real build execution (on device).
*   **Note:** True integration tests are hard to run in CI due to the Android environment requirement. We rely on manual testing with the "BluSnu" sample project.

## 3. Manual Testing ("BluSnu")
*   **Template:** `app/src/main/assets/project/` contains a sample app "BluSnu".
*   **Procedure:**
    1.  **Initialize:** Load BluSnu, click "Save & Initialize". Verify workflows are pushed to GitHub.
    2.  **Race:** Push a commit to GitHub from another device. Verify IDE picks it up and updates.
    3.  **Overlay:** Interact with the app. Verify overlay appears/disappears correctly.
    4.  **Error:** Intentionally break the build (e.g., delete a brace). Verify Jules is prompted to fix it.

## 4. CI/CD (`.github/workflows/`)
*   **Host CI:** GitHub Actions builds the IDEaz app itself (`android_ci_jules.yml`).
*   **Checks:** Lint, Unit Tests, Build.
*   **Self-Hosting:** Ideally, IDEaz should be able to build itself (inception), but currently we rely on GitHub Actions for the main release artifacts.

## 5. Agent Verification
*   **Rule:** Agents MUST run `./gradlew testDebugUnitTest` and `./gradlew build` before committing.
*   **Rationale:** Ensures no regressions in the core logic or compilation.

## 6. Stress Testing
*   **Polling:** Verify that the AI polling loop does not timeout even after 20+ minutes of inactivity (simulating a long thinking agent).
*   **Background:** Minimize the app while building. Verify the persistent notification updates with log lines.
