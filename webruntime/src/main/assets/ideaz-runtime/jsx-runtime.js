// ESM shim for `react/jsx-runtime` (the automatic JSX runtime Babel targets).
// Implemented on top of React.createElement from the UMD build (window.React),
// so we don't need a separate jsx-runtime bundle. Also serves
// `react/jsx-dev-runtime` (mapped to this file by the import map).
const React = window.React;
if (!React) {
    throw new Error("[ideaz] window.React is undefined — react.umd.js failed to load.");
}

export const Fragment = React.Fragment;

/**
 * @param source  For jsxDEV only: { fileName, lineNumber, columnNumber }, emitted
 *                by @babel/plugin-transform-react-jsx-source. Forwarded as
 *                `__source`, which React 18's createElement lifts to
 *                `element._source`, which react-dom then records on the fiber as
 *                `_debugSource`. That is the chain ideaz-bridge.js reads to answer
 *                "which file and line produced the element the user just tapped" -
 *                the whole reason IDEaz can edit the right file.
 *
 *                This used to be declared `jsxImpl(type, props, key)` with a
 *                comment saying the extra arguments were ignored. They were, so
 *                enabling Babel's jsx-source transform produced metadata that this
 *                function silently dropped one call later: no crash, no warning,
 *                and source resolution that always returned null.
 * @param self    For jsxDEV only; forwarded as `__self` for React's own warnings.
 */
function jsxImpl(type, props, key, isStaticChildren, source, self) {
    const config = Object.assign({}, props);
    delete config.children;
    if (key !== undefined) config.key = key;
    if (source !== undefined) config.__source = source;
    if (self !== undefined) config.__self = self;
    const children = props ? props.children : undefined;
    if (children === undefined) {
        return React.createElement(type, config);
    }
    if (Array.isArray(children)) {
        return React.createElement(type, config, ...children);
    }
    return React.createElement(type, config, children);
}

export const jsx = jsxImpl;
export const jsxs = jsxImpl;
// jsxDEV(type, props, key, isStaticChildren, source, self)
export const jsxDEV = jsxImpl;
