# IDEaz IDE: Application Screens & UI Phases

This document provides a high-level overview of the major screens and UI phases within the IDEaz IDE. The user interface is designed to be minimal and contextual, guiding the user through the intent-driven creation process.

## 1. The Live App View
This is the primary state of the application. It is not a screen within the IDEaz IDE itself, but rather the **user's own application**, running live on their device.

**Key UI Components:**
-   **The User's App:** The running, compiled application being built.
-   **IDEaz Floating Button:** A persistent floating action button that allows the user to enter "Edit Mode." (Note: This is a legacy idea, the primary entry is now via the Host App's "Inspect" button).

## 2. Edit Mode (The IDEaz Overlay)
This is the main interaction phase, implemented as a transparent service drawn over the user's live app.

**Key UI Components:**
-   **Touch Interceptor:** A full-screen, transparent overlay that captures the user's initial tap to select an element.
-   **Contextual Prompt/Log UI:** A floating window (drawn by the `WindowManager`) that appears after selection. This UI is stateful:
    -   **Prompt State:** Shows a text input box for the user to type instructions for the selected element.
    -   **Log State:** After submission, the input box hides, and the window becomes a log box that streams the AI's chat output for that specific task.
    -   **Reply State:** If the AI asks a question, the prompt input box reappears below the log.

## 3. The IDEaz Hub Screen
This is a traditional Android screen within the IDEaz IDE app that serves as the main dashboard and entry point.

**Key Components:**
-   **Nav Rail:** Provides navigation to Project Settings, Status, Inspect, and App Settings.
-   **Main Content Area:** Displays status information or settings screens.
-   **Global Console (Bottom Sheet):** A pull-up card containing:
    -   **Build Log:** A stream of all `aapt2`, `kotlinc`, `d8`, etc. output.
    -   **Global AI Chat:** A log for the contextless AI chat feature.
-   **Contextless Chat Input:** A text field at the bottom of the bottom sheet for global AI prompts.

## 4. Settings Screen
A standard Android screen for configuring the IDEaz IDE.

**Key Components:**
-   **Jules API Key Input:** A secure text field for the user to enter and save their personal Jules API key.
-   **Project Settings:** Inputs for GitHub repository details.
-   **Other Preferences:** Options for managing notifications and other app settings.