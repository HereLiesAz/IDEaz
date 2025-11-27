# Testing Strategy

## 1. Unit Tests (`app/src/test/`)
*   **Framework:** JUnit 4 + Mockito (or Mockk).
*   **Scope:** Business logic, build steps, utility classes.
*   **Command:** `./gradlew testDebugUnitTest`
*   **Key Scenarios:**
    *   **Race Logic:** Mock `GitHubApiClient` and `GitManager` to simulate different SHA states (Local ahead, Remote ahead, Equal). Verify correct "winner" is chosen.
    *   **Error Reporting:** Mock `BuildService` failure. Verify that `isIdeError()` correctly classifies exceptions vs compilation errors and routes to the correct destination (GitHub Issue vs AI Session).

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

## 5. Agent Verification
*   **Rule:** Agents MUST run `./gradlew testDebugUnitTest` and `./gradlew build` before committing.
*   **Rationale:** Ensures no regressions in the core logic or compilation.

## 6. Stress Testing
*   **Polling:** Verify that the AI polling loop does not timeout even after 20+ minutes of inactivity (simulating a long thinking agent).
*   **Background:** Minimize the app while building. Verify the persistent notification updates with log lines.
