import { useSelector, useDispatch } from "react-redux";
import { inc, dec, reset } from "./store.js";

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
  const value = useSelector((s) => s.counter.value);
  const dispatch = useDispatch();
  return (
    <main style={page}>
      <h1>React + Redux Toolkit</h1>
      <p>Store count: <strong>{value}</strong></p>
      <div style={{ display: "flex", gap: "0.5rem" }}>
        <button onClick={() => dispatch(dec())}>-</button>
        <button onClick={() => dispatch(inc())}>+</button>
        <button onClick={() => dispatch(reset())}>reset</button>
      </div>
    </main>
  );
}
