# User Interface / User Experience (UI/UX)

## 1. Visual Language
*   **Theme:** Material 3 Dark Mode (Default). High contrast.
*   **Palette:**
    *   **Background:** `#1E1E1E` (Dark Grey).
    *   **Surface:** `#2D2D2D`.
    *   **Primary:** `#BB86FC` (Purple).
    *   **Error:** `#CF6679`.
*   **Typography:** Monospace for code/logs. Sans-serif for UI.

## 2. Navigation
*   **Rail:** `IdeNavRail` on the left.
    *   **Top:** Contextual actions (Prompt, Build).
    *   **Bottom:** Global navigation (Files, Settings).
*   **Sheet:** `IdeBottomSheet` at the bottom.
    *   **Draggable:** Hidden -> Peek -> Half -> Full.

## 3. The Overlay Experience
*   **Goal:** "The IDE is the App".
*   **Transparency:**
    *   **Interact Mode:** Overlay is `Touchable = false`.
    *   **Select Mode:** Overlay is `Touchable = true`. Background is `Color.Transparent`.
*   **Visual Feedback:**
    *   **Selection:** Drawn as a colored Border (`Canvas`).
    *   **Loading:** Indeterminate progress bar in the Rail or Bubble.

## 4. Notifications
*   **Foreground Service:** "IDEaz is running".
    *   **Action:** "Exit", "Prompt".
*   **Build Status:** "Build in progress..." -> "Build Complete".

## 5. Accessibility
*   **Semantics:** All interactive elements must have proper roles (Button, Switch, etc.) and state descriptions.
*   **Touch Targets:** Minimum 48dp. Group related elements (e.g. Label + Switch) into a single toggleable row.
*   **Headings:** Use `heading()` semantics for section titles to assist screen reader navigation.
