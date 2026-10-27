import { useState } from 'react';
import { Link, useLocation, useNavigate } from 'react-router-dom';
import { useAuth } from '../context/AuthContext';
import { errorMessage } from '../api/client';
import AuthLayout from './AuthLayout';

export default function Login() {
  const { login } = useAuth();
  const navigate = useNavigate();
  const location = useLocation();
  const [form, setForm] = useState({ email: '', password: '' });
  const [error, setError] = useState('');
  const [busy, setBusy] = useState(false);

  const update = (e) => setForm({ ...form, [e.target.name]: e.target.value });

  const submit = async (e) => {
    e.preventDefault();
    setError('');
    setBusy(true);
    try {
      await login(form.email, form.password);
      navigate('/', { replace: true });
    } catch (err) {
      setError(errorMessage(err, 'Sign in failed.'));
      setBusy(false);
    }
  };

  const fillDemo = () => setForm({ email: 'demo@stockbroker.com', password: 'Demo@1234' });

  return (
    <AuthLayout
      title="Sign in"
      subtitle="Welcome back. Your watch on the market picks up where you left it."
      footer={<>New here? <Link to="/signup">Create an account</Link></>}
    >
      {location.state?.signedOut && !error && <div className="notice">You’re signed out.</div>}
      {error && <div className="alert" role="alert">{error}</div>}
      <form onSubmit={submit} className="form" noValidate>
        <label>
          Email
          <input name="email" type="email" autoComplete="email" value={form.email} onChange={update} required />
        </label>
        <label>
          Password
          <input
            name="password"
            type="password"
            autoComplete="current-password"
            value={form.password}
            onChange={update}
            required
          />
        </label>
        <button className="btn btn-primary" disabled={busy}>
          {busy ? 'Signing in…' : 'Sign in'}
        </button>
      </form>
      <button type="button" className="link-btn" onClick={fillDemo}>
        Fill in the demo account
      </button>
    </AuthLayout>
  );
}
