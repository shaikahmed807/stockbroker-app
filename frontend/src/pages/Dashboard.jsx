import { useEffect, useMemo, useRef, useState } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import { useAuth } from '../context/AuthContext';
import { useMarket } from '../context/MarketContext';
import { direction, pct, price, signed, volume } from '../utils/format';

/** Briefly highlights a price cell when it changes. */
function FlashCell({ value, prev }) {
  const ref = useRef(null);
  useEffect(() => {
    if (prev == null || prev === value || !ref.current) return;
    const cls = value > prev ? 'flash-up' : 'flash-down';
    ref.current.classList.remove('flash-up', 'flash-down');
    void ref.current.offsetWidth; // restart animation
    ref.current.classList.add(cls);
  }, [value, prev]);
  return <span ref={ref} className="num">{price(value)}</span>;
}

const SORTS = {
  symbol: (a, b) => a.symbol.localeCompare(b.symbol),
  gain: (a, b) => b.changePercent - a.changePercent,
  loss: (a, b) => a.changePercent - b.changePercent,
};

export default function Dashboard() {
  const { user, isPremium } = useAuth();
  const { quotes, error } = useMarket();
  const [query, setQuery] = useState('');
  const [sort, setSort] = useState('symbol');
  const navigate = useNavigate();

  const all = Object.values(quotes);
  const rows = useMemo(() => {
    const q = query.trim().toLowerCase();
    return all
      .filter((s) => !q || s.symbol.toLowerCase().includes(q) || s.companyName.toLowerCase().includes(q))
      .sort(SORTS[sort]);
  }, [all, query, sort]);

  const advancing = all.filter((q) => q.change > 0).length;
  const declining = all.filter((q) => q.change < 0).length;
  const best = [...all].sort(SORTS.gain)[0];
  const worst = [...all].sort(SORTS.loss)[0];

  return (
    <>
      <section className="dash-head">
        <div>
          <h1>Markets</h1>
          <p className="muted">Hello {user.fullName.split(' ')[0]}. Select a stock to see its live chart and analysis.</p>
        </div>
        <dl className="breadth">
          <div>
            <dt>Advancing</dt>
            <dd className="up">{advancing}</dd>
          </div>
          <div>
            <dt>Declining</dt>
            <dd className="down">{declining}</dd>
          </div>
          {best && (
            <div>
              <dt>Top gainer</dt>
              <dd>
                <Link to={`/stocks/${best.symbol}`}>{best.symbol}</Link>{' '}
                <span className={direction(best.changePercent)}>{pct(best.changePercent)}</span>
              </dd>
            </div>
          )}
          {worst && (
            <div>
              <dt>Top loser</dt>
              <dd>
                <Link to={`/stocks/${worst.symbol}`}>{worst.symbol}</Link>{' '}
                <span className={direction(worst.changePercent)}>{pct(worst.changePercent)}</span>
              </dd>
            </div>
          )}
        </dl>
      </section>

      {!isPremium && (
        <div className="upsell">
          <p>
            <strong>See the signals behind the moves.</strong> Premium adds RSI, MACD, Bollinger Bands and a
            buy/sell signal for every stock.
          </p>
          <Link to="/premium" className="btn btn-gold">See Premium plans</Link>
        </div>
      )}

      <section className="panel">
        <div className="table-tools">
          <input
            type="search"
            placeholder="Search by symbol or company"
            value={query}
            onChange={(e) => setQuery(e.target.value)}
            aria-label="Search stocks"
          />
          <div className="seg" role="group" aria-label="Sort">
            {[
              ['symbol', 'A–Z'],
              ['gain', 'Gainers'],
              ['loss', 'Losers'],
            ].map(([k, label]) => (
              <button key={k} className={sort === k ? 'on' : ''} onClick={() => setSort(k)}>
                {label}
              </button>
            ))}
          </div>
        </div>

        {error && <div className="alert">{error}</div>}
        {!error && all.length === 0 && <p className="empty">Loading market data…</p>}
        {all.length > 0 && rows.length === 0 && <p className="empty">No stock matches “{query}”.</p>}

        {rows.length > 0 && (
          <div className="table-wrap">
            <table className="quotes">
              <thead>
                <tr>
                  <th>Stock</th>
                  <th className="r">Price (₹)</th>
                  <th className="r">Change</th>
                  <th className="r hide-sm">Day range</th>
                  <th className="r hide-md">Volume</th>
                  <th className="hide-md">Sector</th>
                </tr>
              </thead>
              <tbody>
                {rows.map((q) => (
                  <tr key={q.symbol} onClick={() => navigate(`/stocks/${q.symbol}`)}>
                    <td>
                      <Link to={`/stocks/${q.symbol}`} className="sym" onClick={(e) => e.stopPropagation()}>
                        {q.symbol}
                      </Link>
                      <span className="co">{q.companyName}</span>
                    </td>
                    <td className="r">
                      <FlashCell value={q.price} prev={q._prev} />
                    </td>
                    <td className={`r num ${direction(q.change)}`}>
                      {signed(q.change)}
                      <small>{pct(q.changePercent)}</small>
                    </td>
                    <td className="r num hide-sm">
                      {price(q.low)} – {price(q.high)}
                    </td>
                    <td className="r num hide-md">{volume(q.volume)}</td>
                    <td className="hide-md muted">{q.sector}</td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )}
      </section>
    </>
  );
}
