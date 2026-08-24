// app/src/main/assets/ideaz-bridge.js
// Loaded by WebProjectHost.onPageFinished via evaluateJavascript.
// Idempotent: guarded by __ideazBridgeLoaded flag.
(function () {
    'use strict';
    if (window.__ideazBridgeLoaded) { return; }
    window.__ideazBridgeLoaded = true;

    /**
     * The source location that produced this element: { fileName, lineNumber,
     * columnNumber }, or null.
     *
     * This is the payoff of enabling Babel's jsx-source transform in
     * ideaz-loader.js. React stores the __source prop the transform emits on the
     * fiber as `_debugSource`, reachable through the DOM node's internal
     * `__reactFiber$<id>` key. We walk up the fiber tree because the tapped DOM
     * node is often rendered by a host element inside a component - the nearest
     * ancestor carrying a _debugSource is the JSX the user actually means.
     *
     * Falls back to a `data-ideaz-source` attribute so non-React projects (and
     * anything that wants to opt in) can supply the same information themselves.
     *
     * Returns null when nothing is available; the AI is told in its system
     * preamble to fall back to the selector in that case.
     */
    function reactFiberSourceOf(node) {
        var el = node;
        var hops = 0;
        while (el && hops < 25) {
            var key = null;
            for (var k in el) {
                if (k.indexOf('__reactFiber$') === 0) { key = k; break; }
            }
            if (key) {
                var fiber = el[key];
                var guard = 0;
                while (fiber && guard < 25) {
                    var src = fiber._debugSource ||
                        (fiber.memoizedProps && fiber.memoizedProps.__source);
                    if (src && src.fileName) {
                        return {
                            fileName: src.fileName,
                            lineNumber: src.lineNumber || 0,
                            columnNumber: src.columnNumber || 0
                        };
                    }
                    fiber = fiber._debugOwner || fiber.return;
                    guard++;
                }
            }
            el = el.parentElement;
            hops++;
        }
        return null;
    }

    function declaredSourceOf(node) {
        var declared = node.closest && node.closest('[data-ideaz-source]');
        if (!declared) return null;
        var raw = declared.getAttribute('data-ideaz-source') || '';
        var m = /^(.*?):(\d+)(?::(\d+))?$/.exec(raw);
        if (m) {
            return {
                fileName: m[1],
                lineNumber: parseInt(m[2], 10),
                columnNumber: m[3] ? parseInt(m[3], 10) : 0
            };
        }
        if (raw) return { fileName: raw, lineNumber: 0, columnNumber: 0 };
        return null;
    }

    function sourceOf(node) {
        // React's own per-element _debugSource is exact - it names the JSX
        // that produced *this* element, not whatever ancestor happens to
        // carry a data-ideaz-source attribute. data-ideaz-source is a
        // genuine fallback for when that isn't available (non-React
        // projects, or a project that opts into it deliberately); checking
        // it first made it a hard override instead - node.closest() walks
        // every ancestor, so a single data-ideaz-source near the app root
        // silently answered every tap anywhere beneath it with the same
        // stale location, discarding the accurate per-element source React
        // had right there on the fiber.
        return reactFiberSourceOf(node) || declaredSourceOf(node);
    }

    /**
     * Builds a minimal CSS selector path from the nearest ancestor-with-id
     * down to `node`. Falls back to a full tag-chain if no id ancestor exists.
     * @param {Element} node
     * @returns {string}
     */
    function buildSelector(node) {
        var parts = [];
        var current = node;
        while (current && current.nodeType === 1 && current !== document.body) {
            var tag = current.tagName.toLowerCase();
            if (current.id) {
                parts.unshift(tag + '#' + current.id);
                break; // id is unique; stop walking up
            }
            var siblings = current.parentNode
                ? Array.prototype.filter.call(
                      current.parentNode.children,
                      function (c) { return c.tagName === current.tagName; }
                  )
                : [];
            var idx = siblings.indexOf(current);
            if (siblings.length > 1 && idx > -1) {
                tag += ':nth-of-type(' + (idx + 1) + ')';
            }
            parts.unshift(tag);
            current = current.parentElement;
        }
        return parts.join(' > ');
    }

    /** CSS properties we capture: [camelCase key for JSON, hyphenated name for getPropertyValue]. */
    var CSS_PROPS = [
        ['color',           'color'],
        ['backgroundColor', 'background-color'],
        ['fontSize',        'font-size'],
        ['fontFamily',      'font-family'],
        ['display',         'display'],
        ['position',        'position'],
        ['width',           'width'],
        ['height',          'height'],
        ['margin',          'margin'],
        ['padding',         'padding']
    ];

    window.ideaz = {
        /**
         * Toggle inspect-mode cursor in the web page.
         * Called by WebProjectHost via evaluateJavascript when selectMode changes.
         * @param {boolean} on
         */
        selectMode: function (on) {
            if (document.body) {
                document.body.style.cursor = on ? 'crosshair' : '';
            }
        },

        /**
         * Collect rich context for the element at the tapped point.
         * Called by the INSPECT_WEB handler in WebProjectHost.
         * @param {Element} el  The element returned by document.elementFromPoint.
         * @returns {Object}    Plain object — caller JSON.stringifies this.
         */
        getElementContext: function (el) {
            if (!el) { return null; }

            // Computed styles (best-effort)
            var styles = {};
            try {
                var cs = window.getComputedStyle(el);
                for (var i = 0; i < CSS_PROPS.length; i++) {
                    styles[CSS_PROPS[i][0]] = cs.getPropertyValue(CSS_PROPS[i][1]);
                }
            } catch (ignore) {}

            // Bounding rect in CSS pixels (viewport-relative)
            var rect = el.getBoundingClientRect();

            // Up to 3 ancestor elements (skip body/html)
            var parents = [];
            var p = el.parentElement;
            while (p && p !== document.body && parents.length < 3) {
                parents.push({
                    tagName: p.tagName.toLowerCase(),
                    id: p.id || '',
                    className: typeof p.className === 'string' ? p.className : ''
                });
                p = p.parentElement;
            }

            return {
                tagName: el.tagName.toLowerCase(),
                id: el.id || '',
                className: typeof el.className === 'string' ? el.className : '',
                selector: buildSelector(el),
                // The file:line that produced this element, when we can find it.
                // This is the single most useful field in the payload - see
                // sourceOf() above.
                source: sourceOf(el),
                // NOTE: outerHtml is passed to Kotlin as a raw string; consumers must not render it as HTML.
                outerHtml: el.outerHTML ? el.outerHTML.substring(0, 2000) : '',
                innerText: el.innerText ? el.innerText.substring(0, 500) : '',
                computedStyles: styles,
                boundingRect: {
                    top: rect.top,
                    left: rect.left,
                    bottom: rect.bottom,
                    right: rect.right,
                    width: rect.width,
                    height: rect.height
                },
                parents: parents
            };
        }
    };
})();
