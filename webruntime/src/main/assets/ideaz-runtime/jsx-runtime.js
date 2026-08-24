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
        // Real React's jsxs never spreads the array into createElement's
        // variadic children arguments - it leaves `children` as the array it
        // already is. Spreading changed the element's shape in two provable
        // ways: an empty array (a `.map()` over an empty list, a real and
        // common case) collapses to zero variadic arguments, and
        // createElement sets props.children to `undefined` when it receives
        // none - not `[]` - which anything downstream inspecting "is this
        // position a list" sees differently. And createElement's own
        // duplicate/missing-key warnings only fire for children that arrive
        // as a single array argument, not for children spread across
        // discrete positional arguments, so spreading silently dropped those
        // warnings too.
        //
        // The natural fix - build the element, then assign the real array
        // onto element.props.children directly - throws: React's dev build
        // (react.umd.js) Object.freezes element.props before returning. So
        // set it on `config` instead: createElement's own config-copy loop
        // (everything but key/ref/__self/__source) already treats a
        // "children" property on config exactly like an explicit prop,
        // landing it on props.children with no copy and no variadic
        // collapsing to fight with, since zero children arguments are
        // passed here.
        config.children = children;
        return React.createElement(type, config);
    }
    return React.createElement(type, config, children);
}

export const jsx = jsxImpl;
export const jsxs = jsxImpl;
// jsxDEV(type, props, key, isStaticChildren, source, self)
export const jsxDEV = jsxImpl;
