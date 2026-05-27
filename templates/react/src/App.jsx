import { Routes, Route, Link } from "react-router-dom";
import { useQuery } from "@tanstack/react-query";
import axios from "axios";
import styled, { ThemeProvider, createGlobalStyle } from "styled-components";
import { useCounter } from "./store.js";

const theme = { fg: "#f5f5f5", bg: "#111111", accent: "#3b82f6" };

const GlobalStyle = createGlobalStyle`
  body { margin: 0; font-family: system-ui, -apple-system, sans-serif; }
`;

const Shell = styled.div`
  min-height: 100dvh;
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 1.5rem;
  padding: 2rem 1rem;
  color: ${(p) => p.theme.fg};
  background: ${(p) => p.theme.bg};
`;

const Nav = styled.nav`
  display: flex;
  gap: 1rem;
  a { color: ${(p) => p.theme.accent}; text-decoration: none; }
`;

const Button = styled.button`
  font: inherit;
  padding: 0.5rem 1rem;
  border: none;
  border-radius: 0.5rem;
  color: #fff;
  background: ${(p) => p.theme.accent};
  cursor: pointer;
`;

function Home() {
  const { count, inc, reset } = useCounter();
  return (
    <section>
      <h1>Home</h1>
      <p>Zustand store count: <strong>{count}</strong></p>
      <div style={{ display: "flex", gap: "0.5rem" }}>
        <Button onClick={inc}>increment</Button>
        <Button onClick={reset}>reset</Button>
      </div>
    </section>
  );
}

function Data() {
  const { data, isPending, isError } = useQuery({
    queryKey: ["todo", 1],
    queryFn: async () =>
      (await axios.get("https://jsonplaceholder.typicode.com/todos/1")).data,
  });
  return (
    <section>
      <h1>Data</h1>
      {isPending && <p>Loading…</p>}
      {isError && <p>Failed (needs network).</p>}
      {data && <pre style={{ textAlign: "left" }}>{JSON.stringify(data, null, 2)}</pre>}
    </section>
  );
}

export default function App() {
  return (
    <ThemeProvider theme={theme}>
      <GlobalStyle />
      <Shell>
        <Nav>
          <Link to="/">Home</Link>
          <Link to="/data">Data</Link>
        </Nav>
        <Routes>
          <Route path="/" element={<Home />} />
          <Route path="/data" element={<Data />} />
          <Route path="*" element={<h1>404</h1>} />
        </Routes>
      </Shell>
    </ThemeProvider>
  );
}
