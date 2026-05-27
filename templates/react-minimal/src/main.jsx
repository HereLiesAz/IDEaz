import { StrictMode, useState } from "react";
import { createRoot } from "react-dom/client";

const center = {
  minHeight: "100dvh",
  display: "flex",
  flexDirection: "column",
  alignItems: "center",
  justifyContent: "center",
  gap: "1rem",
  fontFamily: "system-ui, -apple-system, sans-serif",
};

function App() {
  const [count, setCount] = useState(0);
  return (
    <main style={center}>
      <h1>React Minimal</h1>
      <p>
        Edit <code>src/main.jsx</code> to begin.
      </p>
      <button onClick={() => setCount((c) => c + 1)}>count is {count}</button>
    </main>
  );
}

createRoot(document.getElementById("root")).render(
  <StrictMode>
    <App />
  </StrictMode>
);
