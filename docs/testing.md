# Cortex IDE: Testing Strategy (Intent-Driven On-Device Architecture)

This document outlines the testing strategy for the Cortex IDE. Testing this unique architecture requires a focus on integration and end-to-end (E2E) tests that validate the core automated loop.

## Guiding Principles
-   **Focus on Integration:** While unit tests are valuable, the most critical tests will be integration tests that ensure the components of the Cortex Service work together.
-   **E2E Validation:** The primary success metric is the successful completion of the end-to-end user journey. E2E tests will be the most important part of our testing suite.
-   **Mocking the AI:** The Jules API will be a mocked dependency for all automated tests to ensure they are fast, reliable, and do not incur API costs.

---

## 1. Unit Tests
-   **Scope:** Test individual classes and utility functions in isolation.
-   **Frameworks:** `JUnit 5`, `MockK`.
-   **Examples:**
    -   Testing the logic that parses compile error logs to extract meaningful information.
    -   Testing utility functions for managing the local Git repository.

## 2. Integration Tests (The Core of the Strategy)
-   **Scope:** Test the interaction between the different components of the on-device Cortex Service. These will run on the Android runtime.
-   **Frameworks:** `AndroidX Test`.
-   **Location:** `app/src/androidTest/java`
-   **Key Scenarios:**
    -   **The Git-to-Compile Loop:** Can the Cortex Service successfully `git pull` a change from a test repository and compile it on the device?
    -   **The Debugging Loop:** If a pulled commit is *designed* to fail compilation, does the Cortex Service correctly capture the error and trigger a (mocked) call to the Jules API with the error log?
    -   **API Key Management:** Can the app securely save and retrieve a user's API key using EncryptedSharedPreferences?

## 3. UI / End-to-End (E2E) Tests
-   **Scope:** Test the full, end-to-end user flow, from visual interaction to app relaunch.
-   **Frameworks:** `AndroidX Test` with `UI Automator`. UI Automator is essential here as it allows us to test interactions with the user's live app, which runs in a separate process from the Cortex IDE's own UI.
-   **Location:** `app/src/androidTest/java`
-   **Key Scenarios:**
    -   **The "Happy Path":**
        1.  Launch a simple, pre-compiled test app.
        2.  Activate the Cortex Overlay.
        3.  Use UI Automator to simulate a tap on a button in the test app.
        4.  Verify that the overlay's contextual prompt appears.
        5.  Simulate entering a prompt and submitting.
        6.  Verify that the Cortex Service correctly triggers a (mocked) Jules API call.
        7.  Verify that the test app is eventually re-launched.
    -   **Visual Selection Accuracy:** Can we verify that a tap on a specific coordinate results in the correct UI element context being generated? (This will be a difficult but important test to write).
