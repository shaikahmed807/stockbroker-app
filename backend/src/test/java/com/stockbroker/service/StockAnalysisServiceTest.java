package com.stockbroker.service;

import com.stockbroker.TestData;
import com.stockbroker.dto.StockDtos.AnalysisResponse;
import com.stockbroker.exception.ApiException;
import com.stockbroker.repository.StockRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.http.HttpStatus;

import java.time.Duration;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("StockAnalysisService – Free vs Premium analysis")
class StockAnalysisServiceTest {

    @Mock PriceCacheService cache;
    @Mock StockRepository stocks;

    StockAnalysisService service;

    @BeforeEach
    void setUp() {
        service = new StockAnalysisService(cache, stocks);
        when(cache.getCached(anyString(), eq(AnalysisResponse.class))).thenReturn(Optional.empty());
        when(cache.getQuote(anyString())).thenReturn(Optional.empty());
        when(stocks.findById("TCS")).thenReturn(Optional.of(TestData.tcs()));
    }

    /** 60 steadily rising prices: 101, 102 ... 160. */
    private void risingHistory() {
        when(cache.getHistory("TCS", 300)).thenReturn(TestData.series(60, 101, 1));
    }

    @Test
    void freeTierGetsBasicIndicatorsAndPremiumFieldsAreLocked() {
        risingHistory();

        AnalysisResponse a = service.analyse("TCS", false);

        assertThat(a.premiumLocked()).isTrue();
        assertThat(a.currentPrice()).isEqualTo(160.0);
        assertThat(a.sma20()).isEqualTo(150.5);
        assertThat(a.sma50()).isEqualTo(135.5);
        assertThat(a.trend()).isEqualTo("UP");
        assertThat(a.sessionHigh()).isEqualTo(160.0);
        assertThat(a.sessionLow()).isEqualTo(101.0);
        assertThat(a.dataPoints()).isEqualTo(60);
        assertThat(a.rsi14()).isNull();
        assertThat(a.macd()).isNull();
        assertThat(a.signal()).isNull();
        assertThat(a.reasons()).isNull();
    }

    @Test
    void changeIsMeasuredAgainstPreviousCloseFromQuote() {
        risingHistory();
        when(cache.getQuote("TCS")).thenReturn(Optional.of(TestData.quote("TCS", 160, 150)));

        AnalysisResponse a = service.analyse("TCS", false);

        assertThat(a.change()).isEqualTo(10.0);
        assertThat(a.changePercent()).isEqualTo(6.67);
    }

    @Test
    void premiumTierGetsFullIndicatorSetAndSignal() {
        risingHistory();

        AnalysisResponse a = service.analyse("TCS", true);

        assertThat(a.premiumLocked()).isFalse();
        assertThat(a.rsi14()).isEqualTo(100.0);
        assertThat(a.macd()).isNotNull().isPositive();
        assertThat(a.bollingerUpper()).isGreaterThan(a.bollingerMiddle());
        assertThat(a.bollingerLower()).isLessThan(a.bollingerMiddle());
        assertThat(a.support()).isEqualTo(111.0);
        assertThat(a.resistance()).isEqualTo(160.0);
        assertThat(a.signal()).isIn("STRONG_BUY", "BUY", "HOLD", "SELL", "STRONG_SELL");
        assertThat(a.signalScore()).isBetween(-100, 100);
        assertThat(a.reasons()).isNotEmpty()
                .anyMatch(r -> r.contains("overbought"))
                .anyMatch(r -> r.contains("SMA 20 is above SMA 50"));
    }

    @Test
    void oversoldDowntrendMentionsRebound() {
        when(cache.getHistory("TCS", 300)).thenReturn(TestData.series(60, 200, -1));

        AnalysisResponse a = service.analyse("TCS", true);

        assertThat(a.trend()).isEqualTo("DOWN");
        assertThat(a.rsi14()).isEqualTo(0.0);
        assertThat(a.reasons()).anyMatch(r -> r.contains("oversold"));
    }

    @Test
    void resultIsCachedPerSymbolAndTier() {
        risingHistory();

        service.analyse("tcs", true);

        verify(cache).putCached(eq("stock:analysis:TCS:premium"), any(AnalysisResponse.class), eq(Duration.ofSeconds(2)));
    }

    @Test
    void cachedResultIsReturnedWithoutRecomputing() {
        AnalysisResponse cached = TestData.freeAnalysis("TCS");
        when(cache.getCached("stock:analysis:TCS:free", AnalysisResponse.class)).thenReturn(Optional.of(cached));

        assertThat(service.analyse("TCS", false)).isSameAs(cached);
        verify(cache, never()).getHistory(anyString(), anyInt());
        verifyNoInteractions(stocks);
    }

    @Test
    void unknownSymbolIs404() {
        when(stocks.findById("NOPE")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.analyse("nope", false))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("Unknown stock: NOPE")
                .extracting(e -> ((ApiException) e).getStatus()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void notEnoughDataIs503() {
        when(cache.getHistory("TCS", 300)).thenReturn(TestData.series(1, 100, 0));

        assertThatThrownBy(() -> service.analyse("TCS", false))
                .isInstanceOf(ApiException.class)
                .extracting(e -> ((ApiException) e).getStatus()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
    }

    @Test
    void shortHistoryReportsGatheringData() {
        when(cache.getHistory("TCS", 300)).thenReturn(TestData.series(10, 100, 1));

        AnalysisResponse a = service.analyse("TCS", true);

        assertThat(a.trend()).isEqualTo("INSUFFICIENT_DATA");
        assertThat(a.sma20()).isNull();
        assertThat(a.macd()).isNull();
        assertThat(a.signal()).isEqualTo("HOLD");
    }

    @Test
    void redisCacheOutageDoesNotBreakAnalysis() {
        risingHistory();
        when(cache.getCached(anyString(), eq(AnalysisResponse.class))).thenThrow(new RuntimeException("redis down"));
        doThrow(new RuntimeException("redis down")).when(cache).putCached(anyString(), any(), any());

        assertThat(service.analyse("TCS", true).currentPrice()).isEqualTo(160.0);
    }
}
