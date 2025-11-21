# IDEaz IDE: Application Screens & UI Phases

This document provides a high-level overview of the major screens and UI phases within the IDEaz IDE.

## 1. The Live App View (Interaction Mode)
This is the state where the user's app is fully interactive.
-   **Host App State:** The bottom sheet is fully hidden (`AlmostHidden`).
-   **User Action:** The user can tap, swipe, and interact with their app as a normal user would.

## 2. Edit Mode (Selection Mode)
This is the main interaction phase, where the IDE is active.
-   **Host App State:** The bottom sheet is visible (`Peek` or `Halfway`).
-   **Contextual Prompt/Log UI:** Floating overlay for interacting with specific elements.
-   **Global Console (Bottom Sheet):** Consolidated log for build status and contextless AI.

## 3. The IDEaz Hub Screen
**Project Settings Screen:**
-   **Setup Tab:** Configure project and build. **"Build" now automatically launches the app upon success.**
-   **Clone Tab:** Clone existing repos from GitHub.
-   **Load Tab:** Load local projects.

## 4. Settings Screen
A standard Android screen for configuring the IDEaz IDE.

**Key Components:**
-   **API Key Inputs:** Jules, Gemini, and **GitHub Personal Access Token**.
-   **Permissions:** Status of system permissions.
-   **Preferences:**
    -   **Show warning when cancelling AI task.**
    -   **Auto-report IDE internal errors to GitHub.**
-   **Debug:** Clear caches button.