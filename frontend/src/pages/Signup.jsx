import { useState } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import { useAuth } from '../context/AuthContext';
import { errorMessage } from '../api/client';
import AuthLayout from './AuthLayout';

function validate({ fullName, email, password, confirm }) {
  if (!fullName.trim()) return 'Enter your full name.';
  if (!/^\S+@\S+\.\S+$/.test(email)) return 'Enter a valid email address.';
  if (password.length < 8) return 'Password must be at least 8 characters.';
  if (!/[A-Za-z]/.test(password) || !/\d/.test(password)) return 'Password needs at least one letter and one number.';
  if (password !== confirm) return 'Passwords don’t match.';
  return '';
}

export default function Signup() {
  const { register } = useAuth();
  const navigate = useNavigate();
  const [form, setForm] = useState({ fullName: '', email: '', password: '', confirm: '' });
  const [error, setError] = useState('');
  const [busy, setBusy] = useState(false);

  const update = (e) => setForm({ ...form, [e.target.name]: e.target.value });

  const submit = async (e) => {
    e.preventDefault();
    const problem = validate(form);
    if (problem) return setError(problem);
    setError('');
    setBusy(true);
    try {
      await register(form.fullName, form.email, form.password);
      navigate('/', { replace: true });
    } catch (err) {
      setError(errorMessage(err, 'Could not create your account.'));
      setBusy(false);
    }
  };

  return (
    <AuthLayout
      title="Create your account"
      subtitle="Free to start. Upgrade to Premium whenever you want deeper analysis."
      footer={<>Already have an account? <Link to="/login">Sign in</Link></>}
    >
      {error && <div className="alert" role="alert">{error}</div>}
      <form onSubmit={submit} className="form" noValidate>
        <label>
          Full name
          <input name="fullName" autoComplete="name" value={form.fullName} onChange={update} required />
        </label>
        <label>
          Email
          <input name="email" type="email" autoComplete="email" value={form.email} onChange={update} required />
        </label>
        <label>
          Password
          <input
            name="password"
            type="password"
            autoComplete="new-password"
            value={form.password}
            onChange={update}
            required
          />
          <small>At least 8 characters, with a letter and a number.</small>
        </label>
        <label>
          Confirm password
          <input
            name="confirm"
            type="password"
            autoComplete="new-password"
            value={form.confirm}
            onChange={update}
            required
          />
        </label>
        <button className="btn btn-primary" disabled={busy}>
          {busy ? 'Creating account…' : 'Create account'}
        </button>
      </form>
    </AuthLayout>
  );
}
