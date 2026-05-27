import { Routes, Route, Link, useParams } from "react-router-dom";

const page = {
  minHeight: "100dvh",
  display: "flex",
  flexDirection: "column",
  alignItems: "center",
  justifyContent: "center",
  gap: "1rem",
  fontFamily: "system-ui, -apple-system, sans-serif",
};

function Nav() {
  return (
    <nav style={{ display: "flex", gap: "1rem" }}>
      <Link to="/">Home</Link>
      <Link to="/about">About</Link>
      <Link to="/user/ada">User: ada</Link>
    </nav>
  );
}

function Home() {
  return (
    <>
      <h1>Home</h1>
      <p>Routing with <code>react-router-dom</code>.</p>
    </>
  );
}

function About() {
  return (
    <>
      <h1>About</h1>
      <p>Edit <code>src/App.jsx</code> to add routes.</p>
    </>
  );
}

function User() {
  const { name } = useParams();
  return (
    <>
      <h1>User: {name}</h1>
      <p>A dynamic route parameter.</p>
    </>
  );
}

export default function App() {
  return (
    <main style={page}>
      <Nav />
      <Routes>
        <Route path="/" element={<Home />} />
        <Route path="/about" element={<About />} />
        <Route path="/user/:name" element={<User />} />
        <Route path="*" element={<h1>404</h1>} />
      </Routes>
    </main>
  );
}
