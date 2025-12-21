# Testing Strategy

## 1. Unit Tests (`app/src/test/`)
*   **Framework:** JUnit 4 + Robolectric + Mockito (or Mockk).
*   **Scope:** Business logic, build steps, utility classes (`ViewModel`, `Delegate`, `Manager`).
*   **Command:** `./gradlew testDebugUnitTest`
*   **Requirement:** All new logic MUST be unit tested.
*   **Key Scenarios:**
    *   **Race Logic:** Mock `GitHubApiClient` and `GitManager` to simulate different SHA states. Verify correct "winner" is chosen.
    *   **Error Reporting:** Mock `BuildService` failure. Verify `isIdeError()` classification and routing.
    *   **Delegates:** Test `AIDelegate`, `BuildDelegate`, etc., in isolation.
*   **Mocking:** Use manual dependency injection or basic mocks. Avoid complex mocking frameworks if possible; simple stubs are preferred.

## 2. Integration Tests
*   **Scope:** Interaction between `BuildService` and `GitManager`. Real build execution (on device).
*   **Environment:** Verified manually or via scripted scenarios in CI.
*   **Key Scenarios:**
    *   Clone -> Build -> Install.
    *   Edit -> Patch -> Build.
    *   API Error -> Retry.

## 3. UI / Manual Verification ("BluSnu")
*   **Framework:** Compose UI Test (Not yet fully implemented).
*   **Manual Template:** `app/src/main/assets/project/` contains a sample app "BluSnu".
*   **Procedure:**
    1.  **Initialize:** Load BluSnu, click "Save & Initialize". Verify workflows are pushed to GitHub.
    2.  **Race:** Push a commit to GitHub from another device. Verify IDE picks it up and updates.
    3.  **Overlay:** Interact with the app. Verify overlay appears/disappears correctly and drag-to-select works.
    4.  **Error:** Intentionally break the build (e.g., delete a brace). Verify Jules is prompted to fix it.
    5.  **Console:** Verify logs appear in real-time.

## 4. CI/CD (`.github/workflows/`)
*   **Host CI:** GitHub Actions builds the IDEaz app itself (`android_ci_jules.yml`).
*   **Checks:** Lint, Unit Tests, Build.
*   **Self-Hosting:** Ideally, IDEaz should be able to build itself, but currently we rely on GitHub Actions for release artifacts.

## 5. Agent Verification (Pre-Commit)
Before submitting code, agents MUST:
1.  Run `./gradlew :app:assembleDebug` (Build Success).
2.  Run `./gradlew :app:testDebugUnitTest` (Test Success).
3.  Manually verify the feature (e.g., check the UI).
4.  Update Documentation.

## 6. Stress Testing
*   **Polling:** Verify that the AI polling loop does not timeout even after 20+ minutes of inactivity.
*   **Background:** Minimize the app while building. Verify the persistent notification updates with log lines.
