import { Logo } from '../components/Navbar';

/** Two-column frame shared by the sign-in and sign-up screens. */
export default function AuthLayout({ title, subtitle, children, footer }) {
  return (
    <div className="auth">
      <aside className="auth-side">
        <Logo />
        <div className="auth-pitch">
          <h1>Watch the market move, tick by tick.</h1>
          <ul>
            <li>Live prices for NSE large caps, streamed every two seconds</li>
            <li>Moving averages and trend on every stock, free</li>
            <li>RSI, MACD, Bollinger Bands and buy/sell signals with Premium</li>
          </ul>
        </div>
        <svg className="auth-line" viewBox="0 0 400 120" preserveAspectRatio="none" aria-hidden="true">
          <polyline points="0,96 40,88 72,98 110,70 150,78 190,52 226,60 262,34 300,44 340,20 372,28 400,8" />
        </svg>
        <p className="auth-note">Prices in this app come from a built-in market simulator, not a live exchange.</p>
      </aside>
      <section className="auth-main">
        <div className="auth-card">
          <h2>{title}</h2>
          {subtitle && <p className="muted">{subtitle}</p>}
          {children}
          {footer && <p className="auth-footer">{footer}</p>}
        </div>
      </section>
    </div>
  );
}
