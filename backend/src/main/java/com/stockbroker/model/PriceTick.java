package com.stockbroker.model;

import jakarta.persistence.*;

import java.time.Instant;

/**
 * Every market tick is archived to MySQL. Redis holds the hot, recent window;
 * this table is the durable history (and the fallback when Redis is empty).
 */
@Entity
@Table(name = "price_ticks", indexes = @Index(name = "idx_tick_symbol_time", columnList = "symbol,tick_time"))
public class PriceTick {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 20)
    private String symbol;

    @Column(nullable = false)
    private double price;

    private long volume;

    @Column(nullable = false)
    private Instant tickTime;

    public PriceTick() { }

    public PriceTick(String symbol, double price, long volume, Instant tickTime) {
        this.symbol = symbol;
        this.price = price;
        this.volume = volume;
        this.tickTime = tickTime;
    }

    public Long getId() { return id; }
    public String getSymbol() { return symbol; }
    public double getPrice() { return price; }
    public long getVolume() { return volume; }
    public Instant getTickTime() { return tickTime; }
}
