# PWA template

An installable, offline-capable Progressive Web App with no build step.

- **IDEaz preview:** works as-is. Served over HTTPS, so the service worker
  registers and caches the app shell.
- **Deploy:** any static HTTPS host. For GitHub Pages, enable Pages → "Deploy
  from a branch" (root). No build required.

## Files
- `index.html` — app shell.
- `styles.css`, `app.js` — styles and service-worker registration.
- `sw.js` — service worker: precaches the shell, network-first navigations with
  an offline fallback. Bump `CACHE` when the shell changes.
- `manifest.webmanifest` — install metadata.
- `icon.svg` — app icon (scalable, maskable).
