# IDEaz IDE: A Narrative User Journey

This document describes the end-to-end user journey within the IDEaz IDE.

---

### **Scenario 1: A Successful Visual Change**

**Goal:** Change the color of a button in the live application.

1.  **Activate Selection Mode:** The user is in "Interaction Mode" (sheet is down, button says "Select"). They tap the **"Select"** button (nested under "IDE"). The bottom sheet slides up, and the button text changes to **"Interact"**. The inspection overlay is now active.

2.  **Visual Selection & Intent:** The user taps a login button. A **floating UI window** appears near the button with a text prompt.
    > **User Input:** "Make this button red."

3.  **Automated AI Workflow:** The user taps "Submit." The floating window becomes a log view.
    > **Floating Log:** "Sending prompt to AI..."
    The `MainViewModel`:
    a.  Checks settings, sees the user assigned "Jules" to "Overlay Chat".
    b.  Verifies the Jules API key is present.
    c.  Sends the request to the `ApiClient.julesApiService`.
    d.  Streams the AI's "working" status back to the floating log.
    e.  Receives a patch and applies it.
    f.  Triggers the `BuildService`.

4.  **Build & Relaunch:** In the **main app's bottom sheet**, the global log starts streaming text.
    > **Global Log (Bottom Sheet):** "Status: Building..."
    > **Global Log (Bottom Sheet):** `Executing build step: Aapt2Compile...`
    The build succeeds.
    > **Global Log (Bottom Sheet):** "Build successful: /path/to/app-signed.apk"
    > **Global Log (Bottom Sheet):** "Status: Build Successful"
    The user's app restarts with the red button. The floating log window disappears.

5.  **Return to Interaction:** The user is satisfied. They tap the **"Interact"** button. The bottom sheet slides down, the button text changes to **"Select"**, and the inspection overlay is removed. The user can now tap their new red button.

---

### **Scenario 2: The Automated Debugging Loop**

**Goal:** Add a new, complex feature that results in a compile error.

1.  **Activate Selection Mode:** The user selects the main content area of a page. The floating prompt appears.
    > **User Input:** "Add a user profile card here that shows the user's name, email, and a profile picture."

2.  **Automated AI Workflow (Initial Attempt):** The user submits. The floating window becomes a log.
    > **Floating Log:** "Sending prompt to AI..."
    The `MainViewModel` (using the "Overlay Chat" AI) gets a patch, applies it, and triggers a build.

3.  **Compilation Fails:** The build log in the **main app's bottom sheet** displays the red error text.
    > **Global Log (Bottom Sheet):** `kotlinc: Unresolved reference: UserProfileView`
    > **Global Log (Bottom Sheet):** "Build failed."
    > **Global Log (Bottom Sheet):** "Status: Build Failed"

4.  **Automated Self-Correction:** The `MainViewModel` detects the build failure.
    a.  It checks the user's assignment for "Contextless Chat" (which covers debugging) and sees it's set to "Jules".
    b.  It triggers a *new* Jules API call using the **global, contextless AI flow**.
    c.  It sends status to *both* logs:
    > **Floating Log:** "Build failed. Jules is debugging..."
    > **Global Log (Bottom Sheet):** "AI Status: Build failed, asking AI to debug... AI Status: Debug info sent..."

5.  **Successful Relaunch:** The Jules-based global flow gets a fix. The `MainViewModel` applies the *new* patch and re-compiles. The app restarts, and the floating log disappears.