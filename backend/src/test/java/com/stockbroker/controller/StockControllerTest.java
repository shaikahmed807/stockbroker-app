package com.stockbroker.controller;

import com.stockbroker.TestData;
import com.stockbroker.exception.ApiException;
import com.stockbroker.model.PriceTick;
import com.stockbroker.repository.PriceTickRepository;
import com.stockbroker.repository.StockRepository;
import com.stockbroker.service.PriceCacheService;
import com.stockbroker.service.StockAnalysisService;
import com.stockbroker.config.SecurityConfig;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.hamcrest.Matchers.hasSize;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(StockController.class)
@Import(SecurityConfig.class)
@DisplayName("StockController – /api/stocks endpoints")
class StockControllerTest extends WebLayerTestConfig {

    @Autowired MockMvc mvc;
    @MockBean StockRepository stockRepository;
    @MockBean PriceTickRepository priceTickRepository;
    @MockBean PriceCacheService priceCache;
    @MockBean StockAnalysisService analysisService;

    @Test
    void marketDataRequiresSignIn() throws Exception {
        mvc.perform(get("/api/stocks")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/stocks/TCS/analysis")).andExpect(status().isUnauthorized());
    }

    @Test
    @WithMockUser
    void listPrefersLiveRedisQuoteAndFallsBackToDatabase() throws Exception {
        when(stockRepository.findAll(any(Sort.class))).thenReturn(List.of(TestData.infy(), TestData.tcs()));
        when(priceCache.getQuotes(anyCollection())).thenReturn(List.of(TestData.quote("TCS", 4200, 4100)));

        mvc.perform(get("/api/stocks"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(2)))
                .andExpect(jsonPath("$[0].symbol").value("INFY"))
                .andExpect(jsonPath("$[0].price").value(1850.0))      // from MySQL
                .andExpect(jsonPath("$[1].symbol").value("TCS"))
                .andExpect(jsonPath("$[1].price").value(4200.0));     // from Redis
    }

    @Test
    @WithMockUser
    void listStillWorksWhenRedisIsDown() throws Exception {
        when(stockRepository.findAll(any(Sort.class))).thenReturn(List.of(TestData.tcs()));
        when(priceCache.getQuotes(anyCollection())).thenThrow(new RuntimeException("redis down"));

        mvc.perform(get("/api/stocks"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].price").value(4100.0));
    }

    @Test
    @WithMockUser
    void quoteForUnknownSymbolIs404() throws Exception {
        when(stockRepository.findById("XYZ")).thenReturn(Optional.empty());

        mvc.perform(get("/api/stocks/xyz"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("Unknown stock: XYZ"));
    }

    @Test
    @WithMockUser
    void quoteIsCaseInsensitive() throws Exception {
        when(stockRepository.findById("TCS")).thenReturn(Optional.of(TestData.tcs()));
        when(priceCache.getQuote("TCS")).thenReturn(Optional.of(TestData.quote("TCS", 4150, 4100)));

        mvc.perform(get("/api/stocks/tcs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.price").value(4150.0));
    }

    @Test
    @WithMockUser(roles = "USER")
    void freeUsersGetAtMost60HistoryPoints() throws Exception {
        when(stockRepository.existsById("TCS")).thenReturn(true);
        when(priceCache.getHistory("TCS", 60)).thenReturn(TestData.series(60, 100, 1));

        mvc.perform(get("/api/stocks/TCS/history").param("limit", "300"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(60)));
        verify(priceCache).getHistory("TCS", 60);
    }

    @Test
    @WithMockUser(roles = {"USER", "PREMIUM"})
    void premiumUsersGetUpTo300HistoryPoints() throws Exception {
        when(stockRepository.existsById("TCS")).thenReturn(true);
        when(priceCache.getHistory("TCS", 300)).thenReturn(TestData.series(300, 100, 1));

        mvc.perform(get("/api/stocks/TCS/history"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(300)));
    }

    @Test
    @WithMockUser
    void historyFallsBackToMysqlArchiveInChronologicalOrder() throws Exception {
        when(stockRepository.existsById("TCS")).thenReturn(true);
        when(priceCache.getHistory(anyString(), anyInt())).thenReturn(List.of());
        when(priceTickRepository.findBySymbolOrderByTickTimeDesc(eq("TCS"), any(Pageable.class))).thenReturn(List.of(
                new PriceTick("TCS", 102, 10, Instant.ofEpochMilli(3000)),
                new PriceTick("TCS", 101, 10, Instant.ofEpochMilli(2000)),
                new PriceTick("TCS", 100, 10, Instant.ofEpochMilli(1000))));

        mvc.perform(get("/api/stocks/TCS/history").param("limit", "3"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].price").value(100.0))
                .andExpect(jsonPath("$[2].price").value(102.0));
    }

    @Test
    @WithMockUser
    void historyOfUnknownSymbolIs404() throws Exception {
        when(stockRepository.existsById("NOPE")).thenReturn(false);
        mvc.perform(get("/api/stocks/NOPE/history")).andExpect(status().isNotFound());
    }

    @Test
    @WithMockUser
    void analysisForFreeUserRequestsBasicTier() throws Exception {
        when(analysisService.analyse("TCS", false)).thenReturn(TestData.freeAnalysis("TCS"));

        mvc.perform(get("/api/stocks/TCS/analysis"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.premiumLocked").value(true))
                .andExpect(jsonPath("$.trend").value("UP"));
        verify(analysisService).analyse("TCS", false);
    }

    @Test
    @WithMockUser(roles = {"USER", "PREMIUM"})
    void analysisForPremiumUserRequestsPremiumTier() throws Exception {
        when(analysisService.analyse("TCS", true)).thenReturn(TestData.freeAnalysis("TCS"));

        mvc.perform(get("/api/stocks/TCS/analysis")).andExpect(status().isOk());
        verify(analysisService).analyse("TCS", true);
    }

    @Test
    @WithMockUser
    void analysisWarmingUpIs503() throws Exception {
        when(analysisService.analyse(anyString(), anyBoolean()))
                .thenThrow(new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "Not enough market data yet"));

        mvc.perform(get("/api/stocks/TCS/analysis")).andExpect(status().isServiceUnavailable());
    }
}
