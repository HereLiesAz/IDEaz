const status = document.getElementById("status");

if ("serviceWorker" in navigator) {
    window.addEventListener("load", () => {
        navigator.serviceWorker.register("/sw.js")
            .then((reg) => {
                status.textContent = "Service worker registered (scope: " + reg.scope + ").";
            })
            .catch((err) => {
                status.textContent = "Service worker registration failed: " + err;
            });
    });
} else {
    status.textContent = "Service workers are not supported in this context.";
}
