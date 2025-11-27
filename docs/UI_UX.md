# IDEaz: UI/UX Design System

## Overview
IDEaz is a **visual, post-code creation engine**. The user acts as a director, interacting with their live app, while the AI acts as the engineer.

## The Core Interaction Model
The interaction model revolves around the **Live App** and the **IDE Overlay**.

1.  **The Live App:** The user's running application.
2.  **The IDE Overlay (`UIInspectionService`):** A transparent `AccessibilityService` overlay that intercepts touches for element selection.
3.  **The Global Console (`IdeBottomSheet`):** A persistent bottom sheet in the host app for global logs and contextless chat.

## Modes of Operation

### 1. Interaction Mode
*   **Purpose:** Allow the user to use their app normally.
*   **State:** `UIInspectionService` is **inactive** (or transparent/pass-through). Bottom sheet is hidden or minimized.
*   **Visuals:** The user sees their app cleanly.
*   **Trigger:** User taps "Interact" or swipes down the bottom sheet.

### 2. Selection Mode
*   **Purpose:** Allow the user to select UI elements to modify.
*   **State:** `UIInspectionService` is **active** and intercepting touches.
*   **Visuals:**
    *   **Tap-to-Select:** Tapping an element highlights it with a bounding box.
    *   **Drag-to-Select:** Dragging draws a selection rectangle.
    *   **Prompt:** A floating input box appears near the selection.
*   **Trigger:** User taps "Select" or swipes up the bottom sheet.

## Key Interface Elements

### 1. The Invisible Overlay
*   **Concept:** The IDE is an "invisible layer" over the user's app.
*   **Transparency Rules:**
    *   **IDE Mode:** The background MUST be transparent. The user sees their app.
    *   **Settings/Setup:** The background MUST be **Opaque** (Solid). Transparency here is a bug.
*   **Attachment:** The overlay must "stick" to the target app. It should only be visible when the target package is in the foreground. If the app crashes or updates, the overlay waits.

### 2. The Pull-Up Bottom Card (Console)
A versatile bottom sheet that provides visibility into the background processes. It adapts its content based on the current context:
*   **Git Operations:** Displays **Live Terminal Output** (Clone, Pull, Push status).
*   **Building:** Displays **Live Build Logcat** (Compiler output, errors).
*   **AI Task:** Displays **Jules' Live Output Log** (Reasoning, Activity updates).
*   **Rule:** "Any time the user has to wait for something to be done, the live logcat of that process should be shown."

### 3. The Update Popup
*   **Trigger:** Appears when a background build (Local or Remote) completes successfully while the user is interacting.
*   **Message:** "Updating, gimme a sec."
*   **Behavior:**
    *   **Clipboard:** Any text currently entered in a prompt input box **MUST** be automatically copied to the system clipboard.
    *   **Dismissal:** Disappears automatically when the new version of the app loads.

### 4. Persistent Notification
*   **Purpose:** Keeps the IDE "alive" and informative when backgrounded.
*   **Content:** Displays the **three most recent lines** of log output (from the Bottom Card context).
*   **Reliability:** Ensures the `BuildService` is treated as a foreground service by the OS.

## Theming (`ui/theme`)
*   **Color Palette:** Defined in `Color.kt`. Primarily greys, blacks, and whites (Neutral).
*   **Typography:** Standard Material 3 Expressive.
*   **Dark/Light Mode:** User-toggleable. "Dark Mode" typically means light text on dark background.

## Common UI Patterns
*   **`AzButton`:** Standard button.
*   **`AzTextBox`:** Standard input field.
*   **`LiveOutputBottomCard`:** Floating status card above the bottom sheet.

## Accessibility
*   **Content Descriptions:** All icons and images must have content descriptions.
*   **Touch Targets:** Minimum 48dp touch targets.
