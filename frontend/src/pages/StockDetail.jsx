import { useEffect, useState } from 'react';
import { Link, useParams } from 'react-router-dom';
import { useAuth } from '../context/AuthContext';
import { useMarket } from '../context/MarketContext';
import { errorMessage, stockApi } from '../api/client';
import PriceChart from '../components/PriceChart';
import { direction, pct, price, signed, SIGNAL_LABEL, time, TREND_LABEL, volume } from '../utils/format';

const MAX_POINTS = 300;

function Metric({ label, value, hint, tone }) {
  return (
    <div className="metric">
      <dt>{label}</dt>
      <dd className={tone ? `num ${tone}` : 'num'}>{value}</dd>
      {hint && <p>{hint}</p>}
    </div>
  );
}

function rsiHint(rsi) {
  if (rsi == null) return 'Needs more data';
  if (rsi >= 70) return 'Overbought: buyers may be stretched';
  if (rsi <= 30) return 'Oversold: sellers may be exhausted';
  return 'Neutral zone (30–70)';
}

function signalTone(sig) {
  if (!sig) return '';
  if (sig.includes('BUY')) return 'up';
  if (sig.includes('SELL')) return 'down';
  return 'flat';
}

export default function StockDetail() {
  const { symbol } = useParams();
  const sym = symbol.toUpperCase();
  const { isPremium } = useAuth();
  const { quotes, onTick } = useMarket();
  const quote = quotes[sym];

  const [points, setPoints] = useState([]);
  const [analysis, setAnalysis] = useState(null);
  const [error, setError] = useState('');

  // Initial history from Redis (via REST), then append live ticks from the WebSocket.
  useEffect(() => {
    let active = true;
    setPoints([]);
    setAnalysis(null);
    setError('');
    stockApi
      .history(sym, MAX_POINTS)
      .then((h) => active && setPoints(h))
      .catch((err) => active && setError(errorMessage(err)));

    const off = onTick((q) => {
      if (q.symbol !== sym) return;
      setPoints((prev) => {
        if (prev.length && prev[prev.length - 1].timestamp >= q.timestamp) return prev;
        const next = [...prev, { timestamp: q.timestamp, price: q.price, volume: q.volume }];
        return next.length > MAX_POINTS ? next.slice(-MAX_POINTS) : next;
      });
    });
    return () => {
      active = false;
      off();
    };
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [sym]);

  // Live analysis: refreshed every 3 seconds (backend caches each result in Redis).
  useEffect(() => {
    let active = true;
    const load = () =>
      stockApi
        .analysis(sym)
        .then((a) => active && setAnalysis(a))
        .catch((err) => active && err.response?.status !== 503 && setError(errorMessage(err)));
    load();
    const id = setInterval(load, 3000);
    return () => {
      active = false;
      clearInterval(id);
    };
  }, [sym, isPremium]);

  if (error && !quote) {
    return (
      <div className="panel">
        <div className="alert">{error}</div>
        <Link to="/">Back to markets</Link>
      </div>
    );
  }

  const dir = direction(quote?.change);
  const a = analysis;

  return (
    <>
      <Link to="/" className="back">Markets</Link>

      <section className="quote-head">
        <div>
          <h1>{sym}</h1>
          <p className="muted company">
            {quote?.companyName}
            {quote && <span className="chip">{quote.exchange}</span>}
            {quote && <span className="chip">{quote.sector}</span>}
          </p>
        </div>
        <div className="quote-price">
          <span className="big num">₹{price(quote?.price)}</span>
          <span className={`num ${dir}`}>
            {signed(quote?.change)} ({pct(quote?.changePercent)})
          </span>
          {quote && <span className="muted small">Updated {time(quote.timestamp)}</span>}
        </div>
      </section>

      <section className="panel chart-panel">
        <dl className="ohlc">
          <Metric label="Open" value={price(quote?.open)} />
          <Metric label="High" value={price(quote?.high)} />
          <Metric label="Low" value={price(quote?.low)} />
          <Metric label="Prev close" value={price(quote?.previousClose)} />
          <Metric label="Volume" value={volume(quote?.volume)} />
        </dl>
        {points.length > 1 ? (
          <PriceChart points={points} previousClose={quote?.previousClose} up={dir !== 'down'} />
        ) : (
          <p className="empty">Waiting for price data…</p>
        )}
        {!isPremium && (
          <p className="chart-note">
            Free plan charts show the last 60 ticks. <Link to="/premium">Premium</Link> shows the full session.
          </p>
        )}
      </section>

      <section className="analysis">
        <div className="panel">
          <h2>Trend</h2>
          {!a ? (
            <p className="empty">Calculating…</p>
          ) : (
            <>
              <p className={`trend trend-${(a.trend || '').toLowerCase()}`}>{TREND_LABEL[a.trend] || a.trend}</p>
              <dl className="metrics">
                <Metric label="SMA 20" value={price(a.sma20)} hint="Average of the last 20 ticks" />
                <Metric label="SMA 50" value={price(a.sma50)} hint="Average of the last 50 ticks" />
                <Metric label="EMA 20" value={price(a.ema20)} hint="Weights recent ticks more" />
                <Metric label="Session range" value={`${price(a.sessionLow)} – ${price(a.sessionHigh)}`} />
              </dl>
              <p className="muted small">Based on {a.dataPoints} ticks.</p>
            </>
          )}
        </div>

        <div className={`panel premium-panel ${a?.premiumLocked ? 'locked' : ''}`}>
          <h2>Signals and indicators</h2>
          {!a ? (
            <p className="empty">Calculating…</p>
          ) : a.premiumLocked ? (
            <div className="lock">
              <dl className="metrics ghost" aria-hidden="true">
                <Metric label="RSI 14" value="54.2" />
                <Metric label="MACD" value="0.4182" />
                <Metric label="Bollinger" value="1,702 – 1,736" />
                <Metric label="Signal" value="Buy" />
              </dl>
              <div className="lock-msg">
                <h3>Premium analysis</h3>
                <p>Unlock RSI, MACD, Bollinger Bands, volatility, support and resistance, and a buy/sell signal that explains its reasoning.</p>
                <Link to="/premium" className="btn btn-gold">Upgrade to Premium</Link>
              </div>
            </div>
          ) : (
            <>
              <div className={`signal ${signalTone(a.signal)}`}>
                <span className="signal-label">{SIGNAL_LABEL[a.signal] || a.signal}</span>
                <span className="signal-score num">
                  Score {a.signalScore > 0 ? '+' : ''}
                  {a.signalScore}
                </span>
                <div className="gauge" aria-hidden="true">
                  <span style={{ left: `${(a.signalScore + 100) / 2}%` }} />
                </div>
              </div>
              <dl className="metrics">
                <Metric label="RSI 14" value={a.rsi14?.toFixed(1) ?? '—'} hint={rsiHint(a.rsi14)} />
                <Metric
                  label="MACD histogram"
                  value={a.macdHistogram?.toFixed(4) ?? '—'}
                  tone={direction(a.macdHistogram)}
                  hint={a.macd != null ? `MACD ${a.macd.toFixed(4)} / signal ${a.macdSignal.toFixed(4)}` : null}
                />
                <Metric
                  label="Bollinger Bands"
                  value={a.bollingerLower != null ? `${price(a.bollingerLower)} – ${price(a.bollingerUpper)}` : '—'}
                  hint={a.bollingerMiddle != null ? `Middle ${price(a.bollingerMiddle)}` : null}
                />
                <Metric label="Volatility" value={a.volatilityPercent != null ? `${a.volatilityPercent.toFixed(3)}%` : '—'} hint="Per-tick price swing" />
                <Metric label="Support" value={price(a.support)} hint="Lowest of last 50 ticks" />
                <Metric label="Resistance" value={price(a.resistance)} hint="Highest of last 50 ticks" />
                <Metric label="Momentum" value={pct(a.momentum10)} tone={direction(a.momentum10)} hint="Change over 10 ticks" />
              </dl>
              {a.reasons?.length > 0 && (
                <>
                  <h3>Why this signal</h3>
                  <ul className="reasons">
                    {a.reasons.map((r) => (
                      <li key={r}>{r}</li>
                    ))}
                  </ul>
                </>
              )}
              <p className="muted small">
                Signals are computed from simulated prices for learning purposes and are not investment advice.
              </p>
            </>
          )}
        </div>
      </section>
    </>
  );
}
