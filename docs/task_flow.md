# IDEaz: A Narrative User Journey

This document describes the end-to-end user journey within the IDEaz. The user is a "director," not a developer, and their interaction is entirely visual and conversational.

---

### **Scenario 1: A Successful Visual Change**

**Goal:** Change the color of a button in the live application.

1.  **Activate Edit Mode:** The user is looking at their running application on their Android device. They tap a floating "IDEaz" button to activate the IDE's overlay. The screen dims slightly, and a message "Select an element to change" appears.

2.  **Visual Selection & Intent:** The user draws a box around a login button. The overlay shows the highlighted selection on a static screenshot. A text input field appears.
    > **User Input:** "Make this button red."

3.  **Automated AI Workflow:** The user taps "Submit." A small, non-intrusive notification appears: "Jules is working on your request..." In the background, the on-device IDEaz Service:
    a.  Captures the annotated screenshot and sends the request to the Jules API.
    b.  Waits for the Jules agent to commit the code change to the Invisible Repository.
    c.  Automatically pulls the latest commit.
    d.  Triggers an on-device compilation of the app.

4.  **Seamless Relaunch:** After a short period, the application automatically restarts. The user now sees the live app again, and the login button is red. The task is complete. The user never saw a line of code or a single IDE command.

---

### **Scenario 2: The Automated Debugging Loop**

**Goal:** Add a new, complex feature that results in a compile error.

1.  **Activate Edit Mode:** The user activates the overlay and selects the main content area of a page.
    > **User Input:** "Add a user profile card here that shows the user's name, email, and a profile picture."

2.  **Automated AI Workflow (Initial Attempt):** The user submits the request. The "Jules is working..." notification appears. In the background, the same workflow as before begins: the AI commits the code, the IDEaz Service pulls it and attempts to compile.

3.  **Compilation Fails:** This time, the on-device compilation fails. The Jules agent made a mistake, perhaps using an incorrect variable name.

4.  **Automated Self-Correction:** The IDEaz Service automatically detects the build failure. Instead of showing an error to the user, it:
    a.  Captures the entire compilation error log.
    b.  Triggers a *new* Jules API call with a new prompt: "Your last change failed to compile. Here is the error log. Please fix it and commit the corrected code."
    c.  The user-facing notification smoothly transitions to: "Jules is debugging an issue..."

5.  **Successful Relaunch:** The Jules agent debugs its own mistake, commits a fix, and the loop repeats. The IDEaz Service pulls the new commit and re-compiles. This time, it succeeds.
    The user's application restarts, and they now see the new user profile card, correctly implemented. They were never exposed to the intermediate failure or the technical error log.
