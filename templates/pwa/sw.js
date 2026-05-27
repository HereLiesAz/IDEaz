// Bump CACHE when the app shell changes to invalidate old caches.
const CACHE = "pwa-starter-v1";
const SHELL = [
    "/",
    "/index.html",
    "/styles.css",
    "/app.js",
    "/manifest.webmanifest",
    "/icon.svg",
];

self.addEventListener("install", (event) => {
    event.waitUntil(caches.open(CACHE).then((c) => c.addAll(SHELL)));
    self.skipWaiting();
});

self.addEventListener("activate", (event) => {
    event.waitUntil(
        caches.keys().then((keys) =>
            Promise.all(keys.filter((k) => k !== CACHE).map((k) => caches.delete(k)))
        )
    );
    self.clients.claim();
});

// Network-first for navigations (fresh content when online, cached shell when
// offline); cache-first for other assets.
self.addEventListener("fetch", (event) => {
    const req = event.request;
    if (req.mode === "navigate") {
        event.respondWith(
            fetch(req).catch(() => caches.match("/index.html"))
        );
        return;
    }
    event.respondWith(
        caches.match(req).then((cached) => cached || fetch(req))
    );
});
