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
    -   **Status Area:** Displays `Build Status`, `AI Status`, etc.
    -   **Build Log:** A stream of all `aapt2`, `kotlinc`, `d8`, etc. output.
    -   **Global AI Chat:** A log for the contextless AI chat feature.
-   **Contextless Chat Input:** A text field at the bottom of the bottom sheet for global AI prompts.

## 3. The IDEaz Hub Screen
This is a traditional Android screen within the IDEaz IDE app that serves as the main dashboard and entry point.

**Key Components:**
-   **Nav Rail:** Provides navigation to Project Settings, **IDE** (which contains the Interact/Select toggle), and App Settings.
-   **Main Content Area:** Displays status information or settings screens.
-   **Global Console (Bottom Sheet):** A pull-up card containing:
    -   **Status Area:** Displays `Build Status`, `AI Status`, etc.
    -   **Build Log:** A stream of all `aapt2`, `kotlinc`, `d8`, etc. output.
    -   **Global AI Chat:** A log for the contextless AI chat feature.
-   **Contextless Chat Input:** A text field at the bottom of the bottom sheet for global AI prompts.

## 4. Settings Screen
A standard Android screen for configuring the IDEaz IDE.

**Key Components:**
-   **Jules API Key Input:** A secure text field for the user to enter and save their personal Jules API key.
-   **Project Settings:** Inputs for GitHub repository details.
-   **Other Preferences:** Options for managing notifications and other app settings.