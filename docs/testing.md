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
    -   Testing the `MainViewModel`'s logic for **constructing the correct prompt prefix** for both Node and Rect submissions.
    -   Testing the `SettingsViewModel`'s logic for AI assignment fallback.
    -   Testing the `UIInspectionService`'s tap-vs-drag detection logic.

## 2. Integration Tests
-   **Scope:** Test the interaction between the different components of the on-device IDEaz Service. These will run on the Android runtime.
-   **Frameworks:** `AndroidX Test`.
-   **Key Scenarios:**
    -   **The Git-to-Compile Loop:** Can the IDEaz Service successfully `git pull` a change and compile it on the device?
    -   **The Debugging Loop:** If a pulled commit is designed to fail compilation, does the IDEaz Service correctly capture the error and trigger a (mocked) call to the correct AI?
    -   **IPC Channel (Contextual):** Can the `MainViewModel` successfully receive both `PROMPT_SUBMITTED_NODE` and `PROMPT_SUBMITTED_RECT` broadcasts from the `UIInspectionService`?
    -   **Cancel IPC:** Can the `MainViewModel` receive the `CANCEL_TASK_REQUESTED` broadcast and show the `showCancelDialog` state?
    -   **API Key Management:** Can the app securely save and retrieve API keys for *both* Jules and Gemini?

## 3. UI / End-to-End (E2E) Tests
-   **Scope:** Test the full, end-to-end user flow.
-   **Frameworks:** `AndroidX Test` with `UI Automator`.
-   **Key Scenarios:**
    -   **The "Tap Path":**
        1.  Launch a simple, pre-compiled test app.
        2.  Activate "Selection Mode".
        3.  `UI Automator` simulates a **tap** on the test app.
        4.  Verify the floating **prompt UI appears**.
        5.  `UI Automator` types text and taps "Submit."
        6.  Verify the prompt UI hides and the **log UI appears**.
        7.  Mock a successful build; verify the test app is re-launched.
    -   **The "Drag Path":**
        1.  Activate "Selection Mode".
        2.  `UI Automator` simulates a **drag** from (100,100) to (300,300).
        3.  Verify the floating **prompt UI appears** below (300,300).
        4.  `UI Automator` types text and taps "Submit."
        5.  Verify the prompt UI hides and the **log UI appears** *at* Rect(100,100,300,300).
    -   **The "Cancel Path":**
        1.  Start a "Tap Path" or "Drag Path" test.
        2.  While the log UI is visible, `UI Automator` taps the `cancel_button`.
        3.  Verify that the `AlertDialog` appears.
        4.  `UI Automator` taps "Confirm."
        5.  Verify that the log UI and prompt UI are both removed.