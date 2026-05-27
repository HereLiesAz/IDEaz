# React + TanStack Query template

Vite + React with `@tanstack/react-query` for server-state and `axios` for HTTP.

## Preview in IDEaz
Renders immediately — both libraries are resolved from IDEaz's bundled runtime.
The sample fetch needs network access on the device.

## Run / build locally
```sh
npm install
npm run dev
npm run build
```

## Deploy
Pushing to `main` builds and publishes to GitHub Pages (`.github/workflows/deploy.yml`).
The query + fetch live in `src/App.jsx`.
