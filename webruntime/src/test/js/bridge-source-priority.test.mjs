/**
 * Verifies sourceOf() in ideaz-bridge.js prefers the React fiber's own
 * per-element _debugSource over a data-ideaz-source attribute on an
 * ancestor, as its own docstring claims ("falls back to a data-ideaz-source
 * attribute"). It used to check data-ideaz-source first and return
 * immediately - node.closest() walks every ancestor, so one
 * data-ideaz-source near the app root silently answered every tap anywhere
 * beneath it with the same stale location, discarding the real per-element
 * source that was sitting right there on the fiber.
 *
 * ideaz-bridge.js is a self-invoking IIFE (loaded via evaluateJavascript,
 * not a module) that exposes nothing for a test to import - it isn't meant
 * to. The three functions under test (reactFiberSourceOf, declaredSourceOf,
 * sourceOf) are pure DOM/fiber traversal with no other dependency on the
 * rest of the file or on a real browser, so this extracts their exact
 * source text from the shipped file and evaluates it directly, rather than
 * adding a test-only export to production code.
 *
 * Run: node webruntime/src/test/js/bridge-source-priority.test.mjs
 */
import { readFileSync } from "node:fs";
import { dirname, resolve } from "node:path";
import { fileURLToPath } from "node:url";

const BRIDGE_PATH = resolve(dirname(fileURLToPath(import.meta.url)), "../../../../app/src/androidMain/assets/ideaz-bridge.js");
const source = readFileSync(BRIDGE_PATH, "utf8");

let failures = 0;
function check(label, condition, detail) {
    if (condition) {
        console.log(`  ok   ${label}`);
    } else {
        failures++;
        console.log(`  FAIL ${label}${detail ? `\n       ${detail}` : ""}`);
    }
}

const start = source.indexOf("function reactFiberSourceOf");
const end = source.indexOf("\n    }\n", source.indexOf("function sourceOf", start)) + "\n    }\n".length;
check("found reactFiberSourceOf/declaredSourceOf/sourceOf in ideaz-bridge.js", start !== -1 && end > start);
const body = source.slice(start, end);

const { reactFiberSourceOf, declaredSourceOf, sourceOf } = new Function(`${body}\nreturn { reactFiberSourceOf, declaredSourceOf, sourceOf };`)();

// --- Minimal fake DOM: a node with an ancestor chain, closest(), and an
// attached fake React fiber carrying its own _debugSource. -------------------
function makeNode({ parent = null, attrs = {}, fiber = null } = {}) {
    const node = {
        parentElement: parent,
        getAttribute(name) { return attrs[name] ?? null; },
        closest(selector) {
            const attr = /\[([^\]]+)\]/.exec(selector)[1];
            let el = node;
            while (el) {
                if (el.getAttribute(attr) != null) return el;
                el = el.parentElement;
            }
            return null;
        },
    };
    if (fiber) node["__reactFiber$abc"] = fiber;
    return node;
}

console.log("both a React fiber source and an ancestor data-ideaz-source are present");
const root = makeNode({ attrs: { "data-ideaz-source": "stale/root.jsx:1" } });
const preciseFiber = { _debugSource: { fileName: "/src/Exact.jsx", lineNumber: 42, columnNumber: 3 } };
const tapped = makeNode({ parent: root, fiber: preciseFiber });
const result = sourceOf(tapped);
check(
    "sourceOf prefers the precise per-element fiber source over the ancestor's data-ideaz-source",
    result?.fileName === "/src/Exact.jsx" && result?.lineNumber === 42,
    `got ${JSON.stringify(result)}`,
);

console.log("\nonly an ancestor data-ideaz-source is present (real fallback case)");
const declaredOnly = makeNode({ parent: makeNode({ attrs: { "data-ideaz-source": "src/Legacy.jsx:7:2" } }) });
const fallback = sourceOf(declaredOnly);
check(
    "sourceOf falls back to data-ideaz-source when no fiber source exists",
    fallback?.fileName === "src/Legacy.jsx" && fallback?.lineNumber === 7 && fallback?.columnNumber === 2,
    `got ${JSON.stringify(fallback)}`,
);

console.log("\nneither is present");
check("sourceOf returns null", sourceOf(makeNode()) === null);

console.log(failures === 0 ? "\nPASS" : `\n${failures} FAILURE(S)`);
process.exit(failures === 0 ? 0 : 1);
