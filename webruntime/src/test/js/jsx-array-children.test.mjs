/**
 * Verifies jsxs()'s array-children handling matches real React's shape,
 * against the real vendored React (whose dev build freezes element.props,
 * so a naive "build then mutate props.children" fix would throw here even
 * though it would look correct against a shallow mock).
 *
 * Run: node webruntime/src/test/js/jsx-array-children.test.mjs
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

const sandbox = { console, process, setTimeout, clearTimeout, URL, Blob: class {} };
sandbox.window = sandbox;
sandbox.globalThis = sandbox;
sandbox.self = sandbox;
vm.createContext(sandbox);
vm.runInContext(read("react.umd.js"), sandbox, { filename: "react.umd.js" });

globalThis.window = { React: sandbox.React };
const shim = await import(`data:text/javascript,${encodeURIComponent(read("jsx-runtime.js"))}`);
const React = sandbox.React;

console.log("empty array children");
let result;
try {
    result = shim.jsxs("ul", { children: [] });
} catch (e) {
    check("jsxs(el, { children: [] }) does not throw", false, e.stack || String(e));
}
if (result) {
    check("element built successfully", React.isValidElement(result));
    check(
        "props.children is [] , not undefined - a real-but-empty list must stay distinguishable from no children at all",
        Array.isArray(result.props.children) && result.props.children.length === 0,
        `got ${JSON.stringify(result.props.children)}`,
    );
}

console.log("\nnon-empty array children");
const items = [React.createElement("li", { key: "a" }, "a"), React.createElement("li", { key: "b" }, "b")];
const list = shim.jsxs("ul", { children: items });
check("element built successfully", React.isValidElement(list));
check("props.children is the exact same array reference real React would keep", list.props.children === items);
check("count matches", React.Children.count(list.props.children), items.length === React.Children.count(list.props.children));
check("order preserved", list.props.children[0] === items[0] && list.props.children[1] === items[1]);

console.log("\nsingle (non-array) child still works");
const single = shim.jsx("span", { children: "hi" });
check("props.children is the scalar, unwrapped", single.props.children === "hi");

console.log("\nno children still works");
const empty = shim.jsx("br", {});
check("props.children is undefined for no children at all", empty.props.children === undefined);

console.log(failures === 0 ? "\nPASS" : `\n${failures} FAILURE(S)`);
process.exit(failures === 0 ? 0 : 1);
