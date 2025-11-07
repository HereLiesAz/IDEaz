# IDEaz: UI/UX Design

## Overview
This document outlines the UI/UX design for the IDEaz, an intent-driven creation engine for Android. The user experience is designed to be entirely visual and conversational, completely abstracting the complexities of software development. The user is a **director**, not a developer.

## The Core Interaction Model
The entire user experience is built around the user interacting with their live, running application while an AI agent works on it in the background, orchestrated by services on the user's device.

1.  **The Live App (The Target Application):** This is the user's primary and only interface. It is the running, compiled Android application they are building, running in its own sandboxed process.

2.  **The IDEaz Overlay (The UI Inspection Service):** When the user enters "Inspection Mode," a privileged `AccessibilityService` is activated. This service draws a transparent overlay over the Live App and captures touch events to identify the UI element the user is selecting.

3.  **The Global Console (The Host App):** A bottom sheet within the main IDEaz app provides a log for **build/compile** output and a separate log for a **contextless AI chat**.

## The User Journey: "Select and Instruct"
The core workflow is a simple, powerful, and asynchronous loop:

1.  **Enter Inspection Mode:** The user taps a single "Inspect" button to activate the UI Inspection Service's overlay.
2.  **Visual Selection:** The user taps on an element in their live app. The service identifies the element and notifies the Host App.
3.  **Contextual Instruction:** The Host App commands the service to render a **floating UI** near the selected element. This UI appears with a prompt input box. The user types their desired change in plain English (e.g., "Make this button orange").
4.  **Asynchronous Feedback:** Once submitted, the input box vanishes and is replaced by a **log view in the same floating UI**. This log streams the **AI chat output** for that specific task. The user is never blocked.
5.  **Seamless Relaunch:** Once the AI agent has completed the code modification and the app has been recompiled (with build logs streaming to the *global console*), the Live App automatically restarts, seamlessly showing the new change. The floating AI log UI then disappears.

## Automated Error Handling
The user is never shown a technical error. If the AI writes code that fails to compile, the **global build console** will show the error, and the "Debug with AI" feature can be used there. If the AI chat itself fails, the **contextual overlay log** will show the error message.

## A Responsive "Undo" Experience
A critical part of a seamless user experience is a fast and intuitive "undo" feature. A standard `git revert` followed by a full re-compile and relaunch cycle would feel too slow.

To address this, IDEaz will implement a more responsive, two-phase undo strategy:

1.  **Instant Visual Rollback:** The On-Device Build Service will always cache the last known-good APK before a new version is installed. When the user requests an "undo," the service will immediately reinstall this cached APK for a near-instant visual rollback.
2.  **Background Source Synchronization:** While the user sees the instant rollback, the Host App will perform the necessary `git revert` operation in the background. It will then trigger a re-compile to ensure the project's source of truth is correctly synchronized with the version the user is now seeing.