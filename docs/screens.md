# IDEaz IDE: Application Screens & UI Phases

This document provides a high-level overview of the major screens and UI phases within the IDEaz IDE.

## 1. The Live App View (Interaction Mode)
This is the state where the user's app is fully interactive.
-   **Host App State:** The bottom sheet is fully hidden (`AlmostHidden`). The NavRail "IDE" host item's toggle shows **"Select"**.
-   **Service State:** The `UIInspectionService` is stopped.
-   **User Action:** The user can tap, swipe, and interact with their app as a normal user would.

## 2. Edit Mode (Selection Mode)
This is the main interaction phase, where the IDE is active.
-   **Host App State:** The bottom sheet is visible (`Peek` or `Halfway`). The NavRail "IDE" host item's toggle shows **"Interact"**.
-   **Service State:** The `UIInspectionService` is running, drawing a transparent touch-interceptor overlay.
-   **User Action:** **Tapping** selects an element. **Dragging** selects an area.

**Key UI Components in Selection Mode:**
-   **Contextual Prompt/Log UI:** This is a two-part UI rendered by the `UIInspectionService`:
    1.  **Log Overlay:** A semi-transparent window that is *sized and positioned to match the user's selection*. It streams the AI chat and has a **Cancel (X) button** in the top-right corner.
    2.  **Prompt Overlay:** A text input box that appears *directly below* the Log Overlay for the user to type their prompt.
-   **Global Console (Bottom Sheet):** The visible pull-up card containing:
    -   **Consolidated Log:** A single, scrollable view for *all* global output: Build status, AI status, compile logs, and contextless AI chat history.
-   **Contextless Chat Input:** A text field at the bottom of the bottom sheet for global AI prompts.

## 3. The IDEaz Hub Screen
This is a traditional Android screen within the IDEaz IDE app that serves as the main dashboard and entry point.

**Key Components:**
-   **Nav Rail:** Provides navigation to Project Settings, **IDE** (which contains the Interact/Select toggle), and App Settings.
-   **Main Content Area:** Displays settings screens. The "main" screen is now empty, as all status has moved to the bottom sheet.
-   **Global Console (Bottom Sheet):** The pull-up card described in section 2.

## 4. Settings Screen
A standard Android screen for configuring the IDEaz IDE.

**Key Components:**
-   **API Key Inputs:** Secure text fields for the user to enter and save their **Jules API Key** and **Google AI Studio API Key**. (Note: The Jules key is used by the `ApiClient`, but not by the primary `JulesCliClient`.)
-   **Project Settings:** Inputs for GitHub repository details.
-   **AI Assignments:** Dropdowns to assign an AI model to each task (e.g., "Jules Tools CLI" or "Gemini").
-   **Preferences:** A checkbox to **"Show warning when cancelling AI task"**.