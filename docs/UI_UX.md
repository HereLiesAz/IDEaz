# IDEaz: UI/UX Design

## Overview
This document outlines the UI/UX design for the IDEaz, an intent-driven creation engine for Android. The user experience is designed to be entirely visual and conversational, completely abstracting the complexities of software development. The user is a **director**, not a developer.

## The Core Interaction Model
The entire user experience is built around the user interacting with their live, running application while an AI agent works on it in the background, orchestrated by services on the user's device.

1.  **The Live App (The Target Application):** This is the user's primary and only interface. It is the running, compiled Android application they are building.
2.  **The IDEaz Overlay (The UI Inspection Service):** A privileged `AccessibilityService` that is active during "Selection Mode." It draws a transparent overlay over the LiveApp to capture all touch events.
3.  **The Global Console (The Host App):** A bottom sheet within the main IDEaz app that provides a **consolidated global log** for build status, AI status, compile output, and contextless AI chat.

## The Two Modes: "Interact" vs. "Select"

The app operates in two distinct, user-controlled modes, managed by both a toggle button in the **"IDE" NavRail group** and the bottom sheet gesture:

1.  **Interaction Mode:**
    * **State:** The bottom sheet is fully hidden (`AlmostHidden`).
    * **Button:** The NavRail toggle shows **"Select"**.
    * **Action:** The `UIInspectionService` is **stopped**. The user can fully interact with their live application (tap buttons, navigate, etc.).
    * **Trigger:** User swipes the bottom sheet all the way down OR taps the "Interact" button.

2.  **Selection Mode:**
    * **State:** The bottom sheet is visible (`Peek` or `Halfway`).
    * **Button:** The NavRail toggle shows **"Interact"**.
    * **Action:** The `UIInspectionService` is **started**. All touches are intercepted by the `touchInterceptor` overlay.
    * **Trigger:** User swipes the bottom sheet up OR taps the "Select" button.

## The User Journey: "Select and Instruct"
The core workflow is a simple, powerful, and asynchronous loop:

1.  **Enter Selection Mode:** The user taps the "Select" button (or swipes up the bottom sheet). The button text changes to "Interact," and the inspection overlay becomes active.
2.  **Visual Selection (Hybrid):**
    * **If the user taps:** The service identifies the UI element, and its bounds are highlighted.
    * **If the user drags:** The service draws a red, semi-transparent rectangle. On release, this rectangle is highlighted.
3.  **Contextual Instruction:** In both cases, a **floating UI window (the log overlay)** is created matching the selection's bounds, and a **prompt input box** appears just below it. The user types their instruction.
4.  **Asynchronous Feedback:** Once submitted, the prompt box disappears, and the floating log overlay begins streaming the AI's chat output. A small **(X) button** is visible in the corner of the log overlay.
5.  **Cancellation:** If the user presses the (X), a dialog asks for confirmation. If confirmed, the AI task is cancelled, and the overlay UI disappears, returning the user to Selection Mode.
6.  **Build & Relaunch:** If successful, the AI provides a patch. The `BuildService` compiles the app, streaming all **build logs** to the **main app's bottom sheet**.
7.  **Cycle Complete:** The build succeeds, the user's app restarts with the change, and the floating AI log UI disappears. The app remains in "Selection Mode."