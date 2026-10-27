import { useEffect, useState } from 'react';
import { useAuth } from '../context/AuthContext';
import { errorMessage, subscriptionApi } from '../api/client';
import { date, rupees } from '../utils/format';

const STATUS_LABEL = { ACTIVE: 'Active', CANCELLED: 'Cancelled', EXPIRED: 'Expired' };

function ConfirmDialog({ plan, busy, onConfirm, onClose }) {
  useEffect(() => {
    const esc = (e) => e.key === 'Escape' && !busy && onClose();
    window.addEventListener('keydown', esc);
    return () => window.removeEventListener('keydown', esc);
  }, [busy, onClose]);

  return (
    <div className="overlay" onClick={() => !busy && onClose()}>
      <div className="dialog" role="dialog" aria-modal="true" aria-labelledby="dlg-title" onClick={(e) => e.stopPropagation()}>
        <h2 id="dlg-title">Subscribe to {plan.name}</h2>
        <dl className="bill">
          <div><dt>Plan</dt><dd>{plan.name}</dd></div>
          <div><dt>Access</dt><dd>{plan.durationDays} days</dd></div>
          <div className="bill-total"><dt>Total</dt><dd>{rupees(plan.price)}</dd></div>
        </dl>
        <p className="notice">
          This project uses a demo payment step: no card details are collected and nothing is charged.
          See the README to connect Razorpay or Stripe.
        </p>
        <div className="dialog-actions">
          <button className="btn" onClick={onClose} disabled={busy}>Cancel</button>
          <button className="btn btn-gold" onClick={onConfirm} disabled={busy} autoFocus>
            {busy ? 'Activating…' : `Pay ${rupees(plan.price)} and subscribe`}
          </button>
        </div>
      </div>
    </div>
  );
}

export default function Plans() {
  const { isPremium, refreshUser } = useAuth();
  const [plans, setPlans] = useState([]);
  const [status, setStatus] = useState(null);
  const [history, setHistory] = useState([]);
  const [selected, setSelected] = useState(null);
  const [busy, setBusy] = useState(false);
  const [message, setMessage] = useState('');
  const [error, setError] = useState('');

  const load = async () => {
    try {
      const [p, s, h] = await Promise.all([subscriptionApi.plans(), subscriptionApi.status(), subscriptionApi.history()]);
      setPlans(p);
      setStatus(s);
      setHistory(h);
    } catch (err) {
      setError(errorMessage(err, 'Could not load plans.'));
    }
  };

  useEffect(() => {
    load();
  }, []);

  const subscribe = async () => {
    setBusy(true);
    setError('');
    try {
      const sub = await subscriptionApi.subscribe(selected.code);
      await Promise.all([refreshUser(), load()]);
      setMessage(`Subscribed to ${selected.name}. Premium is active until ${date(sub.endDate)}.`);
      setSelected(null);
    } catch (err) {
      setError(errorMessage(err, 'Could not activate the subscription.'));
    } finally {
      setBusy(false);
    }
  };

  const cancel = async () => {
    if (!window.confirm('Cancel Premium now? You will lose access to premium analysis immediately.')) return;
    setBusy(true);
    setError('');
    try {
      await subscriptionApi.cancel();
      await Promise.all([refreshUser(), load()]);
      setMessage('Premium cancelled. You’re on the Free plan.');
    } catch (err) {
      setError(errorMessage(err, 'Could not cancel the subscription.'));
    } finally {
      setBusy(false);
    }
  };

  return (
    <>
      <section className="plans-head">
        <h1>{isPremium ? 'Your Premium plan' : 'Go Premium'}</h1>
        {status?.premium ? (
          <p>
            You’re on <strong>{plans.find((p) => p.code === status.plan)?.name || 'Premium'}</strong> until{' '}
            {date(status.premiumUntil)} ({status.daysRemaining} days left). Subscribing again adds time to your
            current plan.
          </p>
        ) : (
          <p className="muted">You’re on the Free plan. Premium unlocks the full analysis toolkit on every stock.</p>
        )}
      </section>

      {message && <div className="success" role="status">{message}</div>}
      {error && <div className="alert" role="alert">{error}</div>}

      <section className="plans">
        {plans.map((p) => {
          const current = status ? status.plan === p.code : false;
          const isFree = p.code === 'FREE';
          return (
            <article key={p.code} className={`plan ${p.code === 'PREMIUM_YEARLY' ? 'plan-feature' : ''} ${current ? 'plan-current' : ''}`}>
              <h2>{p.name}</h2>
              <p className="plan-price">
                <span className="num">{isFree ? '₹0' : rupees(p.price)}</span>
                {!isFree && <small>/ {p.durationDays === 365 ? 'year' : 'month'}</small>}
              </p>
              {p.code === 'PREMIUM_YEARLY' && <p className="plan-save">Works out to {rupees(Math.round(p.price / 12))} a month</p>}
              <ul>
                {p.features.map((f) => (
                  <li key={f}>{f}</li>
                ))}
              </ul>
              {isFree ? (
                <button className="btn" disabled>{current ? 'Current plan' : 'Included'}</button>
              ) : (
                <button className={current ? 'btn' : 'btn btn-gold'} onClick={() => setSelected(p)} disabled={busy}>
                  {current ? 'Extend this plan' : `Choose ${p.name}`}
                </button>
              )}
            </article>
          );
        })}
      </section>

      {history.length > 0 && (
        <section className="panel">
          <div className="panel-head">
            <h2>Billing history</h2>
            {status?.premium && (
              <button className="btn btn-danger" onClick={cancel} disabled={busy}>Cancel Premium</button>
            )}
          </div>
          <div className="table-wrap">
            <table className="quotes history">
              <thead>
                <tr>
                  <th>Plan</th>
                  <th className="r">Amount</th>
                  <th>Status</th>
                  <th>Started</th>
                  <th>Ends</th>
                  <th className="hide-sm">Reference</th>
                </tr>
              </thead>
              <tbody>
                {history.map((h) => (
                  <tr key={h.id}>
                    <td>{plans.find((p) => p.code === h.plan)?.name || h.plan}</td>
                    <td className="r num">{rupees(h.amount)}</td>
                    <td><span className={`status status-${h.status.toLowerCase()}`}>{STATUS_LABEL[h.status]}</span></td>
                    <td>{date(h.startDate)}</td>
                    <td>{date(h.endDate)}</td>
                    <td className="hide-sm muted">{h.paymentReference}</td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        </section>
      )}

      {selected && <ConfirmDialog plan={selected} busy={busy} onConfirm={subscribe} onClose={() => setSelected(null)} />}
    </>
  );
}
