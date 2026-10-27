package com.stockbroker.service;

import com.stockbroker.TestData;
import com.stockbroker.dto.StockDtos.PricePoint;
import com.stockbroker.dto.StockDtos.StockQuote;
import com.stockbroker.dto.StockDtos.StockTickEvent;
import com.stockbroker.model.PriceTick;
import com.stockbroker.model.Stock;
import com.stockbroker.repository.PriceTickRepository;
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
import org.springframework.messaging.simp.SimpMessagingTemplate;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("MarketDataConsumer – Kafka tick → Redis + MySQL + WebSocket")
class MarketDataConsumerTest {

    @Mock PriceCacheService cache;
    @Mock StockRepository stocks;
    @Mock PriceTickRepository ticks;
    @Mock SimpMessagingTemplate websocket;

    MarketDataConsumer consumer;
    Stock tcs;

    @BeforeEach
    void setUp() {
        consumer = new MarketDataConsumer(cache, stocks, ticks, websocket);
        tcs = TestData.tcs();
        when(stocks.findById("TCS")).thenReturn(Optional.of(tcs));
        when(stocks.findById("NOPE")).thenReturn(Optional.empty());
    }

    private static StockTickEvent tick(String symbol, double price) {
        return new StockTickEvent(symbol, price, 4095, 4125, 4090, 4100, 50_000, 1_730_000_000_000L);
    }

    @Test
    void tickUpdatesRedisMysqlAndPushesToBrowsers() {
        consumer.onTick(tick("TCS", 4120));

        ArgumentCaptor<StockQuote> quote = ArgumentCaptor.forClass(StockQuote.class);
        verify(cache).saveQuote(quote.capture());
        assertThat(quote.getValue().companyName()).isEqualTo("Tata Consultancy Services Ltd");
        assertThat(quote.getValue().change()).isEqualTo(20.0);
        assertThat(quote.getValue().changePercent()).isEqualTo(0.49);

        verify(cache).appendHistory("TCS", new PricePoint(1_730_000_000_000L, 4120, 50_000));

        ArgumentCaptor<PriceTick> archived = ArgumentCaptor.forClass(PriceTick.class);
        verify(ticks).save(archived.capture());
        assertThat(archived.getValue().getPrice()).isEqualTo(4120);
        assertThat(tcs.getLastPrice()).isEqualTo(4120);
        assertThat(tcs.getDayHigh()).isEqualTo(4125);

        verify(websocket).convertAndSend(eq("/topic/prices"), any(Object.class));
        verify(websocket).convertAndSend(eq("/topic/prices/TCS"), any(Object.class));
    }

    @Test
    void unknownSymbolIsIgnored() {
        consumer.onTick(tick("NOPE", 10));

        verifyNoInteractions(cache, ticks, websocket);
    }

    @Test
    void nullTickIsIgnored() {
        consumer.onTick(null);
        consumer.onTick(new StockTickEvent(null, 1, 1, 1, 1, 1, 1, 1));

        verifyNoInteractions(cache, stocks, ticks, websocket);
    }

    @Test
    void redisOutageStillArchivesAndStreams() {
        doThrow(new RuntimeException("redis down")).when(cache).saveQuote(any());

        consumer.onTick(tick("TCS", 4120));

        verify(ticks).save(any(PriceTick.class));
        verify(websocket, times(2)).convertAndSend(anyString(), any(Object.class));
    }

    @Test
    void stockMetadataIsLookedUpOncePerSymbolForQuotes() {
        consumer.onTick(tick("TCS", 4120));
        consumer.onTick(tick("TCS", 4121));

        // 1 lookup for cached metadata + 1 per tick for the MySQL price update
        verify(stocks, times(3)).findById("TCS");
    }
}
