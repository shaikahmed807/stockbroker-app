import { Navigate, Outlet, Route, Routes } from 'react-router-dom';
import { useAuth } from './context/AuthContext';
import { MarketProvider } from './context/MarketContext';
import Navbar from './components/Navbar';
import TickerTape from './components/TickerTape';
import Login from './pages/Login';
import Signup from './pages/Signup';
import Dashboard from './pages/Dashboard';
import StockDetail from './pages/StockDetail';
import Plans from './pages/Plans';

/** Signed-in area: market data stream + navigation. */
function AppShell() {
  const { user, loading } = useAuth();
  if (loading) return <div className="boot">Loading your account…</div>;
  if (!user) return <Navigate to="/login" replace />;
  return (
    <MarketProvider>
      <Navbar />
      <TickerTape />
      <main className="page">
        <Outlet />
      </main>
    </MarketProvider>
  );
}

function GuestOnly({ children }) {
  const { user, loading } = useAuth();
  if (loading) return <div className="boot">Loading…</div>;
  return user ? <Navigate to="/" replace /> : children;
}

export default function App() {
  return (
    <Routes>
      <Route path="/login" element={<GuestOnly><Login /></GuestOnly>} />
      <Route path="/signup" element={<GuestOnly><Signup /></GuestOnly>} />
      <Route element={<AppShell />}>
        <Route path="/" element={<Dashboard />} />
        <Route path="/stocks/:symbol" element={<StockDetail />} />
        <Route path="/premium" element={<Plans />} />
      </Route>
      <Route path="*" element={<Navigate to="/" replace />} />
    </Routes>
  );
}
