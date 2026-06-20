// ESM shim for `react/jsx-runtime` (the automatic JSX runtime Babel targets).
// Implemented on top of React.createElement from the UMD build (window.React),
// so we don't need a separate jsx-runtime bundle. Also serves
// `react/jsx-dev-runtime` (mapped to this file by the import map).
const React = window.React;
if (!React) {
    throw new Error("[ideaz] window.React is undefined — react.umd.js failed to load.");
}

export const Fragment = React.Fragment;

function jsxImpl(type, props, key) {
    const config = Object.assign({}, props);
    delete config.children;
    if (key !== undefined) config.key = key;
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
// jsxDEV(type, props, key, isStaticChildren, source, self) — extra args ignored.
export const jsxDEV = jsxImpl;
