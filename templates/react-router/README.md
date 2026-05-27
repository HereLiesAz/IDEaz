# React Router template

Vite + React + `react-router-dom`, using `HashRouter` for zero-config static
hosting.

## Preview in IDEaz
Renders immediately — `react-router-dom` is resolved from IDEaz's bundled
runtime. No `npm install` needed.

## Run / build locally
```sh
npm install
npm run dev
npm run build
```

## Deploy
Pushing to `main` builds and publishes to GitHub Pages (`.github/workflows/deploy.yml`).
`HashRouter` means deep links and refreshes work without server rewrites.
Edit routes in `src/App.jsx`.
