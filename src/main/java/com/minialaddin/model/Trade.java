
package com.minialaddin.model;

import jakarta.persistence.*;

import java.math.BigDecimal;
import java.time.Instant;

@Entity
@Table(name = "trades")
public class Trade {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "portfolio_id", nullable = false)
    private Portfolio portfolio;

    @Column(nullable = false, length = 20)
    private String symbol;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 4)
    private TradeSide side;

    @Column(nullable = false, precision = 19, scale = 0)
    private BigDecimal quantity;

    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal price;

    @Column(name = "executed_at", nullable = false)
    private Instant executedAt;

    @Column(name = "request_key", length = 128)
    private String requestKey;

    @Column(name = "request_fingerprint", length = 256)
    private String requestFingerprint;


    protected Trade() {
    }

    public Trade(
            Portfolio portfolio,
            String symbol,
            TradeSide side,
            BigDecimal quantity,
            BigDecimal price
    ) {
        this.portfolio = portfolio;
        this.symbol = symbol;
        this.side = side;
        this.quantity = quantity;
        this.price = price;
        this.executedAt = Instant.now();
    }

    public Trade(
            Portfolio portfolio,
            String symbol,
            TradeSide side,
            BigDecimal quantity,
            BigDecimal price,
            String requestKey,
            String requestFingerprint
    ) {
        this(portfolio, symbol, side, quantity, price);

        this.requestKey = requestKey;
        this.requestFingerprint = requestFingerprint;
    }


    public Long getId() {
        return id;
    }

    public Portfolio getPortfolio() {
        return portfolio;
    }

    public String getSymbol() {
        return symbol;
    }

    public TradeSide getSide() {
        return side;
    }

    public BigDecimal getQuantity() {
        return quantity;
    }

    public BigDecimal getPrice() {
        return price;
    }

    public Instant getExecutedAt() {
        return executedAt;
    }

    public String getRequestKey() {
        return requestKey;
    }

    public String getRequestFingerprint() {
        return requestFingerprint;
    }

}
