# Cortex IDE: UI/UX Design

## Overview
This document outlines the UI/UX design for the Cortex IDE, an intent-driven creation engine for Android. The user experience is designed to be entirely visual and conversational, completely abstracting the complexities of software development. The user is a **director**, not a developer.

## The Core Interaction Model
The entire user experience is built around the user interacting with their live, running application while an AI agent works on it in the background, orchestrated by a service on the user's device.

1.  **The Live App (The User View):** This is the user's primary and only interface. It is the running, compiled Android application they are building. The experience is always WYSIWYG ("what you see is what you get").

2.  **The Cortex Overlay (The Interactive Canvas):** When the user enters "Edit Mode," a transparent overlay service is activated over the Live App. This canvas is the core of the interaction model.
    -   It intercepts user touches and selections.
    -   It captures screenshots and analyzes the screen to identify the UI component the user is targeting.
    -   It presents a contextual prompt, allowing the user to provide natural language instructions for the selected element.

## The User Journey: "Select and Instruct"
The core workflow is a simple, powerful, and asynchronous loop:

1.  **Enter Edit Mode:** The user taps a single "Edit" button to activate the Cortex Overlay.
2.  **Visual Selection:** The user taps directly on any element in their live app. The overlay highlights the element to confirm the selection.
3.  **Contextual Instruction:** A small text prompt appears. The user types their desired change in plain English (e.g., "Make this button orange").
4.  **Asynchronous Feedback:** A non-intrusive notification indicates that the AI agent is working. The user is never blocked.
5.  **Seamless Relaunch:** Once the AI agent has completed the code modification and the app has been recompiled on the device, the Live App automatically restarts, seamlessly showing the new change.

## Automated Error Handling
The user is never shown a technical error. If the AI writes code that fails to compile, the on-device Cortex Service will automatically capture the error, send it back to the AI for a fix, and retry the process, keeping the user informed with a simple "Jules is debugging an issue..." status.
