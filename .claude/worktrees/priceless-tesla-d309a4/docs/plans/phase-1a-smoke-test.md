# Phase 1A — Smoke Test Checklist

**Milestone:** Open a hand-written PWA from a local Git repo, see it render
correctly in IDEaz, hit reload after manual file edit, see the change.

**Prerequisite:** A minimal hand-written PWA in a local Git repo on the device:
```
myapp/
  index.html           ← <h1>Hello PWA</h1>
  manifest.webmanifest ← {"name":"Test","display":"standalone","start_url":"/"}
  sw.js                ← empty service worker
```

---

## Setup

- [ ] Clone or copy the test PWA into IDEaz's internal storage (via Load / Import)
- [ ] Verify IDEaz detects it as `PWA` (check Project screen shows type `PWA`)

## Render

- [ ] Tap **IDEaz** rail → **Build** — confirm WebView shows `Hello PWA`
- [ ] Confirm no `file://` errors in the Console tab
- [ ] Confirm console log shows `[WEB]` messages for any JS `console.log(...)` calls

## Correct origin

- [ ] Open Console tab; confirm the page loaded from `https://appassets.androidplatform.net/files/`
- [ ] Confirm `file://` is **not** present in any console output

## Soft reload

- [ ] Edit `index.html` via Files tab: change `Hello PWA` → `Hello Reload`
- [ ] Tap **Reload** in the nav rail
- [ ] WebView now shows `Hello Reload`

## Hard reload

- [ ] Edit `index.html` again: change back to `Hello PWA`
- [ ] Tap **Hard Reload** in the nav rail
- [ ] WebView now shows `Hello PWA`; confirm no stale cache content

## File observer auto-reload

- [ ] Edit `index.html` via Files tab; save (close the editor)
- [ ] WebView reloads automatically within a few seconds (ProjectFileObserver)

## Security verification

- [ ] Open a prompt and execute JS: `fetch('file:///etc/hosts')`
- [ ] Confirm fetch fails (cross-origin blocked); no file content appears

---

**Phase 1A complete when all checkboxes pass.**
