package com.stockbroker.service;

import com.stockbroker.TestData;
import com.stockbroker.dto.StockDtos.PricePoint;
import com.stockbroker.dto.StockDtos.StockTickEvent;
import com.stockbroker.repository.StockRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.kafka.core.KafkaTemplate;

import java.util.List;
import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("MarketDataSimulator – Kafka producer of simulated ticks")
class MarketDataSimulatorTest {

    @Mock StockRepository stocks;
    @Mock KafkaTemplate<String, Object> kafka;
    @Mock PriceCacheService cache;

    @BeforeEach
    void setUp() {
        when(kafka.send(anyString(), anyString(), any())).thenReturn(CompletableFuture.completedFuture(null));
        when(stocks.findAll()).thenReturn(List.of(TestData.tcs(), TestData.infy()));
    }

    private MarketDataSimulator simulator(boolean enabled) {
        return new MarketDataSimulator(stocks, kafka, cache, "stock-ticks", enabled, 2000);
    }

    @Test
    void disabledSimulatorPublishesNothing() {
        simulator(false).publishTicks();

        verifyNoInteractions(stocks, kafka, cache);
    }

    @Test
    void publishesOneTickPerStockKeyedBySymbol() {
        simulator(true).publishTicks();

        ArgumentCaptor<Object> events = ArgumentCaptor.forClass(Object.class);
        verify(kafka).send(eq("stock-ticks"), eq("TCS"), events.capture());
        verify(kafka).send(eq("stock-ticks"), eq("INFY"), events.capture());
        for (Object e : events.getAllValues()) {
            StockTickEvent tick = (StockTickEvent) e;
            assertThat(tick.price()).isPositive();
            assertThat(tick.high()).isGreaterThanOrEqualTo(tick.low());
            assertThat(tick.volume()).isPositive();
        }
    }

    @Test
    void backfillsHistoryWhenRedisIsEmpty() {
        when(cache.historyLength(anyString())).thenReturn(0L);

        simulator(true).publishTicks();

        ArgumentCaptor<List<PricePoint>> points = ArgumentCaptor.forClass(List.class);
        verify(cache).appendHistoryBatch(eq("TCS"), points.capture());
        assertThat(points.getValue()).hasSize(120);
        assertThat(points.getValue().get(119).price()).isEqualTo(4100.0);
    }

    @Test
    void skipsBackfillWhenHistoryAlreadyExists() {
        when(cache.historyLength(anyString())).thenReturn(200L);

        simulator(true).publishTicks();

        verify(cache, never()).appendHistoryBatch(anyString(), anyList());
    }

    @Test
    void stockListIsLoadedOnlyOnce() {
        MarketDataSimulator sim = simulator(true);
        sim.publishTicks();
        sim.publishTicks();
        sim.publishTicks();

        verify(stocks, times(1)).findAll();
        verify(kafka, times(6)).send(anyString(), anyString(), any());
    }

    @Test
    void waitsUntilStocksAreSeeded() {
        when(stocks.findAll()).thenReturn(List.of());

        simulator(true).publishTicks();

        verifyNoInteractions(kafka);
    }
}
