
package com.minialaddin.service;

import com.minialaddin.dto.PortfolioValuationResponse;
import com.minialaddin.dto.PositionValuationResponse;
import com.minialaddin.exception.StaleMarketPriceException;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class MarketPriceFreshnessPolicyTest {

    private MarketPriceFreshnessPolicy policy;

    private static final Instant NOW =
            Instant.parse("2026-10-29T12:00:00Z");

    @BeforeEach
    void setUp() {
        policy = new MarketPriceFreshnessPolicy();
    }

    private PortfolioValuationResponse reportWithQuote(
            Instant updatedAt
    ) {
        PositionValuationResponse position =
                new PositionValuationResponse(
                        "AAPL",
                        new BigDecimal("10"),
                        new BigDecimal("200.00"),
                        new BigDecimal("2000.00"),
                        new BigDecimal("230.00"),
                        updatedAt,
                        new BigDecimal("2300.00"),
                        new BigDecimal("300.00")
                );

        return new PortfolioValuationResponse(
                1L,
                "Test Fund",
                "USD",
                new BigDecimal("10000.00"),
                new BigDecimal("8000.00"),
                new BigDecimal("2300.00"),
                new BigDecimal("10300.00"),
                BigDecimal.ZERO,
                new BigDecimal("300.00"),
                new BigDecimal("300.00"),
                List.of(position)
        );
    }

    @Test
    void shouldAcceptFreshQuote() {

        var report = reportWithQuote(
                NOW.minusSeconds(60)
        );

        assertDoesNotThrow(
                () -> policy.validateAt(report, NOW)
        );
    }

    @Test
    void shouldRejectQuoteOlderThan24Hours() {

        var report = reportWithQuote(
                NOW.minusSeconds(25 * 3600)
        );

        assertThrows(
                StaleMarketPriceException.class,
                () -> policy.validateAt(report, NOW)
        );
    }

    @Test
    void shouldAcceptQuoteAt24HourBoundary() {

        var report = reportWithQuote(
                NOW.minusSeconds(24 * 3600)
        );

        assertDoesNotThrow(
                () -> policy.validateAt(report, NOW)
        );
    }

    @Test
    void shouldRejectFarFutureTimestamp() {

        var report = reportWithQuote(
                NOW.plusSeconds(600)
        );

        assertThrows(
                StaleMarketPriceException.class,
                () -> policy.validateAt(report, NOW)
        );
    }

    @Test
    void shouldRejectMissingTimestamp() {

        var report = reportWithQuote(null);

        assertThrows(
                StaleMarketPriceException.class,
                () -> policy.validateAt(report, NOW)
        );
    }

    @Test
    void shouldAcceptEmptyPortfolio() {

        var report = new PortfolioValuationResponse(
                1L,
                "Empty Fund",
                "USD",
                new BigDecimal("10000.00"),
                new BigDecimal("10000.00"),
                BigDecimal.ZERO,
                new BigDecimal("10000.00"),
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                List.of()
        );

        assertDoesNotThrow(
                () -> policy.validateAt(report, NOW)
        );
    }
}
