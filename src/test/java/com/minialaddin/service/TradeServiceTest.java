
package com.minialaddin.service;

import com.minialaddin.dto.CreateTradeRequest;
import com.minialaddin.dto.TradeExecutionResult;
import com.minialaddin.dto.TradeResponse;
import com.minialaddin.exception.PortfolioNotFoundException;
import com.minialaddin.exception.TradeRejectedException;
import com.minialaddin.metrics.TradeMetrics;
import com.minialaddin.metrics.TransactionalTradeMetrics;
import com.minialaddin.model.Portfolio;
import com.minialaddin.model.Trade;
import com.minialaddin.model.TradeSide;
import com.minialaddin.repository.PortfolioRepository;
import com.minialaddin.repository.TradeRepository;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@Tag("R1")
class TradeServiceTest {

    private PortfolioRepository portfolioRepository;
    private TradeRepository tradeRepository;

    private TradeMetrics tradeMetrics;
    private TransactionalTradeMetrics transactionalTradeMetrics;

    private TradeService service;


    @BeforeEach
    void setUp() {

        // 1. Initialise repository mocks
        portfolioRepository = mock(PortfolioRepository.class);
        tradeRepository = mock(TradeRepository.class);

        // 2. Initialise metrics mocks
        tradeMetrics = mock(TradeMetrics.class);
        transactionalTradeMetrics =
                mock(TransactionalTradeMetrics.class);

        // 3. Construct the service with all dependencies
        service = new TradeService(
                portfolioRepository,
                tradeRepository,
                tradeMetrics,
                transactionalTradeMetrics
        );

        // 4. Default idempotency lookup: no existing trade
        when(tradeRepository.findByPortfolioIdAndRequestKey(
                anyLong(),
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

        verify(tradeRepository, never())
                .saveAndFlush(any(Trade.class));
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

        verify(tradeRepository, never())
                .saveAndFlush(any(Trade.class));
    }



    @ParameterizedTest(name = "{0}: BUY 10 @ 100.00 with cash={1}, accepted={2}")
    @CsvSource({
            "FT-01, 999.99, false",
            "FT-02, 1000.00, true",
            "FT-03, 1000.01, true"
    })
    void shouldRespectBuyCashBoundary(
            String caseId,
            String initialCash,
            boolean accepted
    ) {
        // Arrange
        Portfolio portfolio = new Portfolio(
                "BUY Boundary Fund",
                new BigDecimal(initialCash)
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
                new BigDecimal("100.00")
        );

        // Act & Assert
        if (accepted) {
            TradeExecutionResult result = service.executeTrade(
                    1L,
                    request,
                    "buy-boundary-0001"
            );

            assertFalse(result.replayed());
            assertEquals("AAPL", result.trade().symbol());
            assertEquals(TradeSide.BUY, result.trade().side());

            verify(tradeRepository, times(1))
                    .saveAndFlush(any(Trade.class));

        } else {
            assertThrows(
                    TradeRejectedException.class,
                    () -> service.executeTrade(
                            1L,
                            request,
                            "buy-boundary-0001"
                    )
            );

            verify(tradeRepository, never())
                    .saveAndFlush(any(Trade.class));
        }
    }


    @ParameterizedTest(name = "{0}: hold 10, SELL {1}, accepted={2}")
    @CsvSource({
            "FT-04, 9, true",
            "FT-05, 10, true",
            "FT-06, 11, false"
    })
    void shouldRespectSellHoldingBoundary(
            String caseId,
            String sellQuantity,
            boolean accepted
    ) {
        // Arrange: portfolio already holds 10 AAPL shares
        Portfolio portfolio = new Portfolio(
                "SELL Boundary Fund",
                new BigDecimal("10000.00")
        );

        Trade previousBuy = new Trade(
                portfolio,
                "AAPL",
                TradeSide.BUY,
                new BigDecimal("10"),
                new BigDecimal("100.00")
        );

        when(portfolioRepository.findByIdForUpdate(1L))
                .thenReturn(Optional.of(portfolio));

        when(tradeRepository
                .findByPortfolioIdOrderByExecutedAtAscIdAsc(1L))
                .thenReturn(List.of(previousBuy));

        when(tradeRepository.saveAndFlush(any(Trade.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        CreateTradeRequest request = new CreateTradeRequest(
                "AAPL",
                TradeSide.SELL,
                new BigDecimal(sellQuantity),
                new BigDecimal("120.00")
        );

        // Act & Assert
        if (accepted) {
            TradeExecutionResult result = service.executeTrade(
                    1L,
                    request,
                    "sell-boundary-0001"
            );

            assertFalse(result.replayed());
            assertEquals(TradeSide.SELL, result.trade().side());

            assertEquals(
                    0,
                    new BigDecimal(sellQuantity)
                            .compareTo(result.trade().quantity())
            );

            verify(tradeRepository, times(1))
                    .saveAndFlush(any(Trade.class));

        } else {
            assertThrows(
                    TradeRejectedException.class,
                    () -> service.executeTrade(
                            1L,
                            request,
                            "sell-boundary-0001"
                    )
            );

            verify(tradeRepository, never())
                    .saveAndFlush(any(Trade.class));
        }
    }

}
