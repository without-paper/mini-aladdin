
package com.minialaddin.service;

import com.minialaddin.dto.CreateTradeRequest;
import com.minialaddin.dto.TradeExecutionResult;
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
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * LO4 follow-up for R1 (trade correctness).
 *
 * JaCoCo showed that no test executed a trade whose history contained
 * a SELL: TradeService lines 238-240 (cash credited by a sale) and 258
 * (holding reduced by a sale) never ran, so replacing add() with
 * subtract() on line 240 would have passed the whole suite. The
 * original boundary tests (FT-01 to FT-06) partitioned the current
 * cash and holding, but not the composition of the trade history.
 *
 * These tests add "history contains SELL" as a category, apply the
 * same one-cent / one-share boundary analysis as FT-01 to FT-06, and
 * also cover execution against a missing portfolio (line 165).
 */
class TradeServiceHistoryTest {

    private static final Long PORTFOLIO_ID = 1L;

    private PortfolioRepository portfolioRepository;
    private TradeRepository tradeRepository;
    private TradeMetrics tradeMetrics;
    private TransactionalTradeMetrics transactionalTradeMetrics;

    private TradeService service;
    private Portfolio portfolio;

    @BeforeEach
    void setUp() {
        portfolioRepository = mock(PortfolioRepository.class);
        tradeRepository = mock(TradeRepository.class);
        tradeMetrics = mock(TradeMetrics.class);
        transactionalTradeMetrics = mock(TransactionalTradeMetrics.class);

        service = new TradeService(
                portfolioRepository,
                tradeRepository,
                tradeMetrics,
                transactionalTradeMetrics
        );

        portfolio = new Portfolio(
                "History Fund",
                new BigDecimal("1000.00")
        );

        when(portfolioRepository.findByIdForUpdate(PORTFOLIO_ID))
                .thenReturn(Optional.of(portfolio));

        when(tradeRepository.findByPortfolioIdAndRequestKey(
                anyLong(),
                anyString()
        )).thenReturn(Optional.empty());

        when(tradeRepository.saveAndFlush(any(Trade.class)))
                .thenAnswer(inv -> inv.getArgument(0));
    }

    // ---------------------------------------------------------
    // Helpers
    // ---------------------------------------------------------

    private Trade historical(
            String symbol,
            TradeSide side,
            String quantity,
            String price
    ) {
        return new Trade(
                portfolio,
                symbol,
                side,
                new BigDecimal(quantity),
                new BigDecimal(price)
        );
    }

    private void givenHistory(Trade... trades) {
        when(tradeRepository
                .findByPortfolioIdOrderByExecutedAtAscIdAsc(PORTFOLIO_ID))
                .thenReturn(List.of(trades));
    }

    private CreateTradeRequest request(
            String symbol,
            TradeSide side,
            String quantity,
            String price
    ) {
        return new CreateTradeRequest(
                symbol,
                side,
                new BigDecimal(quantity),
                new BigDecimal(price)
        );
    }

    /**
     * Oracle shared by all cases: an accepted trade is persisted once
     * and its execution metric is registered for after-commit; a
     * rejected trade is not persisted and is counted as rejected.
     */
    private void assertOutcome(
            boolean accepted,
            CreateTradeRequest request,
            String requestKey
    ) {
        if (accepted) {
            TradeExecutionResult result = service.executeTrade(
                    PORTFOLIO_ID,
                    request,
                    requestKey
            );

            assertFalse(result.replayed());
            assertEquals(request.side(), result.trade().side());
            assertEquals(
                    0,
                    request.quantity().compareTo(result.trade().quantity())
            );

            verify(tradeRepository, times(1))
                    .saveAndFlush(any(Trade.class));
            verify(transactionalTradeMetrics, times(1))
                    .recordExecutionAfterCommit();
            verify(tradeMetrics, never()).recordRejected();

        } else {
            assertThrows(
                    TradeRejectedException.class,
                    () -> service.executeTrade(
                            PORTFOLIO_ID,
                            request,
                            requestKey
                    )
            );

            verify(tradeRepository, never())
                    .saveAndFlush(any(Trade.class));
            verify(transactionalTradeMetrics, never())
                    .recordExecutionAfterCommit();
            verify(tradeMetrics, times(1)).recordRejected();
        }
    }

    // ---------------------------------------------------------
    // FT-13 to FT-15: sale proceeds are credited at the sale price
    //
    // Cash = 1000.00 - 10 x 100.00 + 10 x 120.00 = 1200.00.
    // The SELL price differs from the BUY price, so crediting the
    // cost basis instead of the proceeds would also be detected.
    // ---------------------------------------------------------

    @ParameterizedTest(name = "FT-13..15 BUY after SELL: price={0}, accepted={1}")
    @CsvSource({
            "1199.99, true",
            "1200.00, true",
            "1200.01, false"
    })
    void shouldCreditSaleProceedsBeforeCheckingCash(
            String buyPrice,
            boolean accepted
    ) {
        givenHistory(
                historical("AAPL", TradeSide.BUY, "10", "100.00"),
                historical("AAPL", TradeSide.SELL, "10", "120.00")
        );

        assertOutcome(
                accepted,
                request("MSFT", TradeSide.BUY, "1", buyPrice),
                "history-buy-0001"
        );
    }

    // ---------------------------------------------------------
    // FT-16 to FT-18: earlier SELLs reduce the holding
    //
    // Holding = 10 - 4 = 6 AAPL shares.
    // ---------------------------------------------------------

    @ParameterizedTest(name = "FT-16..18 SELL after partial SELL: quantity={0}, accepted={1}")
    @CsvSource({
            "5, true",
            "6, true",
            "7, false"
    })
    void shouldReduceHoldingByEarlierSells(
            String sellQuantity,
            boolean accepted
    ) {
        givenHistory(
                historical("AAPL", TradeSide.BUY, "10", "100.00"),
                historical("AAPL", TradeSide.SELL, "4", "110.00")
        );

        assertOutcome(
                accepted,
                request("AAPL", TradeSide.SELL, sellQuantity, "110.00"),
                "history-sell-0001"
        );
    }

    // ---------------------------------------------------------
    // FT-19 to FT-20: trades in another symbol do not change the
    // holding being sold
    //
    // AAPL holding = 5. The MSFT history (BUY 3, SELL 1) would give
    // 7 if the symbol filter were ignored, so 6 must be rejected.
    // ---------------------------------------------------------

    @ParameterizedTest(name = "FT-19..20 SELL with other-symbol history: quantity={0}, accepted={1}")
    @CsvSource({
            "5, true",
            "6, false"
    })
    void shouldIgnoreOtherSymbolsWhenCheckingHolding(
            String sellQuantity,
            boolean accepted
    ) {
        givenHistory(
                historical("AAPL", TradeSide.BUY, "5", "100.00"),
                historical("MSFT", TradeSide.BUY, "3", "100.00"),
                historical("MSFT", TradeSide.SELL, "1", "100.00")
        );

        assertOutcome(
                accepted,
                request("AAPL", TradeSide.SELL, sellQuantity, "100.00"),
                "history-cross-0001"
        );
    }

    // ---------------------------------------------------------
    // Execution against a missing portfolio: 404 path, nothing
    // read or persisted.
    // ---------------------------------------------------------

    @Test
    void shouldRejectTradeForUnknownPortfolioWithoutPersisting() {
        when(portfolioRepository.findByIdForUpdate(99L))
                .thenReturn(Optional.empty());

        assertThrows(
                PortfolioNotFoundException.class,
                () -> service.executeTrade(
                        99L,
                        request("AAPL", TradeSide.BUY, "1", "100.00"),
                        "missing-portfolio-0001"
                )
        );

        verify(tradeRepository, never())
                .findByPortfolioIdOrderByExecutedAtAscIdAsc(anyLong());
        verify(tradeRepository, never())
                .saveAndFlush(any(Trade.class));
        verify(transactionalTradeMetrics, never())
                .recordExecutionAfterCommit();
    }
}
