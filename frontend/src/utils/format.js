const inr = new Intl.NumberFormat('en-IN', { minimumFractionDigits: 2, maximumFractionDigits: 2 });
const compact = new Intl.NumberFormat('en-IN', { notation: 'compact', maximumFractionDigits: 1 });

export const price = (v) => (v == null || Number.isNaN(v) ? '—' : inr.format(v));
export const rupees = (v) => (v == null ? '—' : `₹${inr.format(v)}`);
export const signed = (v) => (v == null ? '—' : `${v > 0 ? '+' : v < 0 ? '−' : ''}${inr.format(Math.abs(v))}`);
export const pct = (v) => (v == null ? '—' : `${v > 0 ? '+' : v < 0 ? '−' : ''}${Math.abs(v).toFixed(2)}%`);
export const volume = (v) => (v == null ? '—' : compact.format(v));
export const direction = (v) => (v > 0 ? 'up' : v < 0 ? 'down' : 'flat');

export const time = (ts) =>
  new Date(ts).toLocaleTimeString('en-IN', { hour: '2-digit', minute: '2-digit', second: '2-digit' });

export const date = (iso) =>
  iso ? new Date(iso).toLocaleDateString('en-IN', { day: 'numeric', month: 'short', year: 'numeric' }) : '—';

export const SIGNAL_LABEL = {
  STRONG_BUY: 'Strong buy',
  BUY: 'Buy',
  HOLD: 'Hold',
  SELL: 'Sell',
  STRONG_SELL: 'Strong sell',
};

export const TREND_LABEL = {
  UP: 'Uptrend',
  DOWN: 'Downtrend',
  SIDEWAYS: 'Sideways',
  INSUFFICIENT_DATA: 'Gathering data',
};
