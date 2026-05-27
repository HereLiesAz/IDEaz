import { useQuery } from "@tanstack/react-query";
import axios from "axios";

const page = {
  minHeight: "100dvh",
  display: "flex",
  flexDirection: "column",
  alignItems: "center",
  justifyContent: "center",
  gap: "1rem",
  fontFamily: "system-ui, -apple-system, sans-serif",
  padding: "1.5rem",
  textAlign: "center",
};

async function fetchTodo() {
  const { data } = await axios.get("https://jsonplaceholder.typicode.com/todos/1");
  return data;
}

export default function App() {
  const { data, isPending, isError, error, refetch } = useQuery({
    queryKey: ["todo", 1],
    queryFn: fetchTodo,
  });

  return (
    <main style={page}>
      <h1>React + TanStack Query</h1>
      {isPending && <p>Loading…</p>}
      {isError && <p>Error: {String(error)}</p>}
      {data && (
        <pre style={{ textAlign: "left" }}>{JSON.stringify(data, null, 2)}</pre>
      )}
      <button onClick={() => refetch()}>Refetch</button>
      <p style={{ opacity: 0.7, fontSize: "0.85rem" }}>
        Fetched with <code>axios</code>, cached by <code>@tanstack/react-query</code>.
        (Requires network.)
      </p>
    </main>
  );
}
