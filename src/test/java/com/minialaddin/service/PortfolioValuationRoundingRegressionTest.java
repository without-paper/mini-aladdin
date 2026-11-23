
package com.minialaddin.service;

import com.minialaddin.dto.PortfolioValuationResponse;
import com.minialaddin.model.*;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@Tag("R3")
class PortfolioValuationRoundingRegressionTest {

    @Test
    void shouldReconcilePnlAfterPartialSaleWithFractionalCost() {

        // Arrange: initial cash = $100.00
        Portfolio portfolio = new Portfolio(
                "Rounding Regression Fund",
                new BigDecimal("100.00")
        );

        // Total purchase cost = $0.03 for 2 shares.
        // Weighted average cost = $0.015 per share.
        List<Trade> trades = List.of(
                new Trade(
                        portfolio, "AAPL", TradeSide.BUY,
                        new BigDecimal("1"),
                        new BigDecimal("0.01")
                ),
                new Trade(
                        portfolio, "AAPL", TradeSide.BUY,
                        new BigDecimal("1"),
                        new BigDecimal("0.02")
                ),
                new Trade(
                        portfolio, "AAPL", TradeSide.SELL,
                        new BigDecimal("1"),
                        new BigDecimal("0.02")
                )
        );

        MarketPrice quote = mock(MarketPrice.class);
        when(quote.getPrice())
                .thenReturn(new BigDecimal("0.02"));
        when(quote.getUpdatedAt())
                .thenReturn(Instant.now());

        // Act
        PortfolioValuationResponse result =
                new PortfolioValuationCalculator().calculate(
                        1L,
                        "Rounding Regression Fund",
                        new BigDecimal("100.00"),
                        trades,
                        Map.of("AAPL", quote)
                );

        // Assert: independent cash-flow oracle
        assertEquals(
                new BigDecimal("99.99"),
                result.availableCash()
        );

        assertEquals(
                new BigDecimal("0.02"),
                result.marketValue()
        );

        assertEquals(
                new BigDecimal("100.01"),
                result.totalValue()
        );

        assertEquals(
                new BigDecimal("0.01"),
                result.totalPnl()
        );

        // R3: the displayed P&L components must reconcile.
        assertEquals(
                0,
                result.totalPnl()
                        .compareTo(
                                result.realisedPnl()
                                        .add(result.unrealisedPnl())
                        ),
                "Realised and unrealised P&L must "
                        + "sum to the reported total P&L"
        );

        // The cost of the sold share is rounded to $0.02.
        // The remaining share retains $0.01 cost basis.
        assertEquals(
                new BigDecimal("0.00"),
                result.realisedPnl()
        );

        assertEquals(
                new BigDecimal("0.01"),
                result.unrealisedPnl()
        );

        assertEquals(1, result.positions().size());

        assertEquals(
                new BigDecimal("0.01"),
                result.positions().get(0).costBasis()
        );

        assertEquals(
                result.unrealisedPnl(),
                result.positions().get(0).unrealisedPnl()
        );

    }
}
