# Cortex IDE: UI/UX Design

## Overview
This document outlines the UI/UX design for the Cortex IDE, an intent-driven creation engine for Android. The user experience is designed to be entirely visual and conversational, completely abstracting the complexities of software development. The user is a **director**, not a developer.

## The Core Interaction Model
The entire user experience is built around the user interacting with their live, running application while an AI agent works on it in the background, orchestrated by a service on the user's device.

1.  **The Live App (The User View):** This is the user's primary and only interface. It is the running, compiled Android application they are building. The experience is always WYSIWYG ("what you see is what you get").

2.  **The Cortex Overlay (The Interactive Canvas):** When the user enters "Edit Mode," a transparent overlay service is activated over the Live App. This canvas is the core of the interaction model.
    -   It takes a screenshot of the current screen.
    -   It allows the user to draw a box to select an area.
    -   It presents a contextual prompt, allowing the user to provide natural language instructions for the selected element.

## The User Journey: "Select and Instruct"
The core workflow is a simple, powerful, and asynchronous loop:

1.  **Enter Edit Mode:** The user taps a single "Edit" button to activate the Cortex Overlay.
2.  **Visual Selection:** The user draws a box around an element in the screenshot of their live app. The overlay highlights the element to confirm the selection.
3.  **Contextual Instruction:** A small text prompt appears. The user types their desired change in plain English (e.g., "Make this button orange").
4.  **Asynchronous Feedback:** A non-intrusive notification indicates that the AI agent is working. The user is never blocked.
5.  **Seamless Relaunch:** Once the AI agent has completed the code modification and the app has been recompiled on the device, the Live App automatically restarts, seamlessly showing the new change.

## Automated Error Handling
The user is never shown a technical error. If the AI writes code that fails to compile, a process detailed in the Blueprint, the system handles it autonomously, keeping the user informed with a simple "Jules is debugging an issue..." status.

## A Responsive "Undo" Experience
A critical part of a seamless user experience is a fast and intuitive "undo" feature. A standard `git revert` followed by a full re-compile and relaunch cycle would feel too slow and cumbersome for a simple undo action.

To address this, the Cortex IDE will implement a more responsive, two-phase undo strategy:

1.  **Instant Visual Rollback:** The Cortex Service will always cache the last known-good APK before installing a new version. When the user requests an "undo," the service will immediately reinstall this cached APK. This provides a near-instant visual rollback, allowing the user to continue their work without a lengthy interruption.
2.  **Background Source Synchronization:** While the user sees the instant rollback, the Cortex Service will perform the necessary `git revert` operation in the background. It will then trigger a re-compile to ensure that the "Invisible Repository" (the source of truth) is correctly synchronized with the version the user is now seeing. This background process ensures consistency without sacrificing user-perceived performance.
