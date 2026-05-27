import { create } from "zustand";

// A tiny global store. Move this to its own `src/store.js` as the app grows.
const useCounter = create((set) => ({
  count: 0,
  inc: () => set((s) => ({ count: s.count + 1 })),
  dec: () => set((s) => ({ count: s.count - 1 })),
  reset: () => set({ count: 0 }),
}));

const page = {
  minHeight: "100dvh",
  display: "flex",
  flexDirection: "column",
  alignItems: "center",
  justifyContent: "center",
  gap: "1rem",
  fontFamily: "system-ui, -apple-system, sans-serif",
};

export default function App() {
  const { count, inc, dec, reset } = useCounter();
  return (
    <main style={page}>
      <h1>React + Zustand</h1>
      <p>Global state count: <strong>{count}</strong></p>
      <div style={{ display: "flex", gap: "0.5rem" }}>
        <button onClick={dec}>-</button>
        <button onClick={inc}>+</button>
        <button onClick={reset}>reset</button>
      </div>
    </main>
  );
}
