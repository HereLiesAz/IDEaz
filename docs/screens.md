# Peridium IDE: Application Screens & UI Phases

This document provides a high-level overview of the major screens and UI phases within the Peridium IDE. The user interface is designed to be minimal and contextual, guiding the user through the intent-driven creation process.

## 1. The Live App View
This is the primary state of the application. It is not a screen within the Peridium IDE itself, but rather the **user's own application**, running live on their device.

**Key UI Components:**
-   **The User's App:** The running, compiled application being built.
-   **Peridium Floating Button:** A persistent floating action button that allows the user to enter "Edit Mode."

## 2. Edit Mode (The Peridium Overlay)
This is the main interaction phase, implemented as a transparent service drawn over the user's live app.

**Key UI Components:**
-   **Screenshot Display:** Shows a static screenshot of the user's app.
-   **Selection Tool:** Allows the user to draw a box on the screenshot to select an element.
-   **Contextual Prompt:** A floating text input box that appears next to the selected area, for typing instructions.
-   **Status Indicator:** A non-intrusive UI element that provides high-level feedback on the AI's status ("Jules is working...", "Jules is debugging...").

## 3. The Peridium Hub Screen
This is a traditional Android screen within the Peridium IDE app that serves as the main dashboard and entry point.

**Key Components:**
-   **Project Launcher:** A button to launch the user's most recent application.
-   **Initial Project Creation:** A text input for the user to describe the new application they want to create from scratch.
-   **Settings Entrypoint:** A button or icon to navigate to the settings screen.

## 4. Settings Screen
A standard Android screen for configuring the Peridium IDE.

**Key Components:**
-   **Jules API Key Input:** A secure text field for the user to enter and save their personal Jules API key. This is the core of the "Bring Your Own Key" model.
-   **Other Preferences:** Options for managing notifications and other app settings.
