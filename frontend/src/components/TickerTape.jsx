import { Link } from 'react-router-dom';
import { useMarket } from '../context/MarketContext';
import { direction, pct, price } from '../utils/format';

/** Scrolling strip of every stock's live price — the signature element of the app. */
export default function TickerTape() {
  const { quotes } = useMarket();
  const list = Object.values(quotes).sort((a, b) => a.symbol.localeCompare(b.symbol));
  if (!list.length) return <div className="tape tape-empty" />;

  const items = list.map((q) => (
    <Link key={q.symbol} to={`/stocks/${q.symbol}`} className={`tape-item ${direction(q.change)}`}>
      <b>{q.symbol}</b>
      <span>{price(q.price)}</span>
      <i>{pct(q.changePercent)}</i>
    </Link>
  ));

  return (
    <div className="tape" aria-label="Live prices">
      <div className="tape-track">
        <div className="tape-group">{items}</div>
        <div className="tape-group" aria-hidden="true">{items}</div>
      </div>
    </div>
  );
}
