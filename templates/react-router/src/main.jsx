import { StrictMode } from "react";
import { createRoot } from "react-dom/client";
import { HashRouter } from "react-router-dom";
import App from "./App.jsx";

// HashRouter keeps routing entirely client-side, so it works in the IDEaz
// preview and on static hosts (GitHub Pages) with no server rewrite rules.
createRoot(document.getElementById("root")).render(
  <StrictMode>
    <HashRouter>
      <App />
    </HashRouter>
  </StrictMode>
);
