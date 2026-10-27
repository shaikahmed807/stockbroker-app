import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter, Route, Routes } from 'react-router-dom';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import Signup from '../pages/Signup';
import { useAuth } from '../context/AuthContext';

vi.mock('../context/AuthContext', () => ({ useAuth: vi.fn() }));

const register = vi.fn();

function renderSignup() {
  render(
    <MemoryRouter initialEntries={['/signup']}>
      <Routes>
        <Route path="/signup" element={<Signup />} />
        <Route path="/" element={<p>Markets home</p>} />
      </Routes>
    </MemoryRouter>
  );
}

async function fill({ fullName = 'Jane Trader', email = 'jane@example.com', password = 'Secret123', confirm = password } = {}) {
  if (fullName) await userEvent.type(screen.getByLabelText('Full name'), fullName);
  if (email) await userEvent.type(screen.getByLabelText('Email'), email);
  if (password) await userEvent.type(screen.getByLabelText(/^Password/), password);
  if (confirm) await userEvent.type(screen.getByLabelText('Confirm password'), confirm);
  await userEvent.click(screen.getByRole('button', { name: 'Create account' }));
}

describe('Sign-up page', () => {
  beforeEach(() => {
    register.mockReset();
    useAuth.mockReturnValue({ register });
  });

  it.each([
    [{ fullName: '' }, 'Enter your full name.'],
    [{ email: 'jane-at-example' }, 'Enter a valid email address.'],
    [{ password: 'Ab1', confirm: 'Ab1' }, 'Password must be at least 8 characters.'],
    [{ password: 'abcdefgh', confirm: 'abcdefgh' }, 'Password needs at least one letter and one number.'],
    [{ password: '12345678', confirm: '12345678' }, 'Password needs at least one letter and one number.'],
    [{ confirm: 'Different1' }, /Passwords don.t match/],
  ])('validates %o before calling the server', async (fields, message) => {
    renderSignup();
    await fill(fields);
    expect(screen.getByRole('alert')).toHaveTextContent(message);
    expect(register).not.toHaveBeenCalled();
  });

  it('creates the account and signs the user in', async () => {
    register.mockResolvedValue({ fullName: 'Jane Trader' });
    renderSignup();

    await fill();

    expect(register).toHaveBeenCalledWith('Jane Trader', 'jane@example.com', 'Secret123');
    expect(await screen.findByText('Markets home')).toBeInTheDocument();
  });

  it('shows a duplicate-email error from the server', async () => {
    register.mockRejectedValue({ response: { status: 409, data: { message: 'An account with this email already exists' } } });
    renderSignup();

    await fill();

    expect(await screen.findByRole('alert')).toHaveTextContent('already exists');
  });
});
