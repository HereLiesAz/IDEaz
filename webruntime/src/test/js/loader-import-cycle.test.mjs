/**
 * Verifies that a direct ES module import cycle degrades gracefully instead
 * of hanging the preview forever.
 *
 * ideaz-loader.js resolves imports via loadModule(inputUrl), caching the
 * in-flight Promise in `inputCache` keyed by the raw input URL string. A
 * direct two-file cycle (A imports B, B imports A) re-enters loadModule with
 * the *same* inputUrl for A while the first call is still awaiting
 * resolveModule() - before `inProgress` (keyed by the resolved realUrl) is
 * even populated. Returning the cached-but-still-pending promise in that
 * case is a deadlock: it can only settle once the very call awaiting it
 * returns, so it never does - silently, with nothing in the console and no
 * blob ever created. Only running the real loader against a real cycle
 * proves this: a mocked/stubbed loadModule would just assert away the bug.
 *
 * Run: node webruntime/src/test/js/loader-import-cycle.test.mjs
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

// --- Fake project: a direct two-file import cycle, no JSX needed --------------
const FILES = {
    "/src/moduleA.js": "import './moduleB.js';\nexport const a = 1;\n",
    "/src/moduleB.js": "import './moduleA.js';\nexport const b = 2;\n",
};

const logs = [];
const sandbox = {
    console: {
        log: (...a) => logs.push(["log", a.join(" ")]),
        error: (...a) => logs.push(["error", a.join(" ")]),
    },
    setTimeout,
    clearTimeout,
    URL,
    fetch: async (url) => {
        const path = new URL(url).pathname;
        const text = FILES[path];
        if (text === undefined) return { ok: false, status: 404 };
        return { ok: true, text: async () => text };
    },
    // Minimal Blob/createObjectURL: the test never dereferences the blob:
    // URLs it gets back (Node has no fetch handler for blob: anyway), it
    // only needs each call to produce a distinct, harmless string.
    Blob: function (parts, opts) { this.parts = parts; this.opts = opts; },
};
let blobCounter = 0;
sandbox.URL.createObjectURL = () => `blob:mock-${blobCounter++}`;

let entryDone = false;
sandbox.document = {
    readyState: "complete",
    baseURI: "http://ideaz.local/",
    querySelectorAll: () => [{ getAttribute: (name) => (name === "src" ? "/src/moduleA.js" : null) }],
    addEventListener: () => {},
};
// bootEntry's final `await import(blobUrl)` will throw in plain Node (no
// loader for blob: URLs) - that's fine and expected, its own try/catch logs
// it. Watching for that log line is how we detect boot() actually finished
// running (as opposed to the pre-fix deadlock, which never reaches it).
const originalError = sandbox.console.error;
sandbox.console.error = (...a) => {
    const msg = a.join(" ");
    if (msg.indexOf("Failed to load entry module") !== -1) entryDone = true;
    originalError(...a);
};

sandbox.window = sandbox;
sandbox.globalThis = sandbox;
sandbox.self = sandbox;
vm.createContext(sandbox);
vm.runInContext(read("react.umd.js"), sandbox, { filename: "react.umd.js" });
vm.runInContext(read("babel.min.js"), sandbox, { filename: "babel.min.js" });
vm.runInContext(read("ideaz-loader.js"), sandbox, { filename: "ideaz-loader.js" });

// Every promise in the fix's path resolves synchronously off mocked fetch/
// Babel calls with no real I/O, so this is generous, not a race.
await new Promise((r) => setTimeout(r, 1000));

console.log("import cycle handling");
check(
    "boot() completed instead of hanging (entry module's own load-failure was logged)",
    entryDone,
    "if this never fires, loadModule deadlocked on the cycle and boot() never finished - the exact pre-fix bug.",
);
check(
    "an import cycle was detected and logged",
    logs.some(([level, msg]) => level === "error" && msg.indexOf("Import cycle detected") !== -1),
    JSON.stringify(logs),
);

console.log(failures === 0 ? "\nPASS" : `\n${failures} FAILURE(S)`);
process.exit(failures === 0 ? 0 : 1);
