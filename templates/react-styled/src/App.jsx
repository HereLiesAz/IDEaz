import { useState } from "react";
import styled, { ThemeProvider, createGlobalStyle } from "styled-components";

const theme = { fg: "#f5f5f5", bg: "#111111", accent: "#3b82f6" };

const GlobalStyle = createGlobalStyle`
  body { margin: 0; font-family: system-ui, -apple-system, sans-serif; }
`;

const Page = styled.main`
  min-height: 100dvh;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 1rem;
  color: ${(p) => p.theme.fg};
  background: ${(p) => p.theme.bg};
`;

const Button = styled.button`
  font: inherit;
  padding: 0.6rem 1.2rem;
  border: none;
  border-radius: 0.5rem;
  color: #fff;
  background: ${(p) => p.theme.accent};
  cursor: pointer;
`;

export default function App() {
  const [count, setCount] = useState(0);
  return (
    <ThemeProvider theme={theme}>
      <GlobalStyle />
      <Page>
        <h1>React + styled-components</h1>
        <p>Themed, component-scoped styles.</p>
        <Button onClick={() => setCount((c) => c + 1)}>count is {count}</Button>
      </Page>
    </ThemeProvider>
  );
}
