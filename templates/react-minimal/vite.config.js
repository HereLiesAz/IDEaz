import { defineConfig } from "vite";
import react from "@vitejs/plugin-react";

// base: "./" emits relative asset paths so the build works at any GitHub Pages
// path (user or project site) without further configuration.
export default defineConfig({
  base: "./",
  plugins: [react()],
});
