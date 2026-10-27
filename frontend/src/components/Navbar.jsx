import { useEffect, useRef, useState } from 'react';
import { Link, NavLink, useNavigate } from 'react-router-dom';
import { useAuth } from '../context/AuthContext';
import { useMarket } from '../context/MarketContext';

export function Logo() {
  return (
    <span className="logo">
      <svg viewBox="0 0 32 32" aria-hidden="true">
        <rect width="32" height="32" rx="7" />
        <path d="M6 21l6-6 5 4 9-9" />
      </svg>
      StockBroker
    </span>
  );
}

export default function Navbar() {
  const { user, isPremium, logout } = useAuth();
  const { status } = useMarket();
  const [open, setOpen] = useState(false);
  const [signingOut, setSigningOut] = useState(false);
  const menuRef = useRef(null);
  const navigate = useNavigate();

  useEffect(() => {
    const close = (e) => menuRef.current && !menuRef.current.contains(e.target) && setOpen(false);
    document.addEventListener('mousedown', close);
    return () => document.removeEventListener('mousedown', close);
  }, []);

  const handleSignOut = async () => {
    setSigningOut(true);
    await logout();
    navigate('/login', { replace: true, state: { signedOut: true } });
  };

  const initials = user.fullName
    .split(' ')
    .map((p) => p[0])
    .slice(0, 2)
    .join('')
    .toUpperCase();

  return (
    <header className="nav">
      <Link to="/" className="nav-brand">
        <Logo />
      </Link>

      <nav className="nav-links">
        <NavLink to="/" end>Markets</NavLink>
        <NavLink to="/premium">{isPremium ? 'My plan' : 'Go Premium'}</NavLink>
      </nav>

      <span className={`feed feed-${status}`} title="Live price feed">
        <span className="feed-dot" />
        {status === 'live' ? 'Live' : status === 'connecting' ? 'Connecting' : 'Reconnecting'}
      </span>

      <div className="account" ref={menuRef}>
        <button className="account-btn" onClick={() => setOpen((o) => !o)} aria-expanded={open}>
          <span className={`avatar ${isPremium ? 'avatar-premium' : ''}`}>{initials}</span>
          <span className="account-name">{user.fullName.split(' ')[0]}</span>
        </button>
        {open && (
          <div className="menu" role="menu">
            <div className="menu-head">
              <strong>{user.fullName}</strong>
              <span>{user.email}</span>
              <span className={isPremium ? 'plan-tag premium' : 'plan-tag'}>
                {isPremium ? 'Premium' : 'Free plan'}
              </span>
            </div>
            <Link to="/premium" onClick={() => setOpen(false)} role="menuitem">
              Manage subscription
            </Link>
            <button onClick={handleSignOut} disabled={signingOut} role="menuitem">
              {signingOut ? 'Signing out…' : 'Sign out'}
            </button>
          </div>
        )}
      </div>
    </header>
  );
}
