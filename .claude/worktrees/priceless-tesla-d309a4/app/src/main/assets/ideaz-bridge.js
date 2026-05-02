// app/src/main/assets/ideaz-bridge.js
// Loaded by WebProjectHost.onPageFinished via evaluateJavascript.
// Idempotent: guarded by __ideazBridgeLoaded flag.
(function () {
    'use strict';
    if (window.__ideazBridgeLoaded) { return; }
    window.__ideazBridgeLoaded = true;

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
