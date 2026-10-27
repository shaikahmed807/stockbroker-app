import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter, Route, Routes, useLocation } from 'react-router-dom';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import Navbar from '../components/Navbar';
import TickerTape from '../components/TickerTape';
import { useAuth } from '../context/AuthContext';
import { useMarket } from '../context/MarketContext';
import { FREE_USER, PREMIUM_USER, QUOTES } from '../test/fixtures';

vi.mock('../context/AuthContext', () => ({ useAuth: vi.fn() }));
vi.mock('../context/MarketContext', () => ({ useMarket: vi.fn() }));

function LoginPage() {
  const { state } = useLocation();
  return <p>Login page {state?.signedOut ? '(signed out)' : ''}</p>;
}

function renderNav() {
  render(
    <MemoryRouter>
      <Routes>
        <Route path="/" element={<Navbar />} />
        <Route path="/login" element={<LoginPage />} />
      </Routes>
    </MemoryRouter>
  );
}

const logout = vi.fn();

describe('Navbar', () => {
  beforeEach(() => {
    logout.mockReset().mockResolvedValue(undefined);
    useAuth.mockReturnValue({ user: FREE_USER, isPremium: false, logout });
    useMarket.mockReturnValue({ status: 'live', quotes: QUOTES });
  });

  it('shows initials, first name and live feed status', () => {
    renderNav();
    expect(screen.getByText('JT')).toBeInTheDocument();
    expect(screen.getByText('Jane')).toBeInTheDocument();
    expect(screen.getByTitle('Live price feed')).toHaveTextContent('Live');
  });

  it.each([
    ['connecting', 'Connecting'],
    ['offline', 'Reconnecting'],
  ])('shows feed status %s', (status, label) => {
    useMarket.mockReturnValue({ status });
    renderNav();
    expect(screen.getByTitle('Live price feed')).toHaveTextContent(label);
  });

  it('offers Premium to free users', () => {
    renderNav();
    expect(screen.getByRole('link', { name: 'Go Premium' })).toHaveAttribute('href', '/premium');
  });

  it('shows "My plan" for Premium users', async () => {
    useAuth.mockReturnValue({ user: PREMIUM_USER, isPremium: true, logout });
    renderNav();
    expect(screen.getByRole('link', { name: 'My plan' })).toBeInTheDocument();
    await userEvent.click(screen.getByRole('button', { name: /Jane/ }));
    expect(screen.getByText('Premium')).toBeInTheDocument();
  });

  it('opens the account menu and signs out', async () => {
    renderNav();
    const accountButton = screen.getByRole('button', { name: /Jane/ });
    expect(accountButton).toHaveAttribute('aria-expanded', 'false');

    await userEvent.click(accountButton);
    expect(accountButton).toHaveAttribute('aria-expanded', 'true');
    expect(screen.getByText('jane@example.com')).toBeInTheDocument();
    expect(screen.getByText('Free plan')).toBeInTheDocument();

    await userEvent.click(screen.getByRole('menuitem', { name: 'Sign out' }));
    expect(logout).toHaveBeenCalledTimes(1);
    expect(await screen.findByText(/Login page \(signed out\)/)).toBeInTheDocument();
  });

  it('closes the menu when clicking elsewhere', async () => {
    renderNav();
    await userEvent.click(screen.getByRole('button', { name: /Jane/ }));
    expect(screen.getByRole('menu')).toBeInTheDocument();
    await userEvent.click(document.body);
    expect(screen.queryByRole('menu')).not.toBeInTheDocument();
  });
});

describe('TickerTape', () => {
  it('renders every stock twice for the seamless scroll loop', () => {
    useMarket.mockReturnValue({ quotes: QUOTES });
    render(
      <MemoryRouter>
        <TickerTape />
      </MemoryRouter>
    );
    expect(screen.getAllByText('TCS')).toHaveLength(2);
    expect(screen.getAllByRole('link').filter((a) => a.getAttribute('href') === '/stocks/SBIN')).toHaveLength(2);
  });

  it('marks rising and falling stocks', () => {
    useMarket.mockReturnValue({ quotes: QUOTES });
    const { container } = render(
      <MemoryRouter>
        <TickerTape />
      </MemoryRouter>
    );
    expect(container.querySelectorAll('.tape-item.up')).toHaveLength(4); // TCS + SBIN, twice
    expect(container.querySelectorAll('.tape-item.down')).toHaveLength(2); // INFY, twice
  });

  it('renders an empty strip before data arrives', () => {
    useMarket.mockReturnValue({ quotes: {} });
    const { container } = render(
      <MemoryRouter>
        <TickerTape />
      </MemoryRouter>
    );
    expect(container.querySelector('.tape-empty')).not.toBeNull();
  });
});
