
package com.minialaddin.service;

import com.minialaddin.dto.PortfolioSnapshot;
import com.minialaddin.dto.PortfolioValuationResponse;
import com.minialaddin.model.Portfolio;
import com.minialaddin.model.Trade;
import com.minialaddin.model.TradeSide;
import com.minialaddin.model.MarketPrice;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class PortfolioValuationRegressionTest {

    private final PortfolioCalculationService positionCalculator =
            new PortfolioCalculationService();

    private final PortfolioValuationCalculator valuationCalculator =
            new PortfolioValuationCalculator();

    private Trade trade(
            Portfolio portfolio,
            TradeSide side,
            String quantity,
            String price
    ) {
        return new Trade(
                portfolio,
                "AAPL",
                side,
                new BigDecimal(quantity),
                new BigDecimal(price)
        );
    }

    private MarketPrice quote(String price) {
        MarketPrice result = mock(MarketPrice.class);

        when(result.getPrice())
                .thenReturn(new BigDecimal(price));

        when(result.getUpdatedAt())
                .thenReturn(
                        Instant.parse("2026-10-29T12:00:00Z")
                );

        return result;
    }

    private void assertAmount(
            String expected,
            BigDecimal actual
    ) {
        assertEquals(
                0,
                new BigDecimal(expected).compareTo(actual)
        );
    }

    @Test
    void shouldReconcileCashPositionsAndPnl() {

        Portfolio portfolio = new Portfolio(
                "Regression Fund",
                new BigDecimal("10000.00")
        );

        List<Trade> trades = List.of(
                trade(portfolio, TradeSide.BUY, "10", "200.00"),
                trade(portfolio, TradeSide.BUY, "10", "240.00"),
                trade(portfolio, TradeSide.SELL, "5", "260.00")
        );

        PortfolioSnapshot snapshot = positionCalculator.calculate(
                portfolio.getInitialCash(),
                trades
        );

        PortfolioValuationResponse valuation =
                valuationCalculator.calculate(
                        1L,
                        "Regression Fund",
                        portfolio.getInitialCash(),
                        trades,
                        Map.of("AAPL", quote("250.00"))
                );

        // Both calculators must agree on available cash.
        assertEquals(
                0,
                snapshot.availableCash()
                        .compareTo(valuation.availableCash())
        );

        // Both calculators must agree on current quantity.
        assertEquals(
                0,
                snapshot.positions().get(0).quantity()
                        .compareTo(valuation.positions().get(0).quantity())
        );

        assertAmount("6900.00", valuation.availableCash());
        assertAmount("3750.00", valuation.marketValue());
        assertAmount("10650.00", valuation.totalValue());

        assertAmount("200.00", valuation.realisedPnl());
        assertAmount("450.00", valuation.unrealisedPnl());
        assertAmount("650.00", valuation.totalPnl());

        // Accounting invariant
        assertEquals(
                0,
                valuation.totalValue()
                        .subtract(valuation.initialCash())
                        .compareTo(valuation.totalPnl())
        );
    }
}
