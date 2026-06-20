// ideaz-loader.js — minimal in-browser module loader for IDEaz web previews.
//
// IDEaz has no on-device JS bundler, so Vite/React *source* projects (raw .jsx /
// .tsx that reference bare specifiers like `react`) cannot run natively in the
// WebView. This loader fills that gap at runtime:
//   1. WebProjectPathHandler injects an import map (bare specifiers -> vendored
//      React shims), the Babel standalone bundle, and this script into index.html,
//      and rewrites the project's <script type="module"> tags to
//      type="ideaz-module" so the browser does NOT try to execute raw JSX.
//   2. On DOMContentLoaded this loader walks each entry module, transpiles JSX/TS
//      via Babel, rewrites relative imports to blob: URLs (recursively), and
//      imports the resulting blob module. Bare specifiers are left for the import
//      map to resolve.
//
// Supported: .jsx/.tsx/.ts/.js/.mjs, relative + root-absolute imports, bare React
// imports, extensionless imports (probes .jsx/.tsx/.ts/.js + /index.*), and
// `import './x.css'` / `.json` / image-asset imports.
// Not supported (documented limitations): Vite-only features (import.meta.env,
// /@vite/client HMR, glob/?raw/?url imports) and ES module import cycles.
(function () {
    "use strict";

    var RUNTIME_PREFIX = "/__ideaz__/";
    var CODE_EXTS = ["jsx", "tsx", "ts", "js", "mjs", "cjs"];
    var ASSET_EXTS = ["png", "jpg", "jpeg", "gif", "webp", "svg", "ico", "bmp",
        "avif", "woff", "woff2", "ttf", "otf", "eot", "mp3", "mp4", "webm", "wav"];

    var inputCache = new Map();    // input specifier URL -> Promise<blobUrl>
    var resolvedCache = new Map(); // resolved real URL   -> Promise<blobUrl>
    var inProgress = new Set();    // real URLs currently being built (cycle guard)
    var emptyBlobUrl = null;

    function err(msg, e) {
        console.error("[ideaz] " + msg + (e ? (": " + (e && e.stack ? e.stack : e)) : ""));
    }

    function blobUrlFor(js) {
        return URL.createObjectURL(new Blob([js], { type: "text/javascript" }));
    }

    function emptyModule() {
        if (!emptyBlobUrl) emptyBlobUrl = blobUrlFor("export {};");
        return emptyBlobUrl;
    }

    function pathnameOf(href) {
        try { return new URL(href).pathname; } catch (e) { return href; }
    }

    function extOf(href) {
        var p = pathnameOf(href);
        var slash = p.lastIndexOf("/");
        var name = slash >= 0 ? p.slice(slash + 1) : p;
        var dot = name.lastIndexOf(".");
        return dot > 0 ? name.slice(dot + 1).toLowerCase() : "";
    }

    // A specifier we should resolve locally (vs. a bare specifier left to the
    // import map, or an absolute/external URL).
    function isLocalSpec(s) {
        if (!s) return false;
        if (s.indexOf("://") !== -1) return false;         // http(s):, blob:, data:
        if (s.indexOf("//") === 0) return false;            // protocol-relative
        if (s.charAt(0) === ".") return true;               // ./ ../
        if (s.charAt(0) === "/" && s.indexOf("/@") !== 0) return true; // /src/... (not /@vite)
        return false;                                       // bare: react, lodash, ...
    }

    async function fetchText(url) {
        var r = await fetch(url);
        if (!r.ok) throw new Error("HTTP " + r.status + " for " + url);
        return r.text();
    }

    // Resolve an input URL to a concrete file: returns { realUrl, kind, text }.
    async function resolveModule(inputUrl) {
        var ext = extOf(inputUrl);
        if (ext && ASSET_EXTS.indexOf(ext) !== -1) {
            return { realUrl: inputUrl, kind: "asset" };
        }
        if (ext === "css") {
            return { realUrl: inputUrl, kind: "css", text: await fetchText(inputUrl) };
        }
        if (ext === "json") {
            return { realUrl: inputUrl, kind: "json", text: await fetchText(inputUrl) };
        }
        if (ext && CODE_EXTS.indexOf(ext) !== -1) {
            return { realUrl: inputUrl, kind: "code", text: await fetchText(inputUrl) };
        }
        // No / unknown extension: probe code candidates (Vite-style extensionless imports).
        var base = inputUrl.replace(/\/$/, "");
        var candidates = [inputUrl];
        CODE_EXTS.forEach(function (e) { candidates.push(base + "." + e); });
        CODE_EXTS.forEach(function (e) { candidates.push(base + "/index." + e); });
        for (var i = 0; i < candidates.length; i++) {
            var r = await fetch(candidates[i]);
            if (r.ok) return { realUrl: candidates[i], kind: "code", text: await r.text() };
        }
        throw new Error("Cannot resolve module: " + inputUrl);
    }

    function reactPresets(filename) {
        var presets = [["react", { runtime: "automatic", development: false }]];
        var ext = extOf(filename);
        if (ext === "ts" || ext === "tsx") presets.push("typescript");
        return presets;
    }

    // Babel plugin: collect every static/dynamic import source string.
    function collectPlugin(sink) {
        return function () {
            function take(p) { if (p.node.source) sink.push(p.node.source.value); }
            return {
                visitor: {
                    ImportDeclaration: take,
                    ExportNamedDeclaration: take,
                    ExportAllDeclaration: take,
                    CallExpression: function (p) {
                        var n = p.node;
                        if (n.callee && n.callee.type === "Import" &&
                            n.arguments[0] && n.arguments[0].type === "StringLiteral") {
                            sink.push(n.arguments[0].value);
                        }
                    },
                },
            };
        };
    }

    // Babel plugin: rewrite collected sources using a specifier -> blobUrl map.
    function rewritePlugin(map) {
        return function () {
            function swap(p) {
                var s = p.node.source;
                if (s && Object.prototype.hasOwnProperty.call(map, s.value)) s.value = map[s.value];
            }
            return {
                visitor: {
                    ImportDeclaration: swap,
                    ExportNamedDeclaration: swap,
                    ExportAllDeclaration: swap,
                    CallExpression: function (p) {
                        var n = p.node;
                        if (n.callee && n.callee.type === "Import" &&
                            n.arguments[0] && n.arguments[0].type === "StringLiteral") {
                            var v = n.arguments[0].value;
                            if (Object.prototype.hasOwnProperty.call(map, v)) n.arguments[0].value = map[v];
                        }
                    },
                },
            };
        };
    }

    // Transpile code, resolve its relative imports to blob URLs, return final JS.
    async function processCode(text, baseUrl, filename) {
        var sources = [];
        var pass1 = Babel.transform(text, {
            filename: filename,
            sourceType: "module",
            presets: reactPresets(filename),
            plugins: [collectPlugin(sources)],
        });

        var map = {};
        var seen = {};
        for (var i = 0; i < sources.length; i++) {
            var spec = sources[i];
            if (seen[spec] || !isLocalSpec(spec)) continue;
            seen[spec] = true;
            var childUrl = new URL(spec, baseUrl).href;
            map[spec] = await loadModule(childUrl);
        }

        if (Object.keys(map).length === 0) return pass1.code;

        var pass2 = Babel.transform(pass1.code, {
            filename: filename,
            sourceType: "module",
            plugins: [rewritePlugin(map)],
        });
        return pass2.code;
    }

    function cssModule(text, url) {
        return "const css = " + JSON.stringify(text) + ";\n" +
            "const el = document.createElement('style');\n" +
            "el.setAttribute('data-ideaz-href', " + JSON.stringify(url) + ");\n" +
            "el.textContent = css;\n" +
            "document.head.appendChild(el);\n" +
            "export default css;\n";
    }

    async function buildModule(realUrl, kind, text) {
        if (kind === "code") {
            return blobUrlFor(await processCode(text, realUrl, pathnameOf(realUrl)));
        }
        if (kind === "css") return blobUrlFor(cssModule(text, realUrl));
        if (kind === "json") return blobUrlFor("export default (" + text + ");\n");
        // asset
        return blobUrlFor("export default " + JSON.stringify(realUrl) + ";\n");
    }

    function loadModule(inputUrl) {
        if (inputCache.has(inputUrl)) return inputCache.get(inputUrl);
        var p = (async function () {
            var resolved = await resolveModule(inputUrl);
            var realUrl = resolved.realUrl;
            if (resolvedCache.has(realUrl)) return resolvedCache.get(realUrl);
            if (inProgress.has(realUrl)) {
                err("Import cycle detected at " + realUrl + " — substituting empty module.");
                return emptyModule();
            }
            inProgress.add(realUrl);
            var blobPromise = buildModule(realUrl, resolved.kind, resolved.text);
            resolvedCache.set(realUrl, blobPromise);
            try {
                return await blobPromise;
            } finally {
                inProgress.delete(realUrl);
            }
        })();
        inputCache.set(inputUrl, p);
        return p;
    }

    async function bootEntry(scriptEl) {
        var src = scriptEl.getAttribute("src");
        try {
            var blobUrl;
            if (src) {
                blobUrl = await loadModule(new URL(src, document.baseURI).href);
            } else {
                var code = scriptEl.textContent || "";
                var js = await processCode(code, document.baseURI, "inline-entry.jsx");
                blobUrl = blobUrlFor(js);
            }
            await import(blobUrl);
        } catch (e) {
            err("Failed to load entry module " + (src || "(inline)"), e);
        }
    }

    async function boot() {
        if (typeof Babel === "undefined") {
            err("Babel standalone not loaded — cannot transpile project sources.");
            return;
        }
        var entries = document.querySelectorAll('script[type="ideaz-module"]');
        for (var i = 0; i < entries.length; i++) {
            await bootEntry(entries[i]);
        }
    }

    if (document.readyState === "loading") {
        document.addEventListener("DOMContentLoaded", boot);
    } else {
        boot();
    }
})();
