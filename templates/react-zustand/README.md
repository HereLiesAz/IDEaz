# React + Zustand template

Vite + React with `zustand` for global state.

## Preview in IDEaz
Renders immediately — `zustand` is resolved from IDEaz's bundled runtime.
`zustand/middleware` (persist, devtools) and `zustand/shallow` are also bundled.

## Run / build locally
```sh
npm install
npm run dev
npm run build
```

## Deploy
Pushing to `main` builds and publishes to GitHub Pages (`.github/workflows/deploy.yml`).
The store lives inline in `src/App.jsx`; split it into `src/store.js` as you grow.
