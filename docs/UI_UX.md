# IDEaz: UI/UX Design System

## Overview
This document outlines the UI/UX design for IDEaz. The user experience is designed to be visual, conversational, and "post-code". The user acts as a **director**, interacting with a live app, while the AI acts as the **engineer**.

## Design Philosophy
*   **Material 3 Expressive:** The UI follows Material 3 guidelines using Jetpack Compose.
*   **Immersive:** The IDE interface should be unobtrusive. The user's app is the star.
*   **Reactive:** The UI must respond immediately to state changes (build progress, AI output).
*   **Greyscale/Neutral:** The IDE theme is intentionally neutral (greyscale) to avoid clashing with the user's project colors.

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

## Key Components

### Navigation (`AzNavRail`)
*   **Location:** Left side of the screen (landscape/tablet) or bottom bar (phone).
*   **Structure:**
    *   **Project:** Manage projects (Load, Clone, Create).
    *   **Editor:** (Read-only) View files.
    *   **Git:** Manage version control.
    *   **Settings:** Configure AI, tools, and theme.
*   **Implementation:** Custom `AzNavRail` component.

### Bottom Sheet (`IdeBottomSheet`)
*   **Library:** `com.composables.core.BottomSheet`
*   **Content:**
    *   **Logs:** A streaming list of build/AI logs (`LazyColumn`).
    *   **Chat:** Contextless AI chat input.
*   **Behavior:**
    *   **Peek:** Shows status summary.
    *   **Expanded:** Shows full logs and chat.

### Floating Overlays (`UIInspectionService`)
*   **Technology:** `WindowManager` via `AccessibilityService`.
*   **Elements:**
    *   **Selection Highlight:** A `View` drawing a border around the selected area.
    *   **Prompt Box:** A small floating window with an `AzTextBox` for input.
    *   **Log Overlay:** A floating window displaying real-time AI progress near the selected element.

## Theming (`ui/theme`)
*   **Color Palette:** Defined in `Color.kt`. Primarily greys, blacks, and whites.
*   **Typography:** Standard Material 3 typography.
*   **Dark/Light Mode:** User-toggleable in Settings. Note: "Dark Mode" in this app often means "Light Text on Dark Background", but the "Greyscale" theme might invert this.
*   **Shape:** `AzButtonShape` defines button corners.

## Common UI Patterns
*   **`AzButton`:** The standard button component.
*   **`AzTextBox`:** The standard input field.
*   **`AzNavRail`:** The main navigation structure.
*   **`LiveOutputBottomCard`:** A specific card for showing live build output, floating above the bottom sheet.

## Accessibility
*   The IDE itself uses an Accessibility Service, but the IDE's UI (Host App) must also be accessible.
*   **Content Descriptions:** All icons and images must have content descriptions.
*   **Touch Targets:** Minimum 48dp touch targets.
