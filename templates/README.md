# IDEaz starter templates

Self-contained starter projects. Each subfolder is meant to be exported to its
own repository and used as a "Use this template" base.

Two families:

## Deploy-ready static (work everywhere)
Plain static files — no build step. They render in the IDEaz preview **and**
deploy to GitHub Pages as-is.

- [`static-site`](./static-site) — vanilla HTML/CSS/JS landing page.
- [`pwa`](./pwa) — installable PWA: manifest, service worker, offline fallback.

## React (Vite) — preview instantly, build to deploy
Real Vite + React repos. **IDEaz previews the source directly** (no `npm install`,
no build) using its in-browser runtime, which transpiles JSX/TSX and resolves the
bundled libraries via an import map. To **deploy**, run a real build
(`npm install && npm run build`); each template ships a GitHub Pages workflow that
does this automatically on push.

- [`react-minimal`](./react-minimal) — React + ReactDOM only.
- [`react-router`](./react-router) — `react-router-dom` multi-route SPA.
- [`react-zustand`](./react-zustand) — `zustand` state management.
- [`react-redux`](./react-redux) — `@reduxjs/toolkit` + `react-redux`.
- [`react-query`](./react-query) — `@tanstack/react-query` + `axios` data fetching.
- [`react-styled`](./react-styled) — `styled-components` (and `@emotion`-ready).
- [`react-spa`](./react-spa) — full app: router + zustand + react-query + styled-components.

> **Library versions are pinned to match IDEaz's bundled runtime**, so what you
> see in the preview matches a real build. The bundled set is: react, react-dom,
> react-router(-dom), zustand, @reduxjs/toolkit, react-redux, axios,
> @tanstack/react-query, styled-components, @emotion/react, @emotion/styled.
> Importing a library outside that set works in a real build but not in the
> in-app preview.

The Android (Jetpack Compose) starter already lives in the app at
`app/src/main/assets/project` and can be exported from there.
