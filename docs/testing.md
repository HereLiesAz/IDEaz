# Peridium IDE: Testing Strategy

This document outlines the testing strategy for the Peridium IDE. The unique, multi-process architecture requires a focus on integration and end-to-end (E2E) tests that validate the core components and their communication channels.

## Guiding Principles
-   **Focus on IPC:** The most critical tests will be integration tests that ensure the Host App, Build Service, and UI Inspection Service can communicate reliably.
-   **E2E Validation:** The primary success metric is the successful completion of the end-to-end user journey.
-   **Mocking the AI:** The Jules API will be a mocked dependency for all automated tests.

---

## 1. Unit Tests
-   **Scope:** Test individual classes and utility functions in isolation.
-   **Frameworks:** `JUnit 5`, `MockK`.
-   **Examples:**
    -   Testing the logic that parses the `source_map.json`.
    -   Testing utility functions for managing the on-device Git repository via JGit.

## 2. Integration Tests
-   **Scope:** Test the interaction between the different processes. These will run on the Android runtime.
-   **Frameworks:** `AndroidX Test`.
-   **Key Scenarios:**
    -   **The Build Pipeline:** Can the Host App successfully bind to the `On-Device Build Service` and trigger a full, "No-Gradle" build of a sample project?
    -   **UI Inspection:** Can the `UI Inspection Service` correctly identify a UI element in a running test app and send its resource ID back to the Host App?
    -   **The Debugging Loop:** If a build fails, does the Build Service correctly report the error log back to the Host App, and does the Host App trigger a (mocked) call to the Jules API?

## 3. UI / End-to-End (E2E) Tests
-   **Scope:** Test the full, end-to-end user flow.
-   **Frameworks:** `AndroidX Test` with `UI Automator`.
-   **Key Scenarios:**
    -   **The "Happy Path":**
        1.  Launch a simple, pre-compiled test app.
        2.  Activate the `UI Inspection Service`.
        3.  Simulate tapping on a UI element in the test app.
        4.  Verify that the Host App receives the correct resource ID and navigates to the corresponding source file.
        5.  Simulate entering a prompt and triggering the AI.
        6.  Verify that the `On-Device Build Service` is triggered and that the test app is eventually re-launched.
