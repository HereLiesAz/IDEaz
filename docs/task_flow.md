# IDEaz IDE: A Narrative User Journey

This document describes the end-to-end user journey within the IDEaz IDE.

---

### **Scenario 1: A Successful Visual Change (Tap-to-Select)**

**Goal:** Change the color of a button in the live application.

1.  **Activate Selection Mode:** The user taps the **"Select"** button. The bottom sheet slides up, and the button text changes to **"Interact"**.
2.  **Visual Selection & Intent:** The user **taps** a login button. A **floating log overlay** instantly appears, highlighting the button's bounds. A **prompt input box** appears just below it.
    > **User Input:** "Make this button red."
3.  **Automated AI Workflow:** The user taps "Submit." The prompt box disappears.
    > **Floating Log:** "Sending prompt to AI..."
    The `MainViewModel`:
    a.  Receives the `resourceId` ("login_button") and the prompt.
    b.  Looks up the `resourceId` in the `source_map.json`.
    c.  Constructs a rich prompt: `Context (for element login_button): File: ... User Request: "Make this button red."`
    d.  Sends the prompt to the user's chosen AI.
    e.  Streams AI status back to the floating log.
    f.  Receives a patch and applies it.
    g.  Triggers the `BuildService`.
4.  **Build & Relaunch:** The **global log** in the bottom sheet streams the build output. The build succeeds. The user's app restarts with the red button. The floating log overlay disappears.

---

### **Scenario 2: A Successful Area Change (Drag-to-Select)**

**Goal:** Add a new element to an empty part of the screen.

1.  **Activate Selection Mode:** The user is already in this mode.
2.  **Visual Selection & Intent:** The user **drags a box** over an empty area of the screen. A red rectangle shows the selection in real-time. On release, the **floating log overlay** appears, matching the box's size and position. The **prompt input box** appears below it.
    > **User Input:** "Add a 'Sign Up' link here."
3.  **Automated AI Workflow:** The user taps "Submit."
    > **Floating Log:** "Sending prompt to AI..."
    The `MainViewModel`:
    a.  Receives the `Rect` coordinates and the prompt.
    b.  Constructs a coordinate-based prompt: `Context: Screen area Rect(100, 500, 400, 550)... User Request: "Add a 'Sign Up' link here."`
    c.  Sends the prompt to the user's chosen AI (which must be able to interpret coordinates).
    d.  ... (steps e-g from Scenario 1) ...
4.  **Build & Relaunch:** The app restarts with the new "Sign Up" link visible in the area the user defined.

---

### **Scenario 3: Task Cancellation**

**Goal:** Cancel an AI task that is in progress.

1.  **AI Task In-Progress:** A floating log overlay is on screen, streaming AI updates.
    > **Floating Log:** "Jules is working..."
2.  **User Cancels:** The user taps the **(X) button** in the top-right corner of the floating log.
3.  **Confirmation:** A dialog box appears: "Are you sure you want to cancel this task?"
4.  **Action:** The user taps "Confirm."
    * The `MainViewModel` cancels the AI's `Job`.
    * The `MainViewModel` broadcasts a `TASK_FINISHED` command.
    * The `UIInspectionService` receives the broadcast and removes both the log and prompt overlays.
    * The user is now back in "Selection Mode," ready to start a new task.