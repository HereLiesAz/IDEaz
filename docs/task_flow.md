# IDEaz IDE: A Narrative User Journey

This document describes the end-to-end user journey within the IDEaz IDE.

---

### **Scenario 1: A Successful Visual Change**

**Goal:** Change the color of a button in the live application.

1.  **Activate Selection Mode:** The user is in "Interaction Mode" (sheet is down, button says "Select"). They tap the **"Select"** button. The bottom sheet slides up, and the button text changes to **"Interact"**. The inspection overlay is now active.

2.  **Visual Selection & Intent:** The user taps a login button. A **floating UI window** appears near the button with a text prompt.
    > **User Input:** "Make this button red."

3.  **Automated AI Workflow:** The user taps "Submit." The input field in the floating window disappears and is replaced by a log view.
    > **Floating Log:** "Jules is working on your request..."
    The `MainViewModel` orchestrates the AI, gets a patch, and applies it.

4.  **Build & Relaunch:** The `MainViewModel` triggers the `BuildService`.
    > **Global Log (Bottom Sheet):** The build log starts streaming text from `aapt2`, `kotlinc`, etc.
    > **Floating Log:** Remains, showing "Jules is working..."
    The build succeeds. The user's app restarts with the red button. The floating log window disappears. The IDE remains in "Selection Mode" (sheet up), ready for the next task.

5.  **Return to Interaction:** The user is satisfied. They tap the **"Interact"** button. The bottom sheet slides down, the button text changes to **"Select"**, and the inspection overlay is removed. The user can now tap their new red button.