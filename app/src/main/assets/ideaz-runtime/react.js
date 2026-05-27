// ESM shim for `react`. The real implementation is the UMD build loaded as a
// classic <script> in <head> (sets window.React). This module re-exports it so
// `import React, { useState } from 'react'` resolves via the injected import map.
const React = window.React;
if (!React) {
    throw new Error("[ideaz] window.React is undefined — react.umd.js failed to load.");
}
export default React;
export const {
    Children,
    Component,
    Fragment,
    Profiler,
    PureComponent,
    StrictMode,
    Suspense,
    cloneElement,
    createContext,
    createElement,
    createFactory,
    createRef,
    forwardRef,
    isValidElement,
    lazy,
    memo,
    startTransition,
    useCallback,
    useContext,
    useDebugValue,
    useDeferredValue,
    useEffect,
    useId,
    useImperativeHandle,
    useInsertionEffect,
    useLayoutEffect,
    useMemo,
    useReducer,
    useRef,
    useState,
    useSyncExternalStore,
    useTransition,
    version,
} = React;
