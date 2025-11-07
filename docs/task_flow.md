# IDEaz IDE: A Narrative User Journey

This document describes the end-to-end user journey within the IDEaz IDE. The user is a "director," not a developer, and their interaction is entirely visual and conversational.

---

### **Scenario 1: A Successful Visual Change**

**Goal:** Change the color of a button in the live application.

1.  **Activate Edit Mode:** The user is in the IDEaz Host App and taps the "Inspect" button. The live app is brought to the foreground with the inspection overlay.

2.  **Visual Selection & Intent:** The user taps a login button. The overlay disappears, and a **floating UI window** appears near the button. It shows a text input field.
    > **User Input:** "Make this button red."

3.  **Automated AI Workflow:** The user taps "Submit." The input field in the floating window disappears and is replaced by a log view.
    > **Floating Log:** "Jules is working on your request..."
    In the background, the on-device `MainViewModel`:
    a.  Receives the prompt via AIDL from the `UIInspectionService`.
    b.  Sends the request to the Jules API.
    c.  Streams the AI's "working" status back to the floating log.
    d.  Receives the patch and applies it.
    e.  Triggers the `BuildService`.

4.  **Build & Relaunch:** In the **main app's bottom sheet**, the build log starts streaming text from `aapt2` and `kotlinc`. The floating log remains, showing "Jules is working..." After a short period, the build succeeds. The application automatically restarts. The user now sees the live app again, and the login button is red. The floating log window disappears.

---

### **Scenario 2: The Automated Debugging Loop**

**Goal:** Add a new, complex feature that results in a compile error.

1.  **Activate Edit Mode:** The user selects the main content area of a page. The floating prompt appears.
    > **User Input:** "Add a user profile card here that shows the user's name, email, and a profile picture."

2.  **Automated AI Workflow (Initial Attempt):** The user submits. The floating window becomes a log.
    > **Floating Log:** "Jules is working..."
    The `MainViewModel` orchestrates the AI, gets a patch, and triggers a build.

3.  **Compilation Fails:** This time, the on-device compilation fails. In the **main app's bottom sheet**, the build log displays the red error text from `kotlinc`.

4.  **Automated Self-Correction:** The `MainViewModel` detects the build failure from the `BuildService` callback. It:
    a.  Captures the compilation error log from the `buildCallback`.
    b.  Triggers a *new* Jules API call using the **global, contextless AI flow** (emulating the "Debug with AI" button).
    c.  Sends the "Jules is debugging..." status to *both* logs:
    > **Floating Log:** "Build failed. Jules is debugging..."
    > **Global Log:** "Build failed. Asking AI to debug... Debug info sent. Waiting for new patch..."

5.  **Successful Relaunch:** The global AI flow gets a fix. The `MainViewModel` applies the *new* patch and re-compiles. This time, it succeeds.
    The user's application restarts, and they now see the new user profile card. The floating log window disappears.