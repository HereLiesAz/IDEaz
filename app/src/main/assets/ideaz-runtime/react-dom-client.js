// ESM shim for `react-dom/client` (React 18 root API). Re-exports from the UMD
// build (window.ReactDOM), which includes createRoot / hydrateRoot.
const ReactDOM = window.ReactDOM;
if (!ReactDOM || typeof ReactDOM.createRoot !== "function") {
    throw new Error("[ideaz] ReactDOM.createRoot is unavailable — react-dom.umd.js failed to load.");
}
export const createRoot = ReactDOM.createRoot;
export const hydrateRoot = ReactDOM.hydrateRoot;
export default { createRoot, hydrateRoot };
