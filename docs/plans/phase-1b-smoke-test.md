# Phase 1B Smoke Test

Manual verification for the Web Bridge milestone.

## Pre-conditions
- Device / emulator running Android 11+
- IDEaz installed from a debug build of this branch
- A PWA project created on-device (e.g. the bundled `HelloPWA` template) with
  `index.html`, `manifest.webmanifest`, and at least one button/heading element

## Steps

### 1. Load a PWA in App View
1. Open IDEaz → select the PWA project → tap ▶ (Launch).
2. Verify the PWA renders in the WebView without a white screen.
3. Check that the AI log does NOT contain any `[WEB]` error lines.

### 2. Enter Select Mode
1. Tap the crosshair icon in the NavRail.
2. Verify `isSelectMode == true` (NavRail icon highlights).
3. **Verify the WebView cursor is `crosshair`** (visible on devices with a mouse/stylus).

### 3. Tap an element
1. Tap a button or heading element in the PWA.
2. Verify the `SelectionOverlay` disappears (select mode exits).

### 4. Verify AI log entry
1. Pull down the bottom sheet.
2. Verify a `[WEB-ELEMENT] {…}` line appears in the console.
3. The JSON should contain `tagName`, `selector`, `outerHtml`, `innerText`, and `boundingRect`.

### 5. Verify contextual chat
1. Verify the `ContextualChatOverlay` panel appears on screen.
2. Tap the × close button; verify it dismisses.

### 6. Cursor resets on exit
1. Re-enter and then exit Select Mode without tapping anything.
2. Verify the WebView cursor returns to default (not crosshair).

## Pass Criteria
All 6 steps complete without errors or crashes.
