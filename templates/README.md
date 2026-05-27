# IDEaz starter templates

The canonical starters live in their own GitHub repositories (and are wired into
project creation via the generate-from-template flow). This folder keeps the
React starter as an in-repo reference; the others have been exported to their
own repos.

## Template repositories
- **`HereLiesAz/ideaz-web`** — vanilla HTML/CSS/JS static site. No build; renders
  in the IDEaz preview and deploys to GitHub Pages as-is.
- **`HereLiesAz/ideaz-pwa`** — installable PWA: manifest, service worker, offline
  fallback. No build.
- **`HereLiesAz/ideaz-react`** — the React starter mirrored below.
- **`HereLiesAz/ideaz-android`** — View-based Kotlin app (AppCompatActivity + XML
  layouts, ViewBinding, Material Components) with its own build-and-release /
  CodeQL workflows.

## React (Vite) — preview instantly, build to deploy
[`react`](./react) is a real Vite + React repo. **IDEaz previews the source
directly** (no `npm install`, no build) using its in-browser runtime, which
transpiles JSX/TSX and resolves the bundled libraries via an import map. To
**deploy**, run a real build (`npm install && npm run build`); the template ships
a GitHub Pages workflow that does this automatically on push.

It's batteries-included: router + zustand + react-query + axios +
styled-components. Delete what you don't need; Redux Toolkit and Emotion are also
available in the preview runtime if you prefer them.

> **Library versions are pinned to match IDEaz's bundled runtime**, so what you
> see in the preview matches a real build. The bundled set is: react, react-dom,
> react-router(-dom), zustand, @reduxjs/toolkit, react-redux, axios,
> @tanstack/react-query, styled-components, @emotion/react, @emotion/styled.
> Importing a library outside that set works in a real build but not in the
> in-app preview.
