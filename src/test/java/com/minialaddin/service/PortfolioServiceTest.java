
package com.minialaddin.service;

import com.minialaddin.model.Portfolio;
import com.minialaddin.repository.InMemoryPortfolioRepository;
import com.minialaddin.exception.PortfolioNotFoundException;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.*;

class PortfolioServiceTest {

    private PortfolioService service;

    @BeforeEach
    void setUp() {
        service = new PortfolioService(
                new InMemoryPortfolioRepository()
        );
    }

    @Test
    void shouldCreatePortfolioSuccessfully() {
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
        assertThrows(
                IllegalArgumentException.class,
                () -> service.createPortfolio(
                        "Invalid Fund",
                        new BigDecimal("-100.00")
                )
        );
    }

    @Test
    void shouldRejectBlankName() {
        assertThrows(
                IllegalArgumentException.class,
                () -> service.createPortfolio(
                        " ",
                        BigDecimal.ZERO
                )
        );
    }

    @Test
    void shouldThrowWhenPortfolioDoesNotExist() {
        assertThrows(
                PortfolioNotFoundException.class,
                () -> service.getPortfolio(999L)
        );
    }
}
