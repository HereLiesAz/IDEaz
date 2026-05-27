# React template

A batteries-included Vite + React starter wired with the common app stack:
`react-router-dom` (routing), `zustand` (state), `@tanstack/react-query` +
`axios` (data), and `styled-components` (styling).

## Preview in IDEaz
Renders immediately — every library is resolved from IDEaz's bundled runtime on
a single shared React instance. No `npm install` needed. The Data page's fetch
requires network access on the device.

## Run / build locally
```sh
npm install
npm run dev      # local dev server
npm run build    # production build → dist/
```

## Structure
- `src/main.jsx` — providers (Query, Router).
- `src/App.jsx` — themed shell, nav, routes (Home + Data).
- `src/store.js` — zustand store.

Use what you need and delete the rest — drop the `Data` route to remove
react-query/axios, or swap `styled-components` for plain CSS.

## Also available in the IDEaz preview
The preview runtime also bundles `@reduxjs/toolkit` + `react-redux` (if you
prefer Redux over zustand) and `@emotion/react` / `@emotion/styled` (if you
prefer Emotion over styled-components). Import them and they resolve in the
preview with no setup; add them to `package.json` so a real build picks them up.

## Deploy
Pushing to `main` builds and publishes to GitHub Pages
(`.github/workflows/deploy.yml`); enable Pages → "GitHub Actions" once in repo
settings. `HashRouter` keeps routing working on static hosting with no rewrites.
