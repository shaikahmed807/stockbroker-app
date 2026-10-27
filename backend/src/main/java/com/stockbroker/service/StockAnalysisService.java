package com.stockbroker.service;

import com.stockbroker.dto.StockDtos.AnalysisResponse;
import com.stockbroker.dto.StockDtos.PricePoint;
import com.stockbroker.dto.StockDtos.StockQuote;
import com.stockbroker.exception.ApiException;
import com.stockbroker.model.Stock;
import com.stockbroker.repository.StockRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static com.stockbroker.service.TechnicalIndicators.*;

/**
 * Live technical analysis computed from the Redis price window.
 * Results are cached in Redis for a few seconds (one tick) per stock and tier.
 */
@Service
public class StockAnalysisService {

    private static final Logger log = LoggerFactory.getLogger(StockAnalysisService.class);
    private static final Duration CACHE_TTL = Duration.ofSeconds(2);

    private final PriceCacheService priceCache;
    private final StockRepository stockRepository;

    public StockAnalysisService(PriceCacheService priceCache, StockRepository stockRepository) {
        this.priceCache = priceCache;
        this.stockRepository = stockRepository;
    }

    public AnalysisResponse analyse(String symbol, boolean premium) {
        String sym = symbol.toUpperCase();
        String cacheKey = "stock:analysis:" + sym + ":" + (premium ? "premium" : "free");

        Optional<AnalysisResponse> cached = safe(() -> priceCache.getCached(cacheKey, AnalysisResponse.class));
        if (cached != null && cached.isPresent()) return cached.get();

        Stock stock = stockRepository.findById(sym)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Unknown stock: " + sym));

        List<PricePoint> history = priceCache.getHistory(sym, 300);
        if (history.size() < 2) {
            throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE,
                    "Not enough market data yet for " + sym + ". Try again in a few seconds.");
        }
        List<Double> prices = history.stream().map(PricePoint::price).toList();
        double last = prices.get(prices.size() - 1);

        StockQuote quote = priceCache.getQuote(sym).orElse(null);
        double prevClose = quote != null ? quote.previousClose() : prices.get(0);
        double change = last - prevClose;
        double changePct = prevClose == 0 ? 0 : change / prevClose * 100;

        Double sma20 = sma(prices, 20);
        Double sma50 = sma(prices, 50);
        Double ema20 = ema(prices, 20);
        String trend = trend(last, sma20, sma50);

        AnalysisResponse response;
        if (!premium) {
            response = new AnalysisResponse(sym, stock.getCompanyName(), last, r(change), r(changePct),
                    prices.size(), System.currentTimeMillis(),
                    r(sma20), r(sma50), r(ema20), trend, max(prices, prices.size()), min(prices, prices.size()),
                    true, null, null, null, null, null, null, null, null, null, null, null, null, null, null);
        } else {
            Double rsi = rsi(prices, 14);
            double[] macd = macd(prices, 12, 26, 9);
            double[] bb = bollinger(prices, 20, 2);
            Double vol = volatility(prices, 60);
            Double mom = momentum(prices, 10);
            double support = min(prices, 50);
            double resistance = max(prices, 50);

            List<String> reasons = new ArrayList<>();
            int score = score(last, sma20, sma50, rsi, macd, bb, mom, reasons);

            response = new AnalysisResponse(sym, stock.getCompanyName(), last, r(change), r(changePct),
                    prices.size(), System.currentTimeMillis(),
                    r(sma20), r(sma50), r(ema20), trend, max(prices, prices.size()), min(prices, prices.size()),
                    false, r(rsi),
                    macd == null ? null : r4(macd[0]), macd == null ? null : r4(macd[1]), macd == null ? null : r4(macd[2]),
                    bb == null ? null : r(bb[0]), bb == null ? null : r(bb[1]), bb == null ? null : r(bb[2]),
                    vol == null ? null : r4(vol), r(mom), r(support), r(resistance),
                    signal(score), score, reasons);
        }

        safe(() -> { priceCache.putCached(cacheKey, response, CACHE_TTL); return null; });
        return response;
    }

    // ---------------- scoring ----------------

    /** Combines indicators into a score between -100 (strong sell) and +100 (strong buy). */
    private int score(double price, Double sma20, Double sma50, Double rsi, double[] macd,
                      double[] bb, Double mom, List<String> reasons) {
        int score = 0;

        if (sma20 != null && sma50 != null) {
            if (sma20 > sma50) { score += 20; reasons.add("SMA 20 is above SMA 50 (short-term uptrend)"); }
            else { score -= 20; reasons.add("SMA 20 is below SMA 50 (short-term downtrend)"); }
        }
        if (sma20 != null) {
            if (price > sma20) { score += 10; reasons.add("Price is trading above its 20-period average"); }
            else { score -= 10; reasons.add("Price is trading below its 20-period average"); }
        }
        if (rsi != null) {
            if (rsi < 30) { score += 25; reasons.add(String.format("RSI %.1f: oversold, rebound possible", rsi)); }
            else if (rsi > 70) { score -= 25; reasons.add(String.format("RSI %.1f: overbought, pullback risk", rsi)); }
            else reasons.add(String.format("RSI %.1f: neutral momentum", rsi));
        }
        if (macd != null) {
            if (macd[2] > 0) { score += 20; reasons.add("MACD is above its signal line (bullish)"); }
            else { score -= 20; reasons.add("MACD is below its signal line (bearish)"); }
        }
        if (bb != null) {
            if (price <= bb[2]) { score += 15; reasons.add("Price touched the lower Bollinger Band"); }
            else if (price >= bb[0]) { score -= 15; reasons.add("Price touched the upper Bollinger Band"); }
        }
        if (mom != null) {
            if (mom > 0.3) { score += 10; reasons.add(String.format("Positive momentum (+%.2f%% over 10 ticks)", mom)); }
            else if (mom < -0.3) { score -= 10; reasons.add(String.format("Negative momentum (%.2f%% over 10 ticks)", mom)); }
        }
        return Math.max(-100, Math.min(100, score));
    }

    private static String signal(int score) {
        if (score >= 50) return "STRONG_BUY";
        if (score >= 20) return "BUY";
        if (score <= -50) return "STRONG_SELL";
        if (score <= -20) return "SELL";
        return "HOLD";
    }

    private static String trend(double price, Double sma20, Double sma50) {
        if (sma20 == null) return "INSUFFICIENT_DATA";
        if (sma50 == null) return price >= sma20 ? "UP" : "DOWN";
        double gap = (sma20 - sma50) / sma50 * 100;
        if (gap > 0.05 && price > sma20) return "UP";
        if (gap < -0.05 && price < sma20) return "DOWN";
        return "SIDEWAYS";
    }

    // ---------------- helpers ----------------

    private static Double r(Double v) {
        return v == null ? null : Math.round(v * 100.0) / 100.0;
    }

    private static double r(double v) {
        return Math.round(v * 100.0) / 100.0;
    }

    private static Double r4(double v) {
        return Math.round(v * 10000.0) / 10000.0;
    }

    private <T> T safe(java.util.function.Supplier<T> action) {
        try {
            return action.get();
        } catch (Exception e) {
            log.debug("Analysis cache unavailable: {}", e.getMessage());
            return null;
        }
    }
}
