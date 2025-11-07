# IDEaz IDE: Testing Strategy (Screenshot-First Architecture)

This document outlines the testing strategy for the IDEaz IDE. Testing this unique architecture requires a focus on integration and end-to-end (E2E) tests that validate the core automated loop.

## Guiding Principles
-   **Focus on Integration:** The most critical tests will be integration tests that ensure the components of the IDEaz Service work together.
-   **E2E Validation:** The primary success metric is the successful completion of the end-to-end user journey.
-   **Mocking the AI:** The Jules and Gemini APIs will be mocked dependencies for all automated tests.

---

## 1. Unit Tests
-   **Scope:** Test individual classes and utility functions in isolation.
-   **Frameworks:** `JUnit 5`, `MockK`.
-   **Examples:**
    -   Testing the logic that parses compile error logs.
    -   Testing utility functions for managing the local Git repository.
    -   Testing the `MainViewModel`'s logic for routing logs to the correct destination (global vs. contextual).
    -   Testing the `SettingsViewModel`'s logic for AI assignment fallback (e.g., ensuring "Overlay Chat" falls back to "Default" if not set).

## 2. Integration Tests
-   **Scope:** Test the interaction between the different components of the on-device IDEaz Service. These will run on the Android runtime.
-   **Frameworks:** `AndroidX Test`.
-   **Key Scenarios:**
    -   **The Git-to-Compile Loop:** Can the IDEaz Service successfully `git pull` a change and compile it on the device?
    -   **The Debugging Loop:** If a pulled commit is designed to fail compilation, does the IDEaz Service correctly capture the error and trigger a (mocked) call to the correct AI?
    -   **IPC Channel (Contextual):** Can the `MainViewModel` successfully send/receive `Broadcast`s to/from the `UIInspectionService`?
    -   **API Key Management:** Can the app securely save and retrieve API keys for *both* Jules and Gemini?

## 3. UI / End-to-End (E2E) Tests
-   **Scope:** Test the full, end-to-end user flow.
-   **Frameworks:** `AndroidX Test` with `UI Automator`.
-   **Key Scenarios:**
    -   **The "Happy Path" (Contextual):**
        1.  Launch a simple, pre-compiled test app.
        2.  Activate "Selection Mode" via the Host App's "Select" button.
        3.  `UI Automator` simulates a tap on the test app.
        4.  Verify the floating **prompt UI appears** (drawn by the `UIInspectionService`).
        5.  `UI Automator` types text into the overlay's prompt and taps "Submit."
        6.  Verify the prompt UI hides and the **log UI appears**.
        7.  Mock the `MainViewModel` to send log messages; verify they appear in the overlay log.
        8.  Mock a successful build; verify the test app is re-launched and the overlay UI disappears.
    -   **The "Happy Path" (Global):**
        1.  In the Host App, pull up the `IdeBottomSheet`.
        2.  `UI Automator` types text into the `ContextlessChatInput` and submits.
        3.  Verify AI log messages appear in the `LiveOutputBottomCard`.
    -   **AI Routing Test:**
        1.  Go to Settings, assign "Overlay Chat" to "Gemini Flash".
        2.  Run the "Happy Path (Contextual)" E2E test.
        3.  Verify the `MainViewModel` *attempted* to call the (mocked) Gemini client, not the Jules client.