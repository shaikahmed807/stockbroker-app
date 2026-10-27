import { render, screen, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter, Route, Routes, useParams } from 'react-router-dom';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import Dashboard from '../pages/Dashboard';
import { useAuth } from '../context/AuthContext';
import { useMarket } from '../context/MarketContext';
import { FREE_USER, PREMIUM_USER, QUOTES } from '../test/fixtures';

vi.mock('../context/AuthContext', () => ({ useAuth: vi.fn() }));
vi.mock('../context/MarketContext', () => ({ useMarket: vi.fn() }));

function Detail() {
  const { symbol } = useParams();
  return <p>Detail page for {symbol}</p>;
}

function renderDashboard() {
  render(
    <MemoryRouter>
      <Routes>
        <Route path="/" element={<Dashboard />} />
        <Route path="/stocks/:symbol" element={<Detail />} />
      </Routes>
    </MemoryRouter>
  );
}

const dataRows = () => screen.getAllByRole('row').slice(1); // skip header

describe('Markets dashboard', () => {
  beforeEach(() => {
    useAuth.mockReturnValue({ user: FREE_USER, isPremium: false });
    useMarket.mockReturnValue({ quotes: QUOTES, error: '' });
  });

  it('greets the user and lists every stock', () => {
    renderDashboard();
    expect(screen.getByText(/Hello Jane/)).toBeInTheDocument();
    expect(dataRows()).toHaveLength(3);
  });

  it('summarises market breadth', () => {
    renderDashboard();
    const adv = screen.getByText('Advancing').closest('div');
    const dec = screen.getByText('Declining').closest('div');
    expect(within(adv).getByText('2')).toBeInTheDocument();
    expect(within(dec).getByText('1')).toBeInTheDocument();
    expect(within(screen.getByText('Top gainer').closest('div')).getByText('SBIN')).toBeInTheDocument();
    expect(within(screen.getByText('Top loser').closest('div')).getByText('INFY')).toBeInTheDocument();
  });

  it('searches by symbol or company name', async () => {
    renderDashboard();
    const search = screen.getByRole('searchbox', { name: 'Search stocks' });

    await userEvent.type(search, 'infosys');
    expect(dataRows()).toHaveLength(1);
    expect(within(dataRows()[0]).getByText('INFY')).toBeInTheDocument();

    await userEvent.clear(search);
    await userEvent.type(search, 'zzz');
    expect(screen.getByText(/No stock matches/)).toBeInTheDocument();
  });

  it('sorts by gainers and losers', async () => {
    renderDashboard();

    await userEvent.click(screen.getByRole('button', { name: 'Gainers' }));
    expect(within(dataRows()[0]).getByText('SBIN')).toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'Gainers' })).toHaveClass('on');

    await userEvent.click(screen.getByRole('button', { name: 'Losers' }));
    expect(within(dataRows()[0]).getByText('INFY')).toBeInTheDocument();

    await userEvent.click(screen.getByRole('button', { name: 'A–Z' }));
    expect(within(dataRows()[0]).getByText('INFY')).toBeInTheDocument();
    expect(within(dataRows()[2]).getByText('TCS')).toBeInTheDocument();
  });

  it('colours price changes by direction', () => {
    renderDashboard();
    const tcsRow = dataRows().find((r) => within(r).queryByText('TCS'));
    const infyRow = dataRows().find((r) => within(r).queryByText('INFY'));
    expect(tcsRow.querySelector('td.up')).not.toBeNull();
    expect(infyRow.querySelector('td.down')).not.toBeNull();
  });

  it('opens the stock page when a row is clicked', async () => {
    renderDashboard();
    await userEvent.click(within(dataRows()[0]).getByText('Infosys Ltd'));
    expect(await screen.findByText('Detail page for INFY')).toBeInTheDocument();
  });

  it('promotes Premium to free users only', () => {
    renderDashboard();
    expect(screen.getByRole('link', { name: 'See Premium plans' })).toHaveAttribute('href', '/premium');
  });

  it('hides the upsell for Premium users', () => {
    useAuth.mockReturnValue({ user: PREMIUM_USER, isPremium: true });
    renderDashboard();
    expect(screen.queryByText('See Premium plans')).not.toBeInTheDocument();
  });

  it('shows loading and error states', () => {
    useMarket.mockReturnValue({ quotes: {}, error: '' });
    const { unmount } = render(
      <MemoryRouter>
        <Dashboard />
      </MemoryRouter>
    );
    expect(screen.getByText(/Loading market data/)).toBeInTheDocument();
    unmount();

    useMarket.mockReturnValue({ quotes: {}, error: 'Could not load stocks. Check that the backend is running.' });
    renderDashboard();
    expect(screen.getByText(/Could not load stocks/)).toBeInTheDocument();
  });
});
