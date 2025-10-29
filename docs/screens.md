# Cortex IDE: Application Screens

This document provides a high-level overview of the major screens and UI surfaces within the Cortex IDE Android application.

## 1. Main IDE Screen
This is the primary workspace where developers will spend most of their time. It is a multi-panel screen designed for efficient coding and interaction with the AI agent.

**Key Components:**
-   **Code Editor:** The central panel where the user can view and edit source code files. It features syntax highlighting, touch-based controls, and inline AI suggestions.
-   **Visual Previewer:** A resizable panel, typically adjacent to the Code Editor, that provides a live, interactive preview of the Jetpack Compose UI. This is the main surface for the "tap-to-prompt" interaction.
-   **File Explorer:** A collapsible side panel that displays the project's directory and file structure in a familiar tree view, allowing for easy navigation.
-   **Contextual Prompt Overlay:** A floating UI element that appears over the Visual Previewer when the user taps a component or selects an area. This is the primary input method for giving instructions to the Cortex AI agent.

## 2. Agent Log & Chat Screen
This is a secondary screen that provides a detailed history of the AI agent's activities and allows for a more traditional conversational interaction.

**Key Components:**
-   **Action Log:** A chronological, read-only log of every plan and action the agent has taken (e.g., "Generating plan for user authentication," "Modifying `LoginScreen.kt`").
-   **Code Diff Viewer:** Embedded viewers that show the specific code changes (additions and deletions) the agent has made to files.
-   **Chat Input:** A text input box at the bottom of the screen that allows the user to ask the agent high-level questions or give project-wide instructions that aren't tied to a specific UI element.

## 3. Git Version Control Screen
A dedicated screen for managing all version control operations.

**Key Components:**
-   **Status View:** Shows a list of modified, staged, and untracked files.
-   **Commit View:** A text area for writing commit messages and a button to perform the commit.
-   **History Log:** A scrollable list of previous commits for the current branch.
-   **Remote Controls:** Buttons for performing `push`, `pull`, and `fetch` operations.

## 4. Integrated Terminal Screen
Provides a fully functional command-line interface within the IDE.

**Key Components:**
-   **Terminal Emulator:** A standard terminal view that provides shell access.
-   **Session Management:** The ability to open multiple terminal tabs or sessions.
-   **Primary Use Cases:** Running Gradle tasks, executing custom scripts, or using advanced Git commands.

## 5. Project Management Screen
The initial screen for creating, opening, or cloning projects.

**Key Components:**
-   **Recent Projects:** A list of recently opened projects for quick access.
-   **Clone from URL:** An input field for a Git repository URL to be cloned locally.
-   **Create New Project:** (Future) A wizard for scaffolding a new, standard Android project.

## 6. Settings Screen
Allows the user to customize the IDE's behavior and appearance.

**Key Components:**
-   **Editor Settings:** Options for font size, theme (light/dark/dynamic), and key bindings.
-   **AI Settings:** Preferences related to the AI agent's behavior.
-   **Account Management:** Options for signing in and out of the user's account.
