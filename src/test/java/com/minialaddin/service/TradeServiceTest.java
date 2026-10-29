
package com.minialaddin.service;

import com.minialaddin.dto.TradeResponse;
import com.minialaddin.exception.PortfolioNotFoundException;
import com.minialaddin.repository.PortfolioRepository;
import com.minialaddin.repository.TradeRepository;
import com.minialaddin.dto.CreateTradeRequest;
import com.minialaddin.exception.TradeRejectedException;
import com.minialaddin.model.Portfolio;
import com.minialaddin.model.Trade;
import com.minialaddin.model.TradeSide;
import com.minialaddin.dto.TradeExecutionResult;
import com.minialaddin.config.TestDatabaseConfiguration;

import org.springframework.context.annotation.Import;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import java.math.BigDecimal;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.any;

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

        when(tradeRepository.findByPortfolioIdAndRequestKey(
                eq(1L),
                anyString()
        )).thenReturn(Optional.empty());
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


    @Test
    void shouldAllowBuyWithEnoughCash() {
        Portfolio portfolio = new Portfolio(
                "Test Fund",
                new BigDecimal("10000.00")
        );

        when(portfolioRepository.findByIdForUpdate(1L))
                .thenReturn(Optional.of(portfolio));

        when(tradeRepository
                .findByPortfolioIdOrderByExecutedAtAscIdAsc(1L))
                .thenReturn(List.of());

        when(tradeRepository.saveAndFlush(any(Trade.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        CreateTradeRequest request = new CreateTradeRequest(
                "AAPL",
                TradeSide.BUY,
                new BigDecimal("10"),
                new BigDecimal("200.00")
        );

        TradeExecutionResult result = service.executeTrade(
                1L,
                request,
                "test-trade-0001"
        );

        assertEquals("AAPL", result.trade().symbol());
        assertFalse(result.replayed());

        verify(tradeRepository).saveAndFlush(any(Trade.class));
    }

    @Test
    void shouldRejectBuyWhenCashInsufficient() {
        Portfolio portfolio = new Portfolio(
                "Test Fund",
                new BigDecimal("1000.00")
        );

        when(portfolioRepository.findByIdForUpdate(1L))
                .thenReturn(Optional.of(portfolio));

        when(tradeRepository
                .findByPortfolioIdOrderByExecutedAtAscIdAsc(1L))
                .thenReturn(List.of());

        CreateTradeRequest request = new CreateTradeRequest(
                "AAPL",
                TradeSide.BUY,
                new BigDecimal("10"),
                new BigDecimal("200.00")
        );

        assertThrows(
                TradeRejectedException.class,
                () -> service.executeTrade(1L, request, "test-trade-0001")
        );

        verify(tradeRepository, never()).save(any(Trade.class));
    }

    @Test
    void shouldRejectSellWhenPositionInsufficient() {
        Portfolio portfolio = new Portfolio(
                "Test Fund",
                new BigDecimal("10000.00")
        );

        Trade previousBuy = new Trade(
                portfolio,
                "AAPL",
                TradeSide.BUY,
                new BigDecimal("5"),
                new BigDecimal("200.00")
        );

        when(portfolioRepository.findByIdForUpdate(1L))
                .thenReturn(Optional.of(portfolio));

        when(tradeRepository
                .findByPortfolioIdOrderByExecutedAtAscIdAsc(1L))
                .thenReturn(List.of(previousBuy));

        CreateTradeRequest request = new CreateTradeRequest(
                "AAPL",
                TradeSide.SELL,
                new BigDecimal("10"),
                new BigDecimal("220.00")
        );

        assertThrows(
                TradeRejectedException.class,
                () -> service.executeTrade(1L, request, "test-trade-0001")
        );

        verify(tradeRepository, never()).save(any(Trade.class));
    }

}
