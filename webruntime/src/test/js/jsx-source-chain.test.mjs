/**
 * Verifies the element -> source chain that the whole product rests on.
 *
 * IDEaz's premise is that tapping a rendered element hands the AI
 * `src/App.jsx:42` instead of a CSS selector. That answer is produced by a
 * four-link chain, and every link lives in a different file:
 *
 *   1. ideaz-loader.js  transpiles with { runtime: "automatic", development: true },
 *                       which turns on @babel/plugin-transform-react-jsx-source
 *   2. Babel            emits jsxDEV(type, props, key, isStaticChildren, source, self)
 *   3. jsx-runtime.js   forwards `source` as config.__source
 *   4. React            lifts config.__source onto element._source, and react-dom
 *                       copies that to fiber._debugSource, which ideaz-bridge.js reads
 *
 * Link 3 was broken from the day it was written: the shim declared
 * `jsxImpl(type, props, key)` and dropped the rest. Nothing crashed, nothing
 * warned, and source resolution silently returned null forever. Only an
 * end-to-end assertion catches that class of break, so this test runs the real
 * vendored Babel over real JSX, feeds the real emitted call into the real shim,
 * and reads _source off the real React element. No mocks anywhere in the chain.
 *
 * Run: node webruntime/src/test/js/jsx-source-chain.test.mjs
 */
import { readFileSync } from "node:fs";
import { dirname, resolve } from "node:path";
import { fileURLToPath } from "node:url";
import vm from "node:vm";

const RUNTIME = resolve(dirname(fileURLToPath(import.meta.url)), "../../main/assets/ideaz-runtime");
const read = (name) => readFileSync(resolve(RUNTIME, name), "utf8");

let failures = 0;
function check(label, condition, detail) {
    if (condition) {
        console.log(`  ok   ${label}`);
    } else {
        failures++;
        console.log(`  FAIL ${label}${detail ? `\n       ${detail}` : ""}`);
    }
}
const eq = (label, actual, expected) =>
    check(label, Object.is(actual, expected), `expected ${JSON.stringify(expected)}, got ${JSON.stringify(actual)}`);

// --- Link 1: the loader still asks Babel for source metadata ------------------
// The shim can forward __source perfectly and still resolve nothing if the
// loader stops requesting it, so assert the flag at its source of truth rather
// than duplicating the config and hoping the two stay in step.
const loader = read("ideaz-loader.js");
console.log("loader config");
check(
    'ideaz-loader.js requests { runtime: "automatic", development: true }',
    /\[\s*"react"\s*,\s*\{\s*runtime:\s*"automatic",\s*development:\s*true\s*\}\s*\]/.test(loader),
    "development:true is what enables @babel/plugin-transform-react-jsx-source. Without it nothing downstream has a file or line to forward.",
);

// --- Load the vendored globals the WebView would provide ---------------------
// react.umd.js and babel.min.js are UMD bundles that publish onto `window`.
// A vm context with window aliased to the global object is the smallest thing
// that satisfies them, and it keeps the *shipped* bytes under test.
const sandbox = { console, process, setTimeout, clearTimeout, URL, Blob: class {} };
sandbox.window = sandbox;
sandbox.globalThis = sandbox;
sandbox.self = sandbox;
vm.createContext(sandbox);
vm.runInContext(read("react.umd.js"), sandbox, { filename: "react.umd.js" });
vm.runInContext(read("babel.min.js"), sandbox, { filename: "babel.min.js" });

console.log("\nvendored runtime");
check("react.umd.js publishes window.React", !!sandbox.React);
eq("React version", sandbox.React.version, "18.3.1");
check("babel.min.js publishes window.Babel", !!sandbox.Babel);

// --- Link 3: load the shim under test, as a module, with window.React present -
// The file is ESM but lives outside any package.json, so Node would parse it as
// CommonJS if imported by path. A data: URL import runs the exact same bytes as
// a module. globalThis.window is what the shim reads on its first line.
globalThis.window = { React: sandbox.React };
const shim = await import(`data:text/javascript,${encodeURIComponent(read("jsx-runtime.js"))}`);

console.log("\njsx-runtime.js shim");
check("exports jsx, jsxs, jsxDEV and Fragment",
    !!(shim.jsx && shim.jsxs && shim.jsxDEV && shim.Fragment));
eq("jsxDEV accepts all six arguments Babel passes", shim.jsxDEV.length, 6);

// --- Links 2+3+4 end to end --------------------------------------------------
const SOURCE_FILE = "/src/App.jsx";
const JSX = `
export default function App() {
  return (
    <div className="app">
      <button onClick={() => {}}>Click</button>
    </div>
  );
}
`;

const presets = [["react", { runtime: "automatic", development: true }]];
const transpiled = sandbox.Babel.transform(JSX, {
    filename: SOURCE_FILE,
    sourceType: "module",
    presets,
}).code;

console.log("\nbabel output");
check("emits jsxDEV calls", /jsxDEV\(/.test(transpiled), transpiled.slice(0, 400));
check(`stamps fileName ${SOURCE_FILE}`, transpiled.includes(SOURCE_FILE), transpiled.slice(0, 400));
check("imports react/jsx-dev-runtime (the specifier the import map points at this shim)",
    /["']react\/jsx-dev-runtime["']/.test(transpiled), transpiled.slice(0, 400));

// Run the transpiled module for real, resolving its one import to the shim.
const cjs = sandbox.Babel.transform(transpiled, {
    filename: SOURCE_FILE,
    sourceType: "module",
    plugins: ["transform-modules-commonjs"],
}).code;
const moduleExports = {};
const requireShim = (spec) => {
    if (spec === "react/jsx-dev-runtime" || spec === "react/jsx-runtime") return shim;
    throw new Error(`unexpected import: ${spec}`);
};
new Function("require", "exports", "module", cjs)(requireShim, moduleExports, { exports: moduleExports });
const element = moduleExports.default();

console.log("\nrendered element");
check("App() returned a React element", sandbox.React.isValidElement(element));
check("element._source is populated — this is the assertion the product rests on",
    !!element._source,
    "null here means a tap resolves to no file: the AI gets a selector and has to grep.");
eq("_source.fileName", element._source?.fileName, SOURCE_FILE);
eq("_source.lineNumber", element._source?.lineNumber, 4);
check("_source.columnNumber is a number", typeof element._source?.columnNumber === "number");

const child = sandbox.React.Children.toArray(element.props.children)[0];
console.log("\nnested child element");
check("child element also carries _source", !!child?._source);
eq("child _source.lineNumber", child?._source?.lineNumber, 5);
eq("child _source.fileName", child?._source?.fileName, SOURCE_FILE);

// --- The production path must keep working ----------------------------------
// jsx/jsxs get three arguments and no source. Forwarding must not turn the
// undefined tail into __source: undefined props on the element.
console.log("\nproduction jsx path");
const prod = shim.jsx("span", { id: "x", children: "hi" });
check("jsx() still builds an element", sandbox.React.isValidElement(prod));
eq("jsx() props.id survives", prod.props.id, "x");
eq("jsx() children survive", prod.props.children, "hi");
eq("jsx() leaves _source null", prod._source, null);
eq("key is absent when not passed", prod.key, null);

const multi = shim.jsxs("ul", { children: [shim.jsx("li", { children: "a" }), shim.jsx("li", { children: "b" })] });
eq("jsxs() spreads array children", sandbox.React.Children.count(multi.props.children), 2);

console.log(failures === 0 ? "\nPASS" : `\n${failures} FAILURE(S)`);
process.exit(failures === 0 ? 0 : 1);
