import { act, render, screen, waitFor } from '@testing-library/react';
import { useEffect } from 'react';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { MarketProvider, useMarket } from '../context/MarketContext';
import { stockApi } from '../api/client';
import { QUOTES, quote } from '../test/fixtures';

// Capture the STOMP client the provider creates so the test can play the broker.
const stomp = vi.hoisted(() => ({ instance: null }));

vi.mock('@stomp/stompjs', () => ({
  Client: vi.fn(function Client(options) {
    stomp.instance = {
      options,
      activate: vi.fn(),
      deactivate: vi.fn(),
      subscribe: vi.fn(),
    };
    return stomp.instance;
  }),
}));

vi.mock('../api/client', async (importOriginal) => {
  const actual = await importOriginal();
  return { ...actual, stockApi: { list: vi.fn(), quote: vi.fn(), history: vi.fn(), analysis: vi.fn() } };
});

const ticks = [];

function Probe() {
  const { quotes, status, error, onTick } = useMarket();
  useEffect(() => onTick((q) => ticks.push(q)), [onTick]);
  return (
    <div>
      <p>status: {status}</p>
      <p>error: {error || 'none'}</p>
      {Object.values(quotes).map((q) => (
        <p key={q.symbol}>
          {q.symbol} @ {q.price}
        </p>
      ))}
    </div>
  );
}

const renderProvider = () =>
  render(
    <MarketProvider>
      <Probe />
    </MarketProvider>
  );

describe('MarketContext (live prices over WebSocket)', () => {
  beforeEach(() => {
    ticks.length = 0;
    stomp.instance = null;
    stockApi.list.mockResolvedValue(Object.values(QUOTES));
  });

  it('loads the stock list and connects to the price feed', async () => {
    renderProvider();

    expect(screen.getByText('status: connecting')).toBeInTheDocument();
    expect(await screen.findByText('TCS @ 4120')).toBeInTheDocument();
    expect(stomp.instance.activate).toHaveBeenCalled();
    expect(stomp.instance.options.brokerURL).toMatch(/^ws:\/\/.+\/ws$/);
  });

  it('goes live and applies streamed quotes', async () => {
    renderProvider();
    await screen.findByText('TCS @ 4120');

    act(() => stomp.instance.options.onConnect());
    expect(screen.getByText('status: live')).toBeInTheDocument();
    expect(stomp.instance.subscribe).toHaveBeenCalledWith('/topic/prices', expect.any(Function));

    const onMessage = stomp.instance.subscribe.mock.calls[0][1];
    act(() => onMessage({ body: JSON.stringify(quote('TCS', 4150, 1.5)) }));

    expect(screen.getByText('TCS @ 4150')).toBeInTheDocument();
    expect(ticks).toHaveLength(1);
    expect(ticks[0].symbol).toBe('TCS');
  });

  it('shows reconnecting state when the socket drops', async () => {
    renderProvider();
    act(() => stomp.instance.options.onWebSocketClose());
    expect(await screen.findByText('status: offline')).toBeInTheDocument();
  });

  it('reports when the stock list cannot be loaded', async () => {
    stockApi.list.mockRejectedValue(new Error('down'));
    renderProvider();
    await waitFor(() => expect(screen.getByText(/error: Could not load stocks/)).toBeInTheDocument());
  });

  it('disconnects when unmounted', () => {
    const { unmount } = renderProvider();
    unmount();
    expect(stomp.instance.deactivate).toHaveBeenCalled();
  });
});
