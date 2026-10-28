
package com.minialaddin.service;

import com.minialaddin.exception.PortfolioNotFoundException;
import com.minialaddin.model.Portfolio;
import com.minialaddin.repository.PortfolioRepository;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class PortfolioServiceTest {

    private PortfolioRepository repository;
    private PortfolioService service;

    @BeforeEach
    void setUp() {
        repository = mock(PortfolioRepository.class);
        service = new PortfolioService(repository);
    }

    @Test
    void shouldCreatePortfolioSuccessfully() {
        when(repository.save(any(Portfolio.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        Portfolio portfolio = service.createPortfolio(
                "Growth Fund",
                new BigDecimal("100000.00")
        );

        assertEquals("Growth Fund", portfolio.getName());

        assertEquals(
                0,
                new BigDecimal("100000.00")
                        .compareTo(portfolio.getInitialCash())
        );

        verify(repository).save(any(Portfolio.class));
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

        verifyNoInteractions(repository);
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

        verifyNoInteractions(repository);
    }

    @Test
    void shouldThrowWhenPortfolioDoesNotExist() {
        when(repository.findById(999L))
                .thenReturn(Optional.empty());

        assertThrows(
                PortfolioNotFoundException.class,
                () -> service.getPortfolio(999L)
        );
    }
}
