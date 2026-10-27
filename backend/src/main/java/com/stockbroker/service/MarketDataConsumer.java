package com.stockbroker.service;

import com.stockbroker.dto.StockDtos.PricePoint;
import com.stockbroker.dto.StockDtos.StockQuote;
import com.stockbroker.dto.StockDtos.StockTickEvent;
import com.stockbroker.model.PriceTick;
import com.stockbroker.model.Stock;
import com.stockbroker.repository.PriceTickRepository;
import com.stockbroker.repository.StockRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Kafka CONSUMER for the "stock-ticks" topic. For each tick it:
 *   1. updates the latest quote + history in Redis
 *   2. archives the tick in MySQL (Hibernate)
 *   3. pushes the quote to browsers over WebSocket
 */
@Service
public class MarketDataConsumer {

    private static final Logger log = LoggerFactory.getLogger(MarketDataConsumer.class);

    private final PriceCacheService priceCache;
    private final StockRepository stockRepository;
    private final PriceTickRepository priceTickRepository;
    private final SimpMessagingTemplate websocket;
    private final Map<String, Stock> stockMeta = new ConcurrentHashMap<>();

    public MarketDataConsumer(PriceCacheService priceCache, StockRepository stockRepository,
                              PriceTickRepository priceTickRepository, SimpMessagingTemplate websocket) {
        this.priceCache = priceCache;
        this.stockRepository = stockRepository;
        this.priceTickRepository = priceTickRepository;
        this.websocket = websocket;
    }

    @KafkaListener(
            topics = "${app.kafka.topics.stock-ticks}",
            groupId = "stockbroker-market-data",
            properties = {"spring.json.value.default.type=com.stockbroker.dto.StockDtos$StockTickEvent"})
    @Transactional
    public void onTick(StockTickEvent tick) {
        if (tick == null || tick.symbol() == null) return;

        Stock meta = stockMeta.computeIfAbsent(tick.symbol(),
                s -> stockRepository.findById(s).orElse(null));
        if (meta == null) {
            log.debug("Ignoring tick for unknown symbol {}", tick.symbol());
            stockMeta.remove(tick.symbol());
            return;
        }

        double change = tick.price() - tick.previousClose();
        double changePct = tick.previousClose() == 0 ? 0 : change / tick.previousClose() * 100;
        StockQuote quote = new StockQuote(tick.symbol(), meta.getCompanyName(), meta.getSector(),
                meta.getExchange(), tick.price(), round(change), round(changePct),
                tick.open(), tick.high(), tick.low(), tick.previousClose(), tick.volume(), tick.timestamp());

        // 1. Redis
        try {
            priceCache.saveQuote(quote);
            priceCache.appendHistory(tick.symbol(), new PricePoint(tick.timestamp(), tick.price(), tick.volume()));
        } catch (Exception e) {
            log.warn("Redis write failed for {}: {}", tick.symbol(), e.getMessage());
        }

        // 2. MySQL
        Instant ts = Instant.ofEpochMilli(tick.timestamp());
        priceTickRepository.save(new PriceTick(tick.symbol(), tick.price(), tick.volume(), ts));
        stockRepository.findById(tick.symbol()).ifPresent(stock -> {
            stock.setLastPrice(tick.price());
            stock.setDayOpen(tick.open());
            stock.setDayHigh(tick.high());
            stock.setDayLow(tick.low());
            stock.setPreviousClose(tick.previousClose());
            stock.setUpdatedAt(ts);
        });

        // 3. WebSocket
        websocket.convertAndSend("/topic/prices", quote);
        websocket.convertAndSend("/topic/prices/" + tick.symbol(), quote);
    }

    private static double round(double v) {
        return Math.round(v * 100.0) / 100.0;
    }
}
