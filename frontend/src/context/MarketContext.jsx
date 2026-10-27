import { createContext, useContext, useEffect, useRef, useState } from 'react';
import { Client } from '@stomp/stompjs';
import { stockApi } from '../api/client';

const MarketContext = createContext(null);

function wsUrl() {
  const base = import.meta.env.VITE_WS_URL;
  if (base) return base;
  const proto = window.location.protocol === 'https:' ? 'wss' : 'ws';
  return `${proto}://${window.location.host}/ws`;
}

/**
 * Loads the stock list once, then keeps every quote current from the
 * STOMP topic /topic/prices (fed by the Kafka consumer on the backend).
 */
export function MarketProvider({ children }) {
  const [quotes, setQuotes] = useState({});
  const [status, setStatus] = useState('connecting'); // connecting | live | offline
  const [error, setError] = useState('');
  const listeners = useRef(new Set());

  useEffect(() => {
    let cancelled = false;
    stockApi
      .list()
      .then((list) => {
        if (cancelled) return;
        setQuotes((prev) => {
          const next = { ...prev };
          list.forEach((q) => {
            if (!next[q.symbol]) next[q.symbol] = q;
          });
          return next;
        });
      })
      .catch(() => setError('Could not load stocks. Check that the backend is running.'));

    const client = new Client({
      brokerURL: wsUrl(),
      reconnectDelay: 3000,
      heartbeatIncoming: 10000,
      heartbeatOutgoing: 10000,
      onConnect: () => {
        setStatus('live');
        client.subscribe('/topic/prices', (msg) => {
          const q = JSON.parse(msg.body);
          setQuotes((prev) => ({ ...prev, [q.symbol]: { ...q, _prev: prev[q.symbol]?.price } }));
          listeners.current.forEach((fn) => fn(q));
        });
      },
      onWebSocketClose: () => setStatus('offline'),
      onStompError: () => setStatus('offline'),
    });
    client.activate();

    return () => {
      cancelled = true;
      client.deactivate();
    };
  }, []);

  /** Register a callback for every tick; returns an unsubscribe function. */
  const onTick = (fn) => {
    listeners.current.add(fn);
    return () => listeners.current.delete(fn);
  };

  return (
    <MarketContext.Provider value={{ quotes, status, error, onTick }}>{children}</MarketContext.Provider>
  );
}

export const useMarket = () => useContext(MarketContext);
