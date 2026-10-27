package com.stockbroker.service;

import com.stockbroker.dto.StockDtos.PricePoint;
import com.stockbroker.dto.StockDtos.StockTickEvent;
import com.stockbroker.model.Stock;
import com.stockbroker.repository.StockRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Simulated exchange feed (Kafka PRODUCER).
 *
 * Every tick it moves each stock's price with a geometric random walk and publishes a
 * {@link StockTickEvent} to the "stock-ticks" topic, keyed by symbol.
 *
 * To use a real market-data vendor instead, disable this bean
 * (MARKET_SIMULATOR_ENABLED=false) and publish the vendor's ticks to the same topic.
 */
@Service
public class MarketDataSimulator {

    private static final Logger log = LoggerFactory.getLogger(MarketDataSimulator.class);
    private static final int BACKFILL_POINTS = 120;

    private final StockRepository stockRepository;
    private final KafkaTemplate<String, Object> kafkaTemplate;
    private final PriceCacheService priceCache;
    private final String topic;
    private final boolean enabled;
    private final long tickIntervalMs;

    private final Map<String, SimState> state = new ConcurrentHashMap<>();
    private final Random random = new Random();

    public MarketDataSimulator(StockRepository stockRepository,
                               KafkaTemplate<String, Object> kafkaTemplate,
                               PriceCacheService priceCache,
                               @Value("${app.kafka.topics.stock-ticks}") String topic,
                               @Value("${app.market.simulator-enabled}") boolean enabled,
                               @Value("${app.market.tick-interval-ms}") long tickIntervalMs) {
        this.stockRepository = stockRepository;
        this.kafkaTemplate = kafkaTemplate;
        this.priceCache = priceCache;
        this.topic = topic;
        this.enabled = enabled;
        this.tickIntervalMs = tickIntervalMs;
    }

    @Scheduled(fixedRateString = "${app.market.tick-interval-ms}", initialDelay = 5000)
    public void publishTicks() {
        if (!enabled) return;
        if (state.isEmpty() && !initialise()) return;

        long now = System.currentTimeMillis();
        for (SimState s : state.values()) {
            s.step(random);
            StockTickEvent event = new StockTickEvent(s.symbol, round(s.price), round(s.open),
                    round(s.high), round(s.low), round(s.previousClose), s.volume, now);
            kafkaTemplate.send(topic, s.symbol, event).whenComplete((res, ex) -> {
                if (ex != null) log.warn("Failed to publish tick for {}: {}", s.symbol, ex.getMessage());
            });
        }
    }

    /** Loads stocks and backfills Redis history so indicators have data from the first second. */
    private synchronized boolean initialise() {
        if (!state.isEmpty()) return true;
        List<Stock> stocks = stockRepository.findAll();
        if (stocks.isEmpty()) return false;

        for (Stock stock : stocks) {
            double start = stock.getLastPrice() > 0 ? stock.getLastPrice() : stock.getBasePrice();
            SimState s = new SimState(stock.getSymbol(), start, volatilityFor(stock.getSector()));

            try {
                if (priceCache.historyLength(stock.getSymbol()) < 60) {
                    s.price = backfill(s, start);
                }
            } catch (Exception e) {
                log.warn("Could not backfill history for {} (is Redis running?): {}", stock.getSymbol(), e.getMessage());
            }
            s.open = s.high = s.low = s.price;
            s.previousClose = round(start * (1 + (random.nextGaussian() * 0.008)));
            state.put(stock.getSymbol(), s);
        }
        log.info("Market simulator started for {} stocks (tick every {} ms)", state.size(), tickIntervalMs);
        return true;
    }

    private double backfill(SimState s, double endPrice) {
        // Walk backwards from the current price so the series ends where live ticks begin.
        double[] prices = new double[BACKFILL_POINTS];
        prices[BACKFILL_POINTS - 1] = endPrice;
        for (int i = BACKFILL_POINTS - 2; i >= 0; i--) {
            prices[i] = prices[i + 1] / Math.exp(s.sigma * random.nextGaussian());
        }
        long now = System.currentTimeMillis();
        List<PricePoint> points = new ArrayList<>(BACKFILL_POINTS);
        for (int i = 0; i < BACKFILL_POINTS; i++) {
            long ts = now - (long) (BACKFILL_POINTS - i) * tickIntervalMs;
            points.add(new PricePoint(ts, round(prices[i]), 1000 + random.nextInt(9000)));
        }
        priceCache.appendHistoryBatch(s.symbol, points);
        return endPrice;
    }

    private static double volatilityFor(String sector) {
        if (sector == null) return 0.0020;
        return switch (sector) {
            case "Automobile", "Metals" -> 0.0030;
            case "Information Technology" -> 0.0022;
            case "FMCG" -> 0.0012;
            default -> 0.0018;
        };
    }

    private static double round(double v) {
        return Math.round(v * 100.0) / 100.0;
    }

    private static final class SimState {
        final String symbol;
        final double sigma;
        double price, open, high, low, previousClose;
        long volume;
        double drift;

        SimState(String symbol, double price, double sigma) {
            this.symbol = symbol;
            this.price = price;
            this.sigma = sigma;
        }

        void step(Random r) {
            // drift slowly changes direction to create visible trends
            drift = 0.95 * drift + 0.05 * r.nextGaussian() * sigma;
            price = price * Math.exp(drift + sigma * r.nextGaussian());
            high = Math.max(high, price);
            low = Math.min(low, price);
            volume += 100 + r.nextInt(5000);
        }
    }
}
