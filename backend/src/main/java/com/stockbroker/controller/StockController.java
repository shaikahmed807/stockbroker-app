package com.stockbroker.controller;

import com.stockbroker.dto.StockDtos.AnalysisResponse;
import com.stockbroker.dto.StockDtos.PricePoint;
import com.stockbroker.dto.StockDtos.StockQuote;
import com.stockbroker.exception.ApiException;
import com.stockbroker.model.Stock;
import com.stockbroker.repository.PriceTickRepository;
import com.stockbroker.repository.StockRepository;
import com.stockbroker.service.PriceCacheService;
import com.stockbroker.service.StockAnalysisService;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/stocks")
public class StockController {

    private static final int FREE_HISTORY_LIMIT = 60;
    private static final int PREMIUM_HISTORY_LIMIT = 300;

    private final StockRepository stockRepository;
    private final PriceTickRepository priceTickRepository;
    private final PriceCacheService priceCache;
    private final StockAnalysisService analysisService;

    public StockController(StockRepository stockRepository, PriceTickRepository priceTickRepository,
                           PriceCacheService priceCache, StockAnalysisService analysisService) {
        this.stockRepository = stockRepository;
        this.priceTickRepository = priceTickRepository;
        this.priceCache = priceCache;
        this.analysisService = analysisService;
    }

    /** All stocks with their latest quote (Redis first, MySQL as fallback). */
    @GetMapping
    public List<StockQuote> list() {
        List<Stock> stocks = stockRepository.findAll(org.springframework.data.domain.Sort.by("symbol"));
        Map<String, StockQuote> cached;
        try {
            cached = priceCache.getQuotes(stocks.stream().map(Stock::getSymbol).toList())
                    .stream().collect(Collectors.toMap(StockQuote::symbol, Function.identity()));
        } catch (Exception e) {
            cached = Map.of();
        }
        List<StockQuote> result = new ArrayList<>();
        for (Stock s : stocks) {
            result.add(cached.getOrDefault(s.getSymbol(), fromEntity(s)));
        }
        return result;
    }

    @GetMapping("/{symbol}")
    public StockQuote quote(@PathVariable String symbol) {
        String sym = symbol.toUpperCase();
        Stock stock = stockRepository.findById(sym)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Unknown stock: " + sym));
        try {
            return priceCache.getQuote(sym).orElse(fromEntity(stock));
        } catch (Exception e) {
            return fromEntity(stock);
        }
    }

    /** Recent price history. Free users get the last 60 points, Premium users up to 300. */
    @GetMapping("/{symbol}/history")
    public List<PricePoint> history(@PathVariable String symbol,
                                    @RequestParam(defaultValue = "300") int limit,
                                    Authentication auth) {
        String sym = symbol.toUpperCase();
        if (!stockRepository.existsById(sym)) {
            throw new ApiException(HttpStatus.NOT_FOUND, "Unknown stock: " + sym);
        }
        int max = isPremium(auth) ? PREMIUM_HISTORY_LIMIT : FREE_HISTORY_LIMIT;
        int n = Math.max(1, Math.min(limit, max));

        List<PricePoint> points;
        try {
            points = priceCache.getHistory(sym, n);
        } catch (Exception e) {
            points = List.of();
        }
        if (points.isEmpty()) {
            // Redis empty (e.g. restarted): fall back to the MySQL archive.
            points = priceTickRepository.findBySymbolOrderByTickTimeDesc(sym, PageRequest.of(0, n)).stream()
                    .map(t -> new PricePoint(t.getTickTime().toEpochMilli(), t.getPrice(), t.getVolume()))
                    .collect(Collectors.toCollection(ArrayList::new));
            Collections.reverse(points);
        }
        return points;
    }

    /** Live technical analysis. Advanced indicators require Premium. */
    @GetMapping("/{symbol}/analysis")
    public AnalysisResponse analysis(@PathVariable String symbol, Authentication auth) {
        return analysisService.analyse(symbol, isPremium(auth));
    }

    private static boolean isPremium(Authentication auth) {
        return auth != null && auth.getAuthorities().stream()
                .anyMatch(a -> "ROLE_PREMIUM".equals(a.getAuthority()));
    }

    private static StockQuote fromEntity(Stock s) {
        double price = s.getLastPrice() > 0 ? s.getLastPrice() : s.getBasePrice();
        double prev = s.getPreviousClose() > 0 ? s.getPreviousClose() : price;
        double change = price - prev;
        long ts = s.getUpdatedAt() == null ? System.currentTimeMillis() : s.getUpdatedAt().toEpochMilli();
        return new StockQuote(s.getSymbol(), s.getCompanyName(), s.getSector(), s.getExchange(), price,
                Math.round(change * 100.0) / 100.0,
                prev == 0 ? 0 : Math.round(change / prev * 10000.0) / 100.0,
                s.getDayOpen() > 0 ? s.getDayOpen() : price,
                s.getDayHigh() > 0 ? s.getDayHigh() : price,
                s.getDayLow() > 0 ? s.getDayLow() : price,
                prev, 0, ts);
    }
}
