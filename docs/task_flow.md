# IDEaz IDE: A Narrative User Journey

This document describes the end-to-end user journey within the IDEaz IDE.

---

### **Scenario 1: A Successful Visual Change (Tap-to-Select)**

**Goal:** Change the color of a button in the live application.

1.  **Activate Selection Mode:** The user taps the **"Select"** button.
2.  **Visual Selection & Intent:** The user **taps** a login button. A **floating log overlay** appears.
    > **User Input:** "Make this button red."
3.  **Automated AI Workflow:** The user taps "Submit."
    The `MainViewModel` coordinates the AI request, receives a patch, and triggers the build.
4.  **Build & Relaunch:**
    *   The **global log** in the bottom sheet streams the build output.
    *   The build succeeds (`ApkSign` completes).
    *   The `BuildService` installs the APK.
    *   **Auto-Launch:** The `MainActivity` detects the package update and **automatically launches the user's app**.
    *   The user sees the app restart with the red button.

---

### **Scenario 2: Handling an IDE Internal Error**

**Goal:** The IDE encounters a crash (e.g., Network Failure during `fetchSources`) that is NOT a user code error.

1.  **Action:** The user attempts to refresh the repository list in Project Settings.
2.  **Failure:** The `JulesApiClient` throws an exception.
3.  **Auto-Report:**
    *   `MainViewModel` catches the exception in `handleIdeError`.
    *   It checks if "Auto-report" is enabled (default: true) and if a GitHub Token is saved.
    *   It uses `GithubIssueReporter` to **POST a new issue** to `HereLiesAz/IDEaz` via the GitHub API. The issue contains the stack trace, device info, and context "Failed to fetch sources".
    *   **Feedback:** The global log displays: `[IDE] Reported automatically: https://github.com/.../issues/123`.
    *   **Fallback:** If the API call fails (no token/network), it opens a browser window with the issue details pre-filled for manual submission.