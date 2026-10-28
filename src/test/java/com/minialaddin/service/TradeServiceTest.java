
package com.minialaddin.service;

import com.minialaddin.dto.TradeResponse;
import com.minialaddin.exception.PortfolioNotFoundException;
import com.minialaddin.repository.PortfolioRepository;
import com.minialaddin.repository.TradeRepository;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class TradeServiceTest {

    private TradeRepository tradeRepository;
    private PortfolioRepository portfolioRepository;
    private TradeService service;

    @BeforeEach
    void setUp() {
        tradeRepository = mock(TradeRepository.class);
        portfolioRepository = mock(PortfolioRepository.class);

        service = new TradeService(
                tradeRepository,
                portfolioRepository
        );
    }

    @Test
    void shouldReturnEmptyTradeList() {
        when(portfolioRepository.existsById(1L))
                .thenReturn(true);

        when(tradeRepository
                .findByPortfolioIdOrderByExecutedAtAscIdAsc(1L))
                .thenReturn(List.of());

        List<TradeResponse> result = service.getTrades(1L);

        assertTrue(result.isEmpty());

        verify(tradeRepository)
                .findByPortfolioIdOrderByExecutedAtAscIdAsc(1L);
    }

    @Test
    void shouldRejectUnknownPortfolio() {
        when(portfolioRepository.existsById(999L))
                .thenReturn(false);

        assertThrows(
                PortfolioNotFoundException.class,
                () -> service.getTrades(999L)
        );

        verifyNoInteractions(tradeRepository);
    }
}
