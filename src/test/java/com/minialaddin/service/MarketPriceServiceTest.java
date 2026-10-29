
package com.minialaddin.service;

import com.minialaddin.dto.MarketPriceResponse;
import com.minialaddin.exception.MarketPriceNotFoundException;
import com.minialaddin.model.MarketPrice;
import com.minialaddin.repository.MarketPriceRepository;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class MarketPriceServiceTest {

    private MarketPriceRepository repository;
    private MarketPriceService service;

    @BeforeEach
    void setUp() {
        repository = mock(MarketPriceRepository.class);
        service = new MarketPriceService(repository);
    }

    @Test
    void shouldRejectNegativePrice() {

        assertThrows(
                IllegalArgumentException.class,
                () -> service.updatePrice(
                        "AAPL",
                        new BigDecimal("-10.00")
                )
        );

        verifyNoInteractions(repository);
    }

    @Test
    void shouldRejectInvalidSymbol() {

        assertThrows(
                IllegalArgumentException.class,
                () -> service.updatePrice(
                        "INVALID SYMBOL!",
                        new BigDecimal("200.00")
                )
        );

        verifyNoInteractions(repository);
    }

    @Test
    void shouldRejectExcessivePrecision() {

        assertThrows(
                IllegalArgumentException.class,
                () -> service.updatePrice(
                        "AAPL",
                        new BigDecimal("200.123")
                )
        );

        verifyNoInteractions(repository);
    }

    @Test
    void shouldReturnNotFoundForUnknownSymbol() {

        when(repository.findById("UNKNOWN"))
                .thenReturn(Optional.empty());

        assertThrows(
                MarketPriceNotFoundException.class,
                () -> service.getPrice("UNKNOWN")
        );
    }
}
