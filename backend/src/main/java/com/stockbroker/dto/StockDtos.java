package com.stockbroker.dto;

import java.util.List;

public final class StockDtos {

    private StockDtos() { }

    /** Published to Kafka topic "stock-ticks" by the market feed. */
    public record StockTickEvent(String symbol, double price, double open, double high, double low,
                                 double previousClose, long volume, long timestamp) { }

    /** Latest quote, cached in Redis and pushed to browsers over WebSocket. */
    public record StockQuote(String symbol, String companyName, String sector, String exchange,
                             double price, double change, double changePercent,
                             double open, double high, double low, double previousClose,
                             long volume, long timestamp) { }

    /** One point of price history (stored in a Redis list). */
    public record PricePoint(long timestamp, double price, long volume) { }

    /** Technical analysis result. Premium-only fields are null when premiumLocked = true. */
    public record AnalysisResponse(
            String symbol,
            String companyName,
            double currentPrice,
            double change,
            double changePercent,
            int dataPoints,
            long generatedAt,
            // ---- basic (free) ----
            Double sma20,
            Double sma50,
            Double ema20,
            String trend,
            double sessionHigh,
            double sessionLow,
            // ---- premium ----
            boolean premiumLocked,
            Double rsi14,
            Double macd,
            Double macdSignal,
            Double macdHistogram,
            Double bollingerUpper,
            Double bollingerMiddle,
            Double bollingerLower,
            Double volatilityPercent,
            Double momentum10,
            Double support,
            Double resistance,
            String signal,
            Integer signalScore,
            List<String> reasons
    ) { }
}
