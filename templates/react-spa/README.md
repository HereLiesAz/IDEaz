# React SPA template

A full single-page app combining the bundled libraries:
`react-router-dom` (routing), `zustand` (state), `@tanstack/react-query` +
`axios` (data), and `styled-components` (styling).

## Preview in IDEaz
Renders immediately — every library is resolved from IDEaz's bundled runtime on
a single shared React instance. The Data page's fetch needs network access.

## Run / build locally
```sh
npm install
npm run dev
npm run build
```

## Structure
- `src/main.jsx` — providers (Query, Router).
- `src/App.jsx` — themed shell, nav, routes (Home + Data).
- `src/store.js` — zustand store.

## Deploy
Pushing to `main` builds and publishes to GitHub Pages (`.github/workflows/deploy.yml`).
`HashRouter` keeps routing working on static hosting with no rewrites.
