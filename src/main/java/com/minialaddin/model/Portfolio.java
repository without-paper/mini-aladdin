
package com.minialaddin.model;

import jakarta.persistence.*;

import java.math.BigDecimal;

@Entity
@Table(name = "portfolios")
public class Portfolio {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 255)
    private String name;

    @Column(
            name = "initial_cash",
            nullable = false,
            precision = 19,
            scale = 2
    )
    private BigDecimal initialCash;

    protected Portfolio() {
        // Required by JPA
    }

    public Portfolio(String name, BigDecimal initialCash) {
        this.name = name;
        this.initialCash = initialCash;
    }

    public Long getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public BigDecimal getInitialCash() {
        return initialCash;
    }
}
