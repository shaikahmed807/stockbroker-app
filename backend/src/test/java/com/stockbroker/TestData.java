package com.stockbroker;

import com.stockbroker.dto.StockDtos.AnalysisResponse;
import com.stockbroker.dto.StockDtos.PricePoint;
import com.stockbroker.dto.StockDtos.StockQuote;
import com.stockbroker.model.PlanType;
import com.stockbroker.model.Stock;
import com.stockbroker.model.User;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/** Small factory methods shared by the unit tests. */
public final class TestData {

    public static final String SECRET =
            "c3RvY2ticm9rZXItZGVtby1zZWNyZXQta2V5LWNoYW5nZS1tZS1pbi1wcm9kdWN0aW9uLTEyMzQ1Ng==";

    private TestData() { }

    public static User freeUser(long id, String email) {
        User u = new User();
        u.setId(id);
        u.setFullName("Jane Trader");
        u.setEmail(email);
        u.setPassword("$2a$10$hash");
        return u;
    }

    public static User premiumUser(long id, String email, PlanType plan, Duration remaining) {
        User u = freeUser(id, email);
        u.setPlan(plan);
        u.setPremiumUntil(Instant.now().plus(remaining));
        return u;
    }

    public static Stock tcs() {
        return new Stock("TCS", "Tata Consultancy Services Ltd", "Information Technology", 4100.00);
    }

    public static Stock infy() {
        return new Stock("INFY", "Infosys Ltd", "Information Technology", 1850.00);
    }

    public static StockQuote quote(String symbol, double price, double previousClose) {
        double change = price - previousClose;
        return new StockQuote(symbol, symbol + " Ltd", "Information Technology", "NSE", price,
                change, change / previousClose * 100, previousClose, price, previousClose,
                previousClose, 12_000, 1_730_000_000_000L);
    }

    /** Linear series start, start+step, ... (n points), one second apart. */
    public static List<PricePoint> series(int n, double start, double step) {
        List<PricePoint> points = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            points.add(new PricePoint(1_730_000_000_000L + i * 1000L, start + i * step, 1000));
        }
        return points;
    }

    public static AnalysisResponse freeAnalysis(String symbol) {
        return new AnalysisResponse(symbol, symbol + " Ltd", 4120.0, 20.0, 0.49, 120, 1L,
                4110.0, 4100.0, 4111.0, "UP", 4130.0, 4090.0,
                true, null, null, null, null, null, null, null, null, null, null, null, null, null, null);
    }
}
