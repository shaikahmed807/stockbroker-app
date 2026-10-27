import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter, Route, Routes } from 'react-router-dom';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import Login from '../pages/Login';
import { useAuth } from '../context/AuthContext';

vi.mock('../context/AuthContext', () => ({ useAuth: vi.fn() }));

const login = vi.fn();

function renderLogin(entry = '/login') {
  return render(
    <MemoryRouter initialEntries={[entry]}>
      <Routes>
        <Route path="/login" element={<Login />} />
        <Route path="/" element={<p>Markets home</p>} />
      </Routes>
    </MemoryRouter>
  );
}

describe('Login page', () => {
  beforeEach(() => {
    login.mockReset();
    useAuth.mockReturnValue({ login });
  });

  it('shows the sign-in form', () => {
    renderLogin();
    expect(screen.getByRole('heading', { name: 'Sign in' })).toBeInTheDocument();
    expect(screen.getByLabelText('Email')).toBeInTheDocument();
    expect(screen.getByLabelText('Password')).toHaveAttribute('type', 'password');
    expect(screen.getByRole('link', { name: 'Create an account' })).toHaveAttribute('href', '/signup');
  });

  it('fills in the demo account with one click', async () => {
    renderLogin();
    await userEvent.click(screen.getByRole('button', { name: 'Fill in the demo account' }));
    expect(screen.getByLabelText('Email')).toHaveValue('demo@stockbroker.com');
    expect(screen.getByLabelText('Password')).toHaveValue('Demo@1234');
  });

  it('signs in and goes to the markets page', async () => {
    login.mockResolvedValue({ fullName: 'Jane' });
    renderLogin();

    await userEvent.type(screen.getByLabelText('Email'), 'jane@example.com');
    await userEvent.type(screen.getByLabelText('Password'), 'Secret123');
    await userEvent.click(screen.getByRole('button', { name: 'Sign in' }));

    expect(login).toHaveBeenCalledWith('jane@example.com', 'Secret123');
    expect(await screen.findByText('Markets home')).toBeInTheDocument();
  });

  it('shows the server error when sign-in fails', async () => {
    login.mockRejectedValue({ response: { data: { message: 'Invalid email or password' } } });
    renderLogin();

    await userEvent.type(screen.getByLabelText('Email'), 'jane@example.com');
    await userEvent.type(screen.getByLabelText('Password'), 'wrong');
    await userEvent.click(screen.getByRole('button', { name: 'Sign in' }));

    expect(await screen.findByRole('alert')).toHaveTextContent('Invalid email or password');
    expect(screen.getByRole('button', { name: 'Sign in' })).toBeEnabled();
  });

  it('confirms a completed sign-out', () => {
    renderLogin({ pathname: '/login', state: { signedOut: true } });
    expect(screen.getByText(/signed out/i)).toBeInTheDocument();
  });
});
