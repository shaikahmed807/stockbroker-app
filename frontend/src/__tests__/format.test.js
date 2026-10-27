import { describe, expect, it } from 'vitest';
import { date, direction, pct, price, rupees, signed, SIGNAL_LABEL, time, TREND_LABEL, volume } from '../utils/format';

describe('format helpers', () => {
  it('formats prices with Indian digit grouping and 2 decimals', () => {
    expect(price(1234.5)).toBe('1,234.50');
    expect(price(1234567.891)).toBe('12,34,567.89');
  });

  it('shows a dash for missing values', () => {
    expect(price(null)).toBe('—');
    expect(price(undefined)).toBe('—');
    expect(price(NaN)).toBe('—');
    expect(rupees(null)).toBe('—');
    expect(pct(null)).toBe('—');
    expect(signed(null)).toBe('—');
    expect(volume(null)).toBe('—');
    expect(date(null)).toBe('—');
  });

  it('prefixes rupee amounts', () => {
    expect(rupees(499)).toBe('₹499.00');
  });

  it('signs changes with a real minus sign', () => {
    expect(signed(12.5)).toBe('+12.50');
    expect(signed(-3)).toBe('−3.00');
    expect(signed(0)).toBe('0.00');
  });

  it('formats percentages', () => {
    expect(pct(1.234)).toBe('+1.23%');
    expect(pct(-0.5)).toBe('−0.50%');
    expect(pct(0)).toBe('0.00%');
  });

  it('maps a change to up / down / flat', () => {
    expect(direction(2)).toBe('up');
    expect(direction(-0.01)).toBe('down');
    expect(direction(0)).toBe('flat');
    expect(direction(undefined)).toBe('flat');
  });

  it('compacts large volumes', () => {
    expect(volume(1500000)).not.toBe('—');
    expect(volume(1500000).length).toBeLessThan('1500000'.length);
  });

  it('formats times and dates', () => {
    expect(time(1730000000000)).toMatch(/\d{1,2}:\d{2}:\d{2}/);
    expect(date('2026-01-15T10:00:00Z')).toContain('2026');
  });

  it('has a label for every signal and trend the backend sends', () => {
    ['STRONG_BUY', 'BUY', 'HOLD', 'SELL', 'STRONG_SELL'].forEach((s) => expect(SIGNAL_LABEL[s]).toBeTruthy());
    ['UP', 'DOWN', 'SIDEWAYS', 'INSUFFICIENT_DATA'].forEach((t) => expect(TREND_LABEL[t]).toBeTruthy());
  });
});
