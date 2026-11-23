
package com.minialaddin.service;

import com.minialaddin.dto.MarketPriceResponse;
import com.minialaddin.exception.MarketPriceNotFoundException;
import com.minialaddin.model.MarketPrice;
import com.minialaddin.repository.MarketPriceRepository;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@Tag("R4")
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

    /**
     * Price boundary (R4 input domain): the smallest representable
     * positive price is 0.01; 0.00 and -0.01 lie just outside it.
     */
    @ParameterizedTest(name = "MP-B{index}: price={0} is rejected")
    @ValueSource(strings = {"-0.01", "0.00"})
    void shouldRejectPriceAtOrBelowZero(String price) {

        IllegalArgumentException ex = assertThrows(
                IllegalArgumentException.class,
                () -> service.updatePrice(
                        "AAPL",
                        new BigDecimal(price)
                )
        );

        assertEquals("Invalid market price", ex.getMessage());
        verifyNoInteractions(repository);
    }

    @Test
    @DisplayName("MP-B3: price=0.01 is accepted and the symbol is normalised")
    void shouldAcceptSmallestPositivePrice() {

        BigDecimal price = new BigDecimal("0.01");
        Instant updatedAt = Instant.parse("2026-11-23T12:00:00Z");

        MarketPrice stored = mock(MarketPrice.class);
        when(stored.getSymbol()).thenReturn("AAPL");
        when(stored.getPrice()).thenReturn(price);
        when(stored.getUpdatedAt()).thenReturn(updatedAt);

        when(repository.findById("AAPL"))
                .thenReturn(Optional.of(stored));

        MarketPriceResponse response =
                service.updatePrice(" aapl ", price);

        verify(repository).upsertPrice("AAPL", price);
        assertEquals("AAPL", response.symbol());
        assertEquals(0, price.compareTo(response.price()));
        assertEquals("USD", response.currency());
        assertEquals(updatedAt, response.updatedAt());
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
