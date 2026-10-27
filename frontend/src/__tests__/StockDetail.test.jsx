import { act, render, screen } from '@testing-library/react';
import { MemoryRouter, Route, Routes } from 'react-router-dom';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import StockDetail from '../pages/StockDetail';
import { useAuth } from '../context/AuthContext';
import { useMarket } from '../context/MarketContext';
import { stockApi } from '../api/client';
import { freeAnalysis, points, premiumAnalysis, QUOTES } from '../test/fixtures';

vi.mock('../context/AuthContext', () => ({ useAuth: vi.fn() }));
vi.mock('../context/MarketContext', () => ({ useMarket: vi.fn() }));
vi.mock('../api/client', async (importOriginal) => {
  const actual = await importOriginal();
  return { ...actual, stockApi: { list: vi.fn(), quote: vi.fn(), history: vi.fn(), analysis: vi.fn() } };
});
// Recharts needs real layout; the chart itself is covered visually, here we only count points.
vi.mock('../components/PriceChart', () => ({
  default: (props) => <div data-testid="chart">{props.points.length} points</div>,
}));

let tickListener;

function renderStock(symbol = 'TCS') {
  render(
    <MemoryRouter initialEntries={[`/stocks/${symbol}`]}>
      <Routes>
        <Route path="/stocks/:symbol" element={<StockDetail />} />
      </Routes>
    </MemoryRouter>
  );
}

describe('Stock detail page', () => {
  beforeEach(() => {
    tickListener = null;
    useAuth.mockReturnValue({ isPremium: false });
    useMarket.mockReturnValue({
      quotes: QUOTES,
      onTick: (fn) => {
        tickListener = fn;
        return () => {
          tickListener = null;
        };
      },
    });
    stockApi.history.mockResolvedValue(points(5));
    stockApi.analysis.mockResolvedValue(freeAnalysis);
  });

  it('shows the live quote header', async () => {
    renderStock();
    expect(screen.getByRole('heading', { name: 'TCS' })).toBeInTheDocument();
    expect(screen.getByText('Tata Consultancy Services Ltd')).toBeInTheDocument();
    expect(screen.getByText('₹4,120.00')).toBeInTheDocument();
    expect(await screen.findByTestId('chart')).toHaveTextContent('5 points');
  });

  it('requests history and analysis for the symbol in the URL', async () => {
    renderStock('tcs');
    await screen.findByTestId('chart');
    expect(stockApi.history).toHaveBeenCalledWith('TCS', 300);
    expect(stockApi.analysis).toHaveBeenCalledWith('TCS');
  });

  it('appends live ticks to the chart', async () => {
    renderStock();
    await screen.findByText('5 points');

    act(() => tickListener({ symbol: 'TCS', timestamp: 1730000999999, price: 4130, volume: 10 }));
    expect(screen.getByTestId('chart')).toHaveTextContent('6 points');

    act(() => tickListener({ symbol: 'INFY', timestamp: 1730001999999, price: 1, volume: 1 }));
    act(() => tickListener({ symbol: 'TCS', timestamp: 1, price: 1, volume: 1 })); // stale tick
    expect(screen.getByTestId('chart')).toHaveTextContent('6 points');
  });

  it('locks advanced indicators for free users', async () => {
    renderStock();
    expect(await screen.findByText('Uptrend')).toBeInTheDocument();
    expect(screen.getByRole('heading', { name: 'Premium analysis' })).toBeInTheDocument();
    expect(screen.getByRole('link', { name: 'Upgrade to Premium' })).toHaveAttribute('href', '/premium');
    expect(screen.getByText(/Free plan charts show the last 60 ticks/)).toBeInTheDocument();
  });

  it('shows the signal and its reasoning to Premium users', async () => {
    useAuth.mockReturnValue({ isPremium: true });
    stockApi.analysis.mockResolvedValue(premiumAnalysis);
    renderStock();

    expect(await screen.findByText('Buy')).toBeInTheDocument();
    expect(screen.getByText('Score +35')).toBeInTheDocument();
    expect(screen.getByText('55.2')).toBeInTheDocument();
    expect(screen.getByText('MACD is above its signal line (bullish)')).toBeInTheDocument();
    expect(screen.queryByText('Upgrade to Premium')).not.toBeInTheDocument();
    expect(screen.queryByText(/Free plan charts/)).not.toBeInTheDocument();
  });

  it('shows an error for an unknown stock', async () => {
    stockApi.history.mockRejectedValue({ response: { data: { message: 'Unknown stock: XYZ' } } });
    stockApi.analysis.mockRejectedValue({ response: { status: 404, data: { message: 'Unknown stock: XYZ' } } });
    renderStock('XYZ');

    expect(await screen.findByText('Unknown stock: XYZ')).toBeInTheDocument();
    expect(screen.getByRole('link', { name: 'Back to markets' })).toHaveAttribute('href', '/');
  });
});
