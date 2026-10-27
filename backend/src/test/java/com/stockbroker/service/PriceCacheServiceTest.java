package com.stockbroker.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.stockbroker.TestData;
import com.stockbroker.dto.StockDtos.PricePoint;
import com.stockbroker.dto.StockDtos.StockQuote;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.data.redis.core.ListOperations;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.time.Duration;
import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("PriceCacheService – Redis key layout and JSON")
class PriceCacheServiceTest {

    @Mock StringRedisTemplate redis;
    @Mock ValueOperations<String, String> values;
    @Mock ListOperations<String, String> lists;

    private final ObjectMapper mapper = new ObjectMapper();
    PriceCacheService cache;

    @BeforeEach
    void setUp() {
        when(redis.opsForValue()).thenReturn(values);
        when(redis.opsForList()).thenReturn(lists);
        cache = new PriceCacheService(redis, mapper, 300);
    }

    private String json(Object o) throws Exception {
        return mapper.writeValueAsString(o);
    }

    @Test
    void saveQuoteWritesJsonUnderQuoteKey() throws Exception {
        StockQuote q = TestData.quote("TCS", 4120, 4100);
        cache.saveQuote(q);

        ArgumentCaptor<String> body = ArgumentCaptor.forClass(String.class);
        verify(values).set(eq("stock:quote:TCS"), body.capture());
        assertThat(mapper.readValue(body.getValue(), StockQuote.class)).isEqualTo(q);
    }

    @Test
    void getQuoteRoundTrips() throws Exception {
        StockQuote q = TestData.quote("TCS", 4120, 4100);
        when(values.get("stock:quote:TCS")).thenReturn(json(q));

        assertThat(cache.getQuote("TCS")).contains(q);
    }

    @Test
    void missingOrCorruptQuoteIsEmpty() {
        when(values.get("stock:quote:A")).thenReturn(null);
        when(values.get("stock:quote:B")).thenReturn("{not json");

        assertThat(cache.getQuote("A")).isEmpty();
        assertThat(cache.getQuote("B")).isEmpty();
    }

    @Test
    void getQuotesSkipsMissingAndCorruptEntries() throws Exception {
        StockQuote tcs = TestData.quote("TCS", 4120, 4100);
        when(values.multiGet(List.of("stock:quote:TCS", "stock:quote:INFY", "stock:quote:X")))
                .thenReturn(Arrays.asList(json(tcs), null, "garbage"));

        assertThat(cache.getQuotes(List.of("TCS", "INFY", "X"))).containsExactly(tcs);
    }

    @Test
    void getQuotesHandlesNullFromRedis() {
        when(values.multiGet(anyCollection())).thenReturn(null);
        assertThat(cache.getQuotes(List.of("TCS"))).isEmpty();
    }

    @Test
    void appendHistoryPushesAndTrimsToWindowSize() {
        cache.appendHistory("TCS", new PricePoint(1L, 100.0, 10));

        verify(lists).rightPush(eq("stock:history:TCS"), anyString());
        verify(lists).trim("stock:history:TCS", -300, -1);
    }

    @Test
    void appendHistoryBatchPushesAllPoints() {
        cache.appendHistoryBatch("TCS", TestData.series(5, 100, 1));

        ArgumentCaptor<List<String>> pushed = ArgumentCaptor.forClass(List.class);
        verify(lists).rightPushAll(eq("stock:history:TCS"), pushed.capture());
        assertThat(pushed.getValue()).hasSize(5);
        verify(lists).trim("stock:history:TCS", -300, -1);
    }

    @Test
    void getHistoryReadsMostRecentPointsOldestFirst() throws Exception {
        List<PricePoint> pts = TestData.series(2, 100, 5);
        when(lists.range("stock:history:TCS", -2, -1)).thenReturn(List.of(json(pts.get(0)), json(pts.get(1))));

        assertThat(cache.getHistory("TCS", 2)).containsExactlyElementsOf(pts);
    }

    @Test
    void historyLengthDefaultsToZero() {
        when(lists.size("stock:history:TCS")).thenReturn(null);
        when(lists.size("stock:history:INFY")).thenReturn(42L);

        assertThat(cache.historyLength("TCS")).isZero();
        assertThat(cache.historyLength("INFY")).isEqualTo(42);
    }

    @Test
    void cachedValuesUseTtl() throws Exception {
        Duration ttl = Duration.ofSeconds(2);
        cache.putCached("k", new PricePoint(1L, 2.0, 3), ttl);
        verify(values).set("k", json(new PricePoint(1L, 2.0, 3)), ttl);

        when(values.get("k")).thenReturn(json(new PricePoint(1L, 2.0, 3)));
        assertThat(cache.getCached("k", PricePoint.class)).contains(new PricePoint(1L, 2.0, 3));
    }
}
