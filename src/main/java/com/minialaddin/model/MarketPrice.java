
package com.minialaddin.model;

import jakarta.persistence.*;

import java.math.BigDecimal;
import java.time.Instant;

@Entity
@Table(name = "market_prices")
public class MarketPrice {

    @Id
    @Column(length = 20, nullable = false)
    private String symbol;

    @Column(
            nullable = false,
            precision = 19,
            scale = 2
    )
    private BigDecimal price;

    @Column(
            name = "updated_at",
            nullable = false
    )
    private Instant updatedAt;

    protected MarketPrice() {
        // Required by JPA
    }

    public String getSymbol() {
        return symbol;
    }

    public BigDecimal getPrice() {
        return price;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
