import { render, screen } from '@testing-library/react';
import { MemoryRouter } from 'react-router-dom';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import App from '../App';
import { useAuth } from '../context/AuthContext';
import { FREE_USER } from '../test/fixtures';

vi.mock('../context/AuthContext', () => ({ useAuth: vi.fn() }));
vi.mock('../context/MarketContext', () => ({
  MarketProvider: ({ children }) => children,
  useMarket: () => ({ quotes: {}, status: 'live', error: '', onTick: () => () => {} }),
}));

const renderAt = (path) =>
  render(
    <MemoryRouter initialEntries={[path]}>
      <App />
    </MemoryRouter>
  );

describe('App routing', () => {
  beforeEach(() => {
    useAuth.mockReturnValue({ user: null, loading: false, isPremium: false });
  });

  it('sends signed-out visitors to sign in', () => {
    renderAt('/');
    expect(screen.getByRole('heading', { name: 'Sign in' })).toBeInTheDocument();
  });

  it('protects the Premium page too', () => {
    renderAt('/premium');
    expect(screen.getByRole('heading', { name: 'Sign in' })).toBeInTheDocument();
  });

  it('shows the sign-up page to guests', () => {
    renderAt('/signup');
    expect(screen.getByRole('heading', { name: 'Create your account' })).toBeInTheDocument();
  });

  it('waits while the session is being restored', () => {
    useAuth.mockReturnValue({ user: null, loading: true });
    renderAt('/');
    expect(screen.getByText(/Loading your account/)).toBeInTheDocument();
  });

  it('shows the markets to signed-in users', () => {
    useAuth.mockReturnValue({ user: FREE_USER, loading: false, isPremium: false, logout: vi.fn() });
    renderAt('/');
    expect(screen.getByRole('heading', { name: 'Markets' })).toBeInTheDocument();
  });

  it('keeps signed-in users away from the sign-in page', () => {
    useAuth.mockReturnValue({ user: FREE_USER, loading: false, isPremium: false, logout: vi.fn() });
    renderAt('/login');
    expect(screen.getByRole('heading', { name: 'Markets' })).toBeInTheDocument();
  });

  it('redirects unknown URLs home', () => {
    useAuth.mockReturnValue({ user: FREE_USER, loading: false, isPremium: false, logout: vi.fn() });
    renderAt('/does-not-exist');
    expect(screen.getByRole('heading', { name: 'Markets' })).toBeInTheDocument();
  });
});
