package com.minialaddin.service;

import com.minialaddin.model.Portfolio;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.*;

class PortfolioServiceTest {

    @Test
    void shouldCreatePortfolioSuccessfully() {
        PortfolioService service = new PortfolioService();

        Portfolio portfolio = service.createPortfolio(
                "Growth Fund",
                new BigDecimal("100000.00")
        );

        assertEquals(1L, portfolio.id());
        assertEquals("Growth Fund", portfolio.name());
        assertEquals(
                0,
                new BigDecimal("100000.00")
                        .compareTo(portfolio.initialCash())
        );

        assertEquals(
                portfolio,
                service.getPortfolio(1L)
        );
    }

    @Test
    void shouldRejectNegativeInitialCash() {
        PortfolioService service = new PortfolioService();

        assertThrows(
                IllegalArgumentException.class,
                () -> service.createPortfolio(
                        "Invalid Fund",
                        new BigDecimal("-100.00")
                )
        );
    }

    @Test
    void shouldReturnNullForUnknownPortfolio() {
        PortfolioService service = new PortfolioService();

        assertNull(service.getPortfolio(999L));
    }
}
