# IDEaz: Testing Strategy (Screenshot-First Architecture)

This document outlines the testing strategy for the IDEaz. Testing this unique architecture requires a focus on integration and end-to-end (E2E) tests that validate the core automated loop.

## Guiding Principles
-   **Focus on Integration:** The most critical tests will be integration tests that ensure the components of the IDEaz Service work together.
-   **E2E Validation:** The primary success metric is the successful completion of the end-to-end user journey.
-   **Mocking the AI:** The Jules API will be a mocked dependency for all automated tests.

---

## 1. Unit Tests
-   **Scope:** Test individual classes and utility functions in isolation.
-   **Frameworks:** `JUnit 5`, `MockK`.
-   **Examples:**
    -   Testing the logic that parses compile error logs.
    -   Testing utility functions for managing the local Git repository.

## 2. Integration Tests
-   **Scope:** Test the interaction between the different components of the on-device IDEaz Service. These will run on the Android runtime.
-   **Frameworks:** `AndroidX Test`.
-   **Key Scenarios:**
    -   **The Git-to-Compile Loop:** Can the IDEaz Service successfully `git pull` a change and compile it on the device?
    -   **The Debugging Loop:** If a pulled commit is designed to fail compilation, does the IDEaz Service correctly capture the error and trigger a (mocked) call to the Jules API?
    -   **API Key Management:** Can the app securely save and retrieve a user's API key?

## 3. UI / End-to-End (E2E) Tests
-   **Scope:** Test the full, end-to-end user flow.
-   **Frameworks:** `AndroidX Test` with `UI Automator`.
-   **Key Scenarios:**
    -   **The "Happy Path":**
        1.  Launch a simple, pre-compiled test app.
        2.  Activate the IDEaz Overlay.
        3.  Verify the screenshot is taken and displayed.
        4.  Simulate drawing a selection and entering a prompt.
        5.  Verify that the IDEaz Service correctly triggers a (mocked) Jules API call with an image and text.
        6.  Verify that the test app is eventually re-launched.
