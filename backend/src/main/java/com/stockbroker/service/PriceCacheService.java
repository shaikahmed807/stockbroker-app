package com.stockbroker.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.stockbroker.dto.StockDtos.PricePoint;
import com.stockbroker.dto.StockDtos.StockQuote;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

/**
 * Redis is the hot store for market data:
 *   stock:quote:{SYM}      -> latest quote (JSON string)
 *   stock:history:{SYM}    -> list of recent price points, oldest first, capped at history-size
 *   stock:analysis:{SYM}:{tier} -> short-lived cached analysis result
 */
@Service
public class PriceCacheService {

    private static final Logger log = LoggerFactory.getLogger(PriceCacheService.class);

    private final StringRedisTemplate redis;
    private final ObjectMapper mapper;
    private final int historySize;

    public PriceCacheService(StringRedisTemplate redis, ObjectMapper mapper,
                             @Value("${app.market.history-size}") int historySize) {
        this.redis = redis;
        this.mapper = mapper;
        this.historySize = historySize;
    }

    // ---------------- quotes ----------------

    public void saveQuote(StockQuote quote) {
        redis.opsForValue().set(quoteKey(quote.symbol()), write(quote));
    }

    public Optional<StockQuote> getQuote(String symbol) {
        String json = redis.opsForValue().get(quoteKey(symbol));
        return Optional.ofNullable(read(json, StockQuote.class));
    }

    public List<StockQuote> getQuotes(Collection<String> symbols) {
        List<String> keys = symbols.stream().map(this::quoteKey).toList();
        List<String> values = redis.opsForValue().multiGet(keys);
        List<StockQuote> result = new ArrayList<>();
        if (values != null) {
            for (String v : values) {
                StockQuote q = read(v, StockQuote.class);
                if (q != null) result.add(q);
            }
        }
        return result;
    }

    // ---------------- history ----------------

    public void appendHistory(String symbol, PricePoint point) {
        String key = historyKey(symbol);
        redis.opsForList().rightPush(key, write(point));
        redis.opsForList().trim(key, -historySize, -1);
    }

    public void appendHistoryBatch(String symbol, List<PricePoint> points) {
        String key = historyKey(symbol);
        List<String> json = points.stream().map(this::write).toList();
        redis.opsForList().rightPushAll(key, json);
        redis.opsForList().trim(key, -historySize, -1);
    }

    public long historyLength(String symbol) {
        Long size = redis.opsForList().size(historyKey(symbol));
        return size == null ? 0 : size;
    }

    /** Most recent {@code limit} points, oldest first. */
    public List<PricePoint> getHistory(String symbol, int limit) {
        List<String> raw = redis.opsForList().range(historyKey(symbol), -limit, -1);
        List<PricePoint> points = new ArrayList<>();
        if (raw != null) {
            for (String r : raw) {
                PricePoint p = read(r, PricePoint.class);
                if (p != null) points.add(p);
            }
        }
        return points;
    }

    // ---------------- analysis cache ----------------

    public <T> Optional<T> getCached(String key, Class<T> type) {
        return Optional.ofNullable(read(redis.opsForValue().get(key), type));
    }

    public void putCached(String key, Object value, Duration ttl) {
        redis.opsForValue().set(key, write(value), ttl);
    }

    // ---------------- helpers ----------------

    private String quoteKey(String symbol) { return "stock:quote:" + symbol; }

    private String historyKey(String symbol) { return "stock:history:" + symbol; }

    private String write(Object o) {
        try {
            return mapper.writeValueAsString(o);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Could not serialise " + o.getClass().getSimpleName(), e);
        }
    }

    private <T> T read(String json, Class<T> type) {
        if (json == null) return null;
        try {
            return mapper.readValue(json, type);
        } catch (JsonProcessingException e) {
            log.warn("Skipping unreadable cache entry for {}: {}", type.getSimpleName(), e.getMessage());
            return null;
        }
    }
}
