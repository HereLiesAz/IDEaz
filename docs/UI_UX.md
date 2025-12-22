# IDEaz: UI/UX Design System

## Overview
IDEaz is a **visual, post-code creation engine**. The user acts as a director, interacting with their live app, while the AI acts as the engineer.

## The Core Interaction Model
The interaction model revolves around the **Live App** and the **IDE Overlay**.

1.  **The Live App:** The user's running application.
2.  **The IDE Overlay (`IdeazOverlayService`):** A system alert window service that hosts the IDE UI (Navigation Rail, Bottom Sheet, Settings) and intercepts touches for selection.
3.  **The Global Console (`IdeBottomSheet`):** A persistent bottom sheet in the host app for global logs and contextless chat.

## Modes of Operation

### 1. Interaction Mode (Docked / FAB)
*   **Purpose:** Allow the user to use their app normally.
*   **State:** `IdeazOverlayService` window (for the Rail) is **dynamic** (managed by `AzNavRail`). It shrinks to wrap the content (Rail/FAB) when stationary to unblock the underlying app, and expands to full screen during drag operations to ensure smooth movement.
*   **Visuals:** The user sees their app cleanly with the Rail docked.
    *   **FAB Mode:** Long-press the rail header to collapse it into a Floating Action Button (FAB). The FAB can be dragged around the screen.
*   **Trigger:** User taps "Interact" or toggles the mode. Long-press header for FAB.

### 2. Selection Mode
*   **Purpose:** Allow the user to select UI elements to modify.
*   **State:** `IdeazOverlayService` window is full screen (`MATCH_PARENT`) and intercepts touches.
*   **Visuals:**
    *   **Drag-to-Select:** Dragging draws a selection rectangle.
    *   **Prompt:** A floating input box appears near the selection.
*   **Trigger:** User taps "Select".

### 3. Overlay Mode (Settings/Project)
*   **Purpose:** Configure the project or IDE settings.
*   **State:** `IdeazOverlayService` window is full screen and opaque.
*   **Visuals:** Settings or Project screens are visible.

## Key Interface Elements

### 1. The Unified Overlay
*   **Concept:** The IDE runs entirely within a System Alert Window (`TYPE_APPLICATION_OVERLAY`).
*   **Compliance:** The overlay service extends `AzNavRailOverlayService`, leveraging the library's built-in `WindowManager` handling for dynamic sizing and drag support.
*   **Transparency Rules:**
    *   **Interact/Select Mode:** The background MUST be transparent.
    *   **Settings/Setup:** The background MUST be **Opaque** (Solid).
*   **Attachment:** The overlay is persistent.

### 2. The Pull-Up Bottom Card (Console)
A versatile bottom sheet that provides visibility into the background processes.
*   **Tabs:** Build, Git, AI, All.
*   **Content:** Log output from respective sources.
*   **Empty State:** Displays context-aware messages (e.g., "No build logs yet") when no logs are available for the selected tab.
*   **Theming:** High contrast (Dark Grey/White in Dark Mode, Light Grey/Black in Light Mode).

### 3. The Update Popup
*   **Trigger:** Appears when a background build (Local or Remote) completes successfully while the user is interacting.
*   **Message:** "Updating, gimme a sec."
*   **Behavior:**
    *   **Clipboard:** Any text currently entered in a prompt input box **MUST** be automatically copied to the system clipboard.
    *   **Dismissal:** Disappears automatically when the new version of the app loads.

### 4. Persistent Notification
*   **Purpose:** Keeps the IDE "alive" and informative.
*   **Content:** Displays the **three most recent lines** of log output (collapsed) and **10+ lines** (expanded).
*   **Reliability:** Ensures the `IdeazOverlayService` is treated as a foreground service.

## Visual Language & Theming (`ui/theme`)
*   **Theme:** Material 3 Dark Mode (Default). High contrast.
*   **Palette:**
    *   **Background:** `#1E1E1E` (Dark Grey).
    *   **Surface:** `#2D2D2D`.
    *   **Primary:** `#BB86FC` (Purple).
    *   **Error:** `#CF6679`.
*   **Typography:** Monospace for code/logs. Sans-serif for UI (Standard Material 3 Expressive).
*   **Dark/Light Mode:** User-toggleable or System follow.

## Common UI Patterns
*   **`AzButton`:** Standard button.
*   **`AzTextBox`:** Standard input field.
*   **`LiveOutputBottomCard`:** Floating status card above the bottom sheet.

## Accessibility
*   **Content Descriptions:** All icons and images must have content descriptions.
*   **Touch Targets:** Minimum 48dp touch targets. Group related elements (e.g. Label + Switch) into a single toggleable row.
*   **Semantics:** Use `heading()` semantics for section titles.
