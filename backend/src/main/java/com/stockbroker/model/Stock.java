package com.stockbroker.model;

import jakarta.persistence.*;

import java.time.Instant;

@Entity
@Table(name = "stocks")
public class Stock {

    @Id
    @Column(length = 20)
    private String symbol;

    @Column(nullable = false, length = 120)
    private String companyName;

    @Column(length = 60)
    private String sector;

    @Column(length = 10)
    private String exchange = "NSE";

    /** Reference price the market simulator starts from. */
    @Column(nullable = false)
    private double basePrice;

    private double lastPrice;
    private double dayOpen;
    private double dayHigh;
    private double dayLow;
    private double previousClose;
    private Instant updatedAt;

    public Stock() { }

    public Stock(String symbol, String companyName, String sector, double basePrice) {
        this.symbol = symbol;
        this.companyName = companyName;
        this.sector = sector;
        this.basePrice = basePrice;
        this.lastPrice = basePrice;
    }

    public String getSymbol() { return symbol; }
    public void setSymbol(String symbol) { this.symbol = symbol; }
    public String getCompanyName() { return companyName; }
    public void setCompanyName(String companyName) { this.companyName = companyName; }
    public String getSector() { return sector; }
    public void setSector(String sector) { this.sector = sector; }
    public String getExchange() { return exchange; }
    public void setExchange(String exchange) { this.exchange = exchange; }
    public double getBasePrice() { return basePrice; }
    public void setBasePrice(double basePrice) { this.basePrice = basePrice; }
    public double getLastPrice() { return lastPrice; }
    public void setLastPrice(double lastPrice) { this.lastPrice = lastPrice; }
    public double getDayOpen() { return dayOpen; }
    public void setDayOpen(double dayOpen) { this.dayOpen = dayOpen; }
    public double getDayHigh() { return dayHigh; }
    public void setDayHigh(double dayHigh) { this.dayHigh = dayHigh; }
    public double getDayLow() { return dayLow; }
    public void setDayLow(double dayLow) { this.dayLow = dayLow; }
    public double getPreviousClose() { return previousClose; }
    public void setPreviousClose(double previousClose) { this.previousClose = previousClose; }
    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }
}
