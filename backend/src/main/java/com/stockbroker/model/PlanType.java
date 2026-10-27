package com.stockbroker.model;

import java.math.BigDecimal;
import java.util.List;

/**
 * Subscription plans offered by the platform. Prices are in INR.
 */
public enum PlanType {

    FREE("Free", BigDecimal.ZERO, 0, List.of(
            "Live prices for all listed stocks",
            "Basic analysis: SMA 20/50, EMA 20, trend",
            "Last 60 data points on charts")),

    PREMIUM_MONTHLY("Premium Monthly", new BigDecimal("499"), 30, List.of(
            "Everything in Free",
            "RSI, MACD, Bollinger Bands, volatility",
            "Support & resistance levels",
            "Buy / Sell signal with reasoning",
            "Full intraday history on charts")),

    PREMIUM_YEARLY("Premium Yearly", new BigDecimal("4999"), 365, List.of(
            "Everything in Premium Monthly",
            "2 months free compared to monthly",
            "Priority support"));

    private final String displayName;
    private final BigDecimal price;
    private final int durationDays;
    private final List<String> features;

    PlanType(String displayName, BigDecimal price, int durationDays, List<String> features) {
        this.displayName = displayName;
        this.price = price;
        this.durationDays = durationDays;
        this.features = features;
    }

    public String getDisplayName() { return displayName; }
    public BigDecimal getPrice() { return price; }
    public int getDurationDays() { return durationDays; }
    public List<String> getFeatures() { return features; }

    public boolean isPremium() {
        return this != FREE;
    }
}
