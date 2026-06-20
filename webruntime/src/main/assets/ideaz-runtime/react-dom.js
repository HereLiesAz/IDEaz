// ESM shim for `react-dom`. Re-exports the UMD build (window.ReactDOM).
const ReactDOM = window.ReactDOM;
if (!ReactDOM) {
    throw new Error("[ideaz] window.ReactDOM is undefined — react-dom.umd.js failed to load.");
}
export default ReactDOM;
export const {
    createPortal,
    flushSync,
    render,
    hydrate,
    findDOMNode,
    unmountComponentAtNode,
    unstable_batchedUpdates,
    version,
} = ReactDOM;
