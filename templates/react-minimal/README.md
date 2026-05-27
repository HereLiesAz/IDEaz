# React Minimal template

A Vite + React starter with no extra dependencies.

## Preview in IDEaz
Open the project — it renders immediately. IDEaz transpiles `src/main.jsx` in the
WebView and resolves `react` / `react-dom` from its bundled runtime. No
`npm install` needed.

## Run / build locally
```sh
npm install
npm run dev      # local dev server
npm run build    # production build → dist/
```

## Deploy
Pushing to `main` builds and publishes to GitHub Pages via
`.github/workflows/deploy.yml`. Enable Pages → "GitHub Actions" once in repo
settings.
