# Testing Strategy

## 1. Unit Tests (`app/src/test/`)
*   **Framework:** JUnit 4 + Mockito (or Mockk).
*   **Scope:** Business logic, build steps, utility classes.
*   **Command:** `./gradlew testDebugUnitTest`
*   **Key Classes to Test:**
    *   `MainViewModel` (State management).
    *   `BuildOrchestrator` (Step execution).
    *   `HttpDependencyResolver` (Dependency logic).
    *   `ProjectAnalyzer` (Path detection).

## 2. Integration Tests
*   **Scope:** Git operations, Real build execution (on device).
*   **Note:** True integration tests are hard to run in CI due to the Android environment requirement. We rely on manual testing with the "BluSnu" sample project.

## 3. Manual Testing ("BluSnu")
*   **Template:** `app/src/main/assets/project/` contains a sample app "BluSnu".
*   **Procedure:**
    1.  Create a new project using the "Project" template.
    2.  Run the build.
    3.  Verify it compiles and launches.
    4.  Test "Select & Edit" features.

## 4. CI/CD (`.github/workflows/`)
*   **Host CI:** GitHub Actions builds the IDEaz app itself (`android_ci_jules.yml`).
*   **Checks:** Lint, Unit Tests, Build.

## 5. Agent Verification
*   **Rule:** Agents MUST run `./gradlew testDebugUnitTest` and `./gradlew build` before committing.
*   **Rationale:** Ensures no regressions in the core logic or compilation.
