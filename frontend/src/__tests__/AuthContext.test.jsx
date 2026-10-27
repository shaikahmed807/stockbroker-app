import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { AuthProvider, useAuth } from '../context/AuthContext';
import { authApi } from '../api/client';

vi.mock('../api/client', async (importOriginal) => {
  const actual = await importOriginal();
  return { ...actual, authApi: { login: vi.fn(), register: vi.fn(), logout: vi.fn(), me: vi.fn() } };
});

function Probe() {
  const { user, loading, isPremium, login, register, logout } = useAuth();
  if (loading) return <p>loading</p>;
  return (
    <div>
      <p>user: {user ? user.fullName : 'none'}</p>
      <p>premium: {String(isPremium)}</p>
      <button onClick={() => login('jane@example.com', 'Secret123')}>login</button>
      <button onClick={() => register('Jane', 'jane@example.com', 'Secret123')}>register</button>
      <button onClick={logout}>logout</button>
    </div>
  );
}

const renderProvider = () =>
  render(
    <AuthProvider>
      <Probe />
    </AuthProvider>
  );

describe('AuthContext', () => {
  beforeEach(() => {
    authApi.login.mockResolvedValue({ token: 'jwt-1', user: { fullName: 'Jane', premium: true } });
    authApi.register.mockResolvedValue({ token: 'jwt-2', user: { fullName: 'Jane', premium: false } });
    authApi.logout.mockResolvedValue({ message: 'ok' });
  });

  it('starts signed out when there is no saved token', () => {
    renderProvider();
    expect(screen.getByText('user: none')).toBeInTheDocument();
    expect(authApi.me).not.toHaveBeenCalled();
  });

  it('login stores the token and the user', async () => {
    renderProvider();
    await userEvent.click(screen.getByText('login'));
    expect(await screen.findByText('user: Jane')).toBeInTheDocument();
    expect(screen.getByText('premium: true')).toBeInTheDocument();
    expect(localStorage.getItem('sb_token')).toBe('jwt-1');
  });

  it('register signs the new user in', async () => {
    renderProvider();
    await userEvent.click(screen.getByText('register'));
    expect(await screen.findByText('user: Jane')).toBeInTheDocument();
    expect(localStorage.getItem('sb_token')).toBe('jwt-2');
  });

  it('logout revokes the token on the server and clears the session', async () => {
    renderProvider();
    await userEvent.click(screen.getByText('login'));
    await screen.findByText('user: Jane');

    await userEvent.click(screen.getByText('logout'));

    expect(authApi.logout).toHaveBeenCalled();
    expect(await screen.findByText('user: none')).toBeInTheDocument();
    expect(localStorage.getItem('sb_token')).toBeNull();
  });

  it('logout still clears the session if the server call fails', async () => {
    authApi.logout.mockRejectedValue(new Error('offline'));
    renderProvider();
    await userEvent.click(screen.getByText('login'));
    await screen.findByText('user: Jane');

    await userEvent.click(screen.getByText('logout'));

    expect(await screen.findByText('user: none')).toBeInTheDocument();
  });

  it('restores the session from a saved token', async () => {
    localStorage.setItem('sb_token', 'saved');
    authApi.me.mockResolvedValue({ fullName: 'Returning User', premium: false });
    renderProvider();

    expect(screen.getByText('loading')).toBeInTheDocument();
    expect(await screen.findByText('user: Returning User')).toBeInTheDocument();
  });

  it('drops an expired saved token', async () => {
    localStorage.setItem('sb_token', 'expired');
    authApi.me.mockRejectedValue({ response: { status: 401 } });
    renderProvider();

    expect(await screen.findByText('user: none')).toBeInTheDocument();
    await waitFor(() => expect(localStorage.getItem('sb_token')).toBeNull());
  });
});
