# IDEaz: UI/UX Design

## Overview
This document outlines the UI/UX design for the IDEaz, an intent-driven creation engine for Android. The user experience is designed to be entirely visual and conversational, completely abstracting the complexities of software development. The user is a **director**, not a developer.

## The Core Interaction Model
The entire user experience is built around the user interacting with their live, running application while an AI agent works on it in the background, orchestrated by services on the user's device.

1.  **The Live App (The Target Application):** This is the user's primary and only interface. It is the running, compiled Android application they are building, running in its own sandboxed process.

2.  **The IDEaz Overlay (The UI Inspection Service):** A privileged `AccessibilityService` that is active during "Selection Mode." It draws a transparent overlay over the LiveApp to capture touch events.

3.  **The Global Console (The Host App):** A bottom sheet within the main IDEaz app provides a log for **build/compile** output and a separate log for a **contextless AI chat**.

## The Two Modes: "Interact" vs. "Select"

The app operates in two distinct, user-controlled modes, managed by both a toggle button in the NavRail and the bottom sheet gesture:

1.  **Interaction Mode:**
    * **State:** The bottom sheet is fully hidden (`AlmostHidden`).
    * **Button:** The NavRail button shows **"Select"**.
    * **Action:** The `UIInspectionService` is **stopped**. The user can fully interact with their live application (tap buttons, navigate, etc.).
    * **Trigger:** User swipes the bottom sheet all the way down OR taps the "Interact" button.

2.  **Selection Mode:**
    * **State:** The bottom sheet is visible (`Peek` or `Halfway`).
    * **Button:** The NavRail button shows **"Interact"**.
    * **Action:** The `UIInspectionService` is **started**. Tapping the screen no longer interacts with the user's app but instead selects an element for modification.
    * **Trigger:** User swipes the bottom sheet up OR taps the "Select" button.

## The User Journey: "Select and Instruct"
The core workflow is a simple, powerful, and asynchronous loop:

1.  **Enter Selection Mode:** The user taps the "Select" button (or swipes up the bottom sheet). The button text changes to "Interact," and the inspection overlay becomes active.
2.  **Visual Selection:** The user taps on an element in their live app. The service identifies the element and notifies the Host App.
3.  **Contextual Instruction:** The Host App commands the service to render a **floating UI** near the selected element. This UI appears with a prompt input box. The user types their desired change in plain English (e.g., "Make this button orange").
4.  **Asynchronous Feedback:** Once submitted, the input box vanishes and is replaced by a **log view in the same floating UI**. This log streams the **AI chat output** for that specific task.
5.  **Build & Relaunch:** The AI provides a patch, which is applied. The `BuildService` compiles the app, streaming all **build logs** to the **main app's bottom sheet**.
6.  **Cycle Complete:** The build succeeds, the user's app restarts with the change, and the floating AI log UI disappears. The app remains in "Selection Mode" (sheet up, button says "Interact"), ready for the next instruction.