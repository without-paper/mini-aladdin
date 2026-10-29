
package com.minialaddin.service;

import com.minialaddin.dto.PortfolioValuationResponse;
import com.minialaddin.exception.MissingMarketPriceException;
import com.minialaddin.model.*;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class PortfolioValuationCalculatorTest {

    private PortfolioValuationCalculator calculator;
    private Portfolio portfolio;

    @BeforeEach
    void setUp() {
        calculator = new PortfolioValuationCalculator();
        portfolio = new Portfolio(
                "Growth Fund",
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
                new BigDecimal(price)
        );
    }

    private MarketPrice quote(String symbol, String price) {
        MarketPrice marketPrice = mock(MarketPrice.class);

        when(marketPrice.getPrice())
                .thenReturn(new BigDecimal(price));

        when(marketPrice.getUpdatedAt())
                .thenReturn(Instant.parse("2026-10-29T12:00:00Z"));

        return marketPrice;
    }

    private void assertAmount(String expected, BigDecimal actual) {
        assertEquals(
                0,
                new BigDecimal(expected).compareTo(actual)
        );
    }

    @Test
    void shouldValueEmptyPortfolio() {
        PortfolioValuationResponse result = calculator.calculate(
                1L,
                "Growth Fund",
                new BigDecimal("10000.00"),
                List.of(),
                Map.of()
        );

        assertAmount("10000.00", result.totalValue());
        assertAmount("0.00", result.totalPnl());
        assertTrue(result.positions().isEmpty());
    }

    @Test
    void shouldCalculateMarketValueAndPnl() {
        List<Trade> trades = List.of(
                trade("AAPL", TradeSide.BUY, "10", "200.00"),
                trade("MSFT", TradeSide.BUY, "5", "300.00"),
                trade("AAPL", TradeSide.SELL, "3", "220.00")
        );

        Map<String, MarketPrice> prices = Map.of(
                "AAPL", quote("AAPL", "230.00"),
                "MSFT", quote("MSFT", "320.00")
        );

        PortfolioValuationResponse result = calculator.calculate(
                1L,
                "Growth Fund",
                new BigDecimal("10000.00"),
                trades,
                prices
        );

        assertAmount("7160.00", result.availableCash());
        assertAmount("3210.00", result.marketValue());
        assertAmount("10370.00", result.totalValue());
        assertAmount("60.00", result.realisedPnl());
        assertAmount("310.00", result.unrealisedPnl());
        assertAmount("370.00", result.totalPnl());

        assertEquals(2, result.positions().size());
    }

    @Test
    void shouldUseWeightedAverageCost() {
        List<Trade> trades = List.of(
                trade("AAPL", TradeSide.BUY, "10", "200.00"),
                trade("AAPL", TradeSide.BUY, "10", "240.00"),
                trade("AAPL", TradeSide.SELL, "5", "260.00")
        );

        PortfolioValuationResponse result = calculator.calculate(
                1L,
                "Growth Fund",
                new BigDecimal("10000.00"),
                trades,
                Map.of("AAPL", quote("AAPL", "250.00"))
        );

        assertAmount("220.00", result.positions().get(0).averageCost());
        assertAmount("3300.00", result.positions().get(0).costBasis());
        assertAmount("200.00", result.realisedPnl());
        assertAmount("450.00", result.unrealisedPnl());
        assertAmount("650.00", result.totalPnl());
    }

    @Test
    void shouldRejectMissingMarketPrice() {
        List<Trade> trades = List.of(
                trade("AAPL", TradeSide.BUY, "10", "200.00")
        );

        assertThrows(
                MissingMarketPriceException.class,
                () -> calculator.calculate(
                        1L,
                        "Growth Fund",
                        new BigDecimal("10000.00"),
                        trades,
                        Map.of()
                )
        );
    }

    @Test
    void shouldRejectSellingMoreThanHeld() {
        List<Trade> trades = List.of(
                trade("AAPL", TradeSide.SELL, "5", "200.00")
        );

        assertThrows(
                IllegalStateException.class,
                () -> calculator.calculate(
                        1L,
                        "Growth Fund",
                        new BigDecimal("10000.00"),
                        trades,
                        Map.of()
                )
        );
    }

    @Test
    void shouldPreserveRealisedPnlAfterPositionClosed() {


        List<Trade> trades = List.of(
                trade("AAPL", TradeSide.BUY, "10", "200.00"),
                trade("AAPL", TradeSide.SELL, "10", "250.00")
        );

        PortfolioValuationResponse result = calculator.calculate(
                1L,
                "Growth Fund",
                new BigDecimal("10000.00"),
                trades,
                Map.of()
        );

        assertAmount("10500.00", result.availableCash());
        assertAmount("0.00", result.marketValue());
        assertAmount("500.00", result.realisedPnl());
        assertAmount("0.00", result.unrealisedPnl());
        assertAmount("500.00", result.totalPnl());

        assertTrue(result.positions().isEmpty());
    }

    @Test
    void shouldResetAverageCostAfterClosingAndReopening() {


        List<Trade> trades = List.of(
                trade("AAPL", TradeSide.BUY, "10", "200.00"),
                trade("AAPL", TradeSide.SELL, "10", "220.00"),
                trade("AAPL", TradeSide.BUY, "5", "300.00")
        );

        PortfolioValuationResponse result = calculator.calculate(
                1L,
                "Growth Fund",
                new BigDecimal("10000.00"),
                trades,
                Map.of("AAPL", quote("AAPL", "320.00"))
        );

        assertAmount("200.00", result.realisedPnl());
        assertAmount("300.00", result.positions().get(0).averageCost());
        assertAmount("1500.00", result.positions().get(0).costBasis());
        assertAmount("100.00", result.unrealisedPnl());
        assertAmount("300.00", result.totalPnl());
    }
}
