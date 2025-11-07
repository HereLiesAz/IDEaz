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
-   **User Action:** Tapping the screen selects a UI element, which triggers the **Contextual Prompt/Log UI**.

**Key UI Components in Selection Mode:**
-   **Contextual Prompt/Log UI:** A floating window (drawn by the `WindowManager`) that appears after selection. This UI is stateful:
    -   **Prompt State:** Shows a text input box for the user to type instructions.
    -   **Log State:** After submission, becomes a log box that streams the AI's chat output.
    -   **Reply State:** If the AI asks a question, the prompt input box reappears.
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
-   **API Key Inputs:** Secure text fields for the user to enter and save their **Jules API Key** and **Google AI Studio API Key**. Includes buttons to the key-generation pages.
-   **Project Settings:** Inputs for GitHub repository details.
-   **AI Assignments:** A series of dropdown menus allowing the user to assign an AI model (e.g., "Jules", "Gemini Flash") to each specific task ("Default", "Project Initialization", "Contextless Chat", "Overlay Chat").