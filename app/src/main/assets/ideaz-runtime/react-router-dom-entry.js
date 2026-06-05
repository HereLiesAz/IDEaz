// react-router/dom entry shim for IDEaz in-WebView previews.
//
// react-router v7 exposes the DOM data-router API under the `react-router/dom`
// subpath. The vendored `react-router-dom.js` compat bundle imports
// `RouterProvider`/`HydratedRouter` from `react-router/dom`, so that specifier
// MUST resolve to a real module. The import map previously aliased
// `react-router/dom` back to `react-router-dom.js` itself, so the bundle imported
// its own exports — an export-name resolution cycle the browser rejects:
//   SyntaxError: Detected cycle while resolving name 'RouterProvider' in 'react-router/dom'
//
// RouterProvider, createBrowserRouter, and the rest of the DOM router API live in
// the core `react-router` bundle (which only depends on `react`), so re-export
// from there. `HydratedRouter` is an SSR-hydration export not present in the
// client core bundle; alias it to RouterProvider — the closest client-only
// equivalent, since in-WebView previews never SSR-hydrate.
export * from "react-router";
export { RouterProvider as HydratedRouter } from "react-router";
