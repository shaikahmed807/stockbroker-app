// Shared test data for component tests.

export const quote = (symbol, price, changePercent, extra = {}) => ({
  symbol,
  companyName: `${symbol} Limited`,
  sector: 'Information Technology',
  exchange: 'NSE',
  price,
  change: Math.round(price * changePercent) / 100,
  changePercent,
  open: price,
  high: price + 10,
  low: price - 10,
  previousClose: price - 5,
  volume: 125000,
  timestamp: 1730000000000,
  ...extra,
});

export const QUOTES = {
  TCS: quote('TCS', 4120, 1.2, { companyName: 'Tata Consultancy Services Ltd' }),
  INFY: quote('INFY', 1850, -0.8, { companyName: 'Infosys Ltd' }),
  SBIN: quote('SBIN', 820, 2.5, { companyName: 'State Bank of India', sector: 'Banking' }),
};

export const FREE_USER = {
  id: 1,
  fullName: 'Jane Trader',
  email: 'jane@example.com',
  role: 'USER',
  plan: 'FREE',
  premium: false,
};

export const PREMIUM_USER = { ...FREE_USER, plan: 'PREMIUM_MONTHLY', premium: true };

export const PLANS = [
  { code: 'FREE', name: 'Free', price: 0, currency: 'INR', durationDays: 0, features: ['Live prices'] },
  { code: 'PREMIUM_MONTHLY', name: 'Premium Monthly', price: 499, currency: 'INR', durationDays: 30, features: ['RSI, MACD'] },
  { code: 'PREMIUM_YEARLY', name: 'Premium Yearly', price: 4999, currency: 'INR', durationDays: 365, features: ['2 months free'] },
];

export const points = (n, start = 4100) =>
  Array.from({ length: n }, (_, i) => ({ timestamp: 1730000000000 + i * 2000, price: start + i, volume: 1000 }));

export const freeAnalysis = {
  symbol: 'TCS',
  companyName: 'Tata Consultancy Services Ltd',
  currentPrice: 4120,
  change: 20,
  changePercent: 0.49,
  dataPoints: 120,
  sma20: 4110,
  sma50: 4100,
  ema20: 4111,
  trend: 'UP',
  sessionHigh: 4130,
  sessionLow: 4090,
  premiumLocked: true,
};

export const premiumAnalysis = {
  ...freeAnalysis,
  premiumLocked: false,
  rsi14: 55.2,
  macd: 0.4182,
  macdSignal: 0.3,
  macdHistogram: 0.1182,
  bollingerUpper: 4140,
  bollingerMiddle: 4110,
  bollingerLower: 4080,
  volatilityPercent: 0.12,
  momentum10: 0.45,
  support: 4090,
  resistance: 4130,
  signal: 'BUY',
  signalScore: 35,
  reasons: ['MACD is above its signal line (bullish)', 'RSI 55.2: neutral momentum'],
};
