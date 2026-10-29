
package com.minialaddin.service;

import com.minialaddin.dto.PortfolioSnapshot;
import com.minialaddin.model.Portfolio;
import com.minialaddin.model.Trade;
import com.minialaddin.model.TradeSide;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class PortfolioCalculationServiceTest {

    private PortfolioCalculationService calculator;
    private Portfolio portfolio;

    @BeforeEach
    void setUp() {
        calculator = new PortfolioCalculationService();

        portfolio = new Portfolio(
                "Test Fund",
                new BigDecimal("10000.00")
        );
    }

    private Trade trade(
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
                new BigDecimal(price),
                null,
                null
        );
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
    void shouldReturnInitialCashForEmptyPortfolio() {

        PortfolioSnapshot result = calculator.calculate(
                new BigDecimal("10000.00"),
                List.of()
        );

        assertAmount("10000.00", result.availableCash());
        assertTrue(result.positions().isEmpty());
    }

    @Test
    void shouldCalculateCashAfterBuyAndSell() {

        List<Trade> trades = List.of(
                trade("AAPL", TradeSide.BUY, "10", "200.00"),
                trade("MSFT", TradeSide.BUY, "5", "300.00"),
                trade("AAPL", TradeSide.SELL, "3", "220.00")
        );

        PortfolioSnapshot result = calculator.calculate(
                new BigDecimal("10000.00"),
                trades
        );

        assertAmount("7160.00", result.availableCash());
        assertEquals(2, result.positions().size());
    }

    @Test
    void shouldAggregatePositionsBySymbol() {

        List<Trade> trades = List.of(
                trade("AAPL", TradeSide.BUY, "10", "200.00"),
                trade("AAPL", TradeSide.BUY, "5", "210.00"),
                trade("AAPL", TradeSide.SELL, "3", "220.00")
        );

        PortfolioSnapshot result = calculator.calculate(
                new BigDecimal("10000.00"),
                trades
        );

        assertEquals(1, result.positions().size());

        assertEquals(
                "AAPL",
                result.positions().get(0).symbol()
        );

        assertAmount(
                "12",
                result.positions().get(0).quantity()
        );

        assertAmount("7610.00", result.availableCash());
    }

    @Test
    void shouldRemoveFullyClosedPositions() {

        List<Trade> trades = List.of(
                trade("AAPL", TradeSide.BUY, "10", "200.00"),
                trade("AAPL", TradeSide.SELL, "10", "220.00")
        );

        PortfolioSnapshot result = calculator.calculate(
                new BigDecimal("10000.00"),
                trades
        );

        assertTrue(result.positions().isEmpty());

        assertAmount("10200.00", result.availableCash());
    }

    @Test
    void shouldRejectNegativeCalculatedPosition() {

        List<Trade> trades = List.of(
                trade("AAPL", TradeSide.SELL, "10", "200.00")
        );

        assertThrows(
                IllegalStateException.class,
                () -> calculator.calculate(
                        new BigDecimal("10000.00"),
                        trades
                )
        );
    }

    @Test
    void shouldRejectNegativeCalculatedCash() {

        List<Trade> trades = List.of(
                trade("AAPL", TradeSide.BUY, "100", "200.00")
        );

        assertThrows(
                IllegalStateException.class,
                () -> calculator.calculate(
                        new BigDecimal("10000.00"),
                        trades
                )
        );
    }
}
