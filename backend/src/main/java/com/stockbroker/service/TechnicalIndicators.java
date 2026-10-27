package com.stockbroker.service;

import java.util.List;

/**
 * Pure functions for common technical indicators. Input lists are ordered oldest -> newest.
 * Each method returns null when there is not enough data.
 */
public final class TechnicalIndicators {

    private TechnicalIndicators() { }

    public static Double sma(List<Double> prices, int period) {
        if (prices.size() < period) return null;
        double sum = 0;
        for (int i = prices.size() - period; i < prices.size(); i++) sum += prices.get(i);
        return sum / period;
    }

    public static Double ema(List<Double> prices, int period) {
        double[] series = emaSeries(prices, period);
        return series == null ? null : series[series.length - 1];
    }

    /** EMA for every index from (period-1) onwards; earlier slots are NaN. */
    static double[] emaSeries(List<Double> prices, int period) {
        if (prices.size() < period) return null;
        double[] out = new double[prices.size()];
        double k = 2.0 / (period + 1);
        double seed = 0;
        for (int i = 0; i < period; i++) {
            seed += prices.get(i);
            out[i] = Double.NaN;
        }
        out[period - 1] = seed / period;
        for (int i = period; i < prices.size(); i++) {
            out[i] = prices.get(i) * k + out[i - 1] * (1 - k);
        }
        return out;
    }

    /** Wilder's RSI. */
    public static Double rsi(List<Double> prices, int period) {
        if (prices.size() <= period) return null;
        double gain = 0, loss = 0;
        for (int i = 1; i <= period; i++) {
            double d = prices.get(i) - prices.get(i - 1);
            if (d >= 0) gain += d; else loss -= d;
        }
        double avgGain = gain / period, avgLoss = loss / period;
        for (int i = period + 1; i < prices.size(); i++) {
            double d = prices.get(i) - prices.get(i - 1);
            avgGain = (avgGain * (period - 1) + Math.max(d, 0)) / period;
            avgLoss = (avgLoss * (period - 1) + Math.max(-d, 0)) / period;
        }
        if (avgLoss == 0) return 100.0;
        double rs = avgGain / avgLoss;
        return 100 - (100 / (1 + rs));
    }

    /** @return {macd, signal, histogram} or null. */
    public static double[] macd(List<Double> prices, int fast, int slow, int signal) {
        if (prices.size() < slow + signal) return null;
        double[] fastE = emaSeries(prices, fast);
        double[] slowE = emaSeries(prices, slow);
        if (fastE == null || slowE == null) return null;

        int start = slow - 1;
        int n = prices.size() - start;
        double[] macdLine = new double[n];
        for (int i = 0; i < n; i++) macdLine[i] = fastE[start + i] - slowE[start + i];

        double k = 2.0 / (signal + 1);
        double sig = 0;
        for (int i = 0; i < signal; i++) sig += macdLine[i];
        sig /= signal;
        for (int i = signal; i < n; i++) sig = macdLine[i] * k + sig * (1 - k);

        double last = macdLine[n - 1];
        return new double[]{last, sig, last - sig};
    }

    /** @return {upper, middle, lower} or null. */
    public static double[] bollinger(List<Double> prices, int period, double stdDevs) {
        Double mid = sma(prices, period);
        if (mid == null) return null;
        double var = 0;
        for (int i = prices.size() - period; i < prices.size(); i++) {
            var += Math.pow(prices.get(i) - mid, 2);
        }
        double sd = Math.sqrt(var / period);
        return new double[]{mid + stdDevs * sd, mid, mid - stdDevs * sd};
    }

    /** Standard deviation of tick-to-tick returns, in percent. */
    public static Double volatility(List<Double> prices, int lookback) {
        int n = Math.min(lookback, prices.size() - 1);
        if (n < 2) return null;
        double[] r = new double[n];
        int offset = prices.size() - n;
        for (int i = 0; i < n; i++) {
            double prev = prices.get(offset + i - 1);
            r[i] = (prices.get(offset + i) - prev) / prev;
        }
        double mean = 0;
        for (double v : r) mean += v;
        mean /= n;
        double var = 0;
        for (double v : r) var += (v - mean) * (v - mean);
        return Math.sqrt(var / (n - 1)) * 100;
    }

    /** Rate of change over {@code period} ticks, in percent. */
    public static Double momentum(List<Double> prices, int period) {
        if (prices.size() <= period) return null;
        double past = prices.get(prices.size() - 1 - period);
        return (prices.get(prices.size() - 1) - past) / past * 100;
    }

    public static double min(List<Double> prices, int lookback) {
        return prices.subList(Math.max(0, prices.size() - lookback), prices.size())
                .stream().mapToDouble(Double::doubleValue).min().orElse(0);
    }

    public static double max(List<Double> prices, int lookback) {
        return prices.subList(Math.max(0, prices.size() - lookback), prices.size())
                .stream().mapToDouble(Double::doubleValue).max().orElse(0);
    }
}
