
package com.minialaddin.service;

import com.minialaddin.config.TestDatabaseConfiguration;
import com.minialaddin.dto.CreateTradeRequest;
import com.minialaddin.dto.PortfolioValuationResponse;
import com.minialaddin.dto.PositionValuationResponse;
import com.minialaddin.model.Portfolio;
import com.minialaddin.model.TradeSide;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.math.BigDecimal;
import java.util.Locale;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;

/**
 * R3/R5: a valuation must be computed from one consistent snapshot.
 *
 * Unlike {@link PortfolioSnapshotIsolationIntegrationTest}, which checks
 * the PostgreSQL behaviour the design relies on, this test drives the real
 * {@code PortfolioValuationService}. A spy on the position calculator runs
 * between "trades loaded" and "prices loaded"; at that moment another
 * thread commits a new trade and a new price. Under the service's
 * REPEATABLE_READ transaction the valuation must still use the old price.
 * Downgrading the isolation level would mix old trades with the new price
 * and fail this test.
 */
@Tag("R3")
@Tag("R5")
@Tag("integration")
@SpringBootTest
@Import(TestDatabaseConfiguration.class)
class PortfolioValuationConsistencyIntegrationTest {

    @MockitoSpyBean
    private PortfolioCalculationService positionCalculator;

    @Autowired
    private PortfolioValuationService valuationService;

    @Autowired
    private PortfolioService portfolioService;

    @Autowired
    private TradeService tradeService;

    @Autowired
    private MarketPriceService marketPriceService;

    private static void assertAmount(String expected, BigDecimal actual) {
        assertEquals(0, new BigDecimal(expected).compareTo(actual),
                () -> "expected " + expected + " but was " + actual);
    }

    private static CreateTradeRequest buy(
            String symbol,
            String quantity
    ) {
        return new CreateTradeRequest(
                symbol, TradeSide.BUY,
                new BigDecimal(quantity), new BigDecimal("100.00")
        );
    }

    @Test
    @DisplayName("R5-S1: valuation uses one snapshot while a trade and a price commit mid-valuation")
    void shouldValueFromOneSnapshotDespiteConcurrentCommits()
            throws Exception {

        String symbol = "V" + UUID.randomUUID().toString()
                .replace("-", "").substring(0, 9)
                .toUpperCase(Locale.ROOT);

        Portfolio portfolio = portfolioService.createPortfolio(
                "Snapshot Valuation Fund",
                new BigDecimal("10000.00")
        );

        Long id = portfolio.getId();

        tradeService.executeTrade(id, buy(symbol, "10"),
                "snapshot-buy-0001");
        marketPriceService.updatePrice(symbol, new BigDecimal("200.00"));

        AtomicBoolean interleaved = new AtomicBoolean();
        AtomicReference<Integer> isolation = new AtomicReference<>();
        ExecutorService otherClient = Executors.newSingleThreadExecutor();

        PortfolioValuationResponse during;

        try {
            doAnswer(invocation -> {
                if (interleaved.compareAndSet(false, true)) {
                    isolation.set(TransactionSynchronizationManager
                            .getCurrentTransactionIsolationLevel());

                    // Trades are loaded, prices are not: commit both now.
                    otherClient.submit(() -> {
                        tradeService.executeTrade(id, buy(symbol, "5"),
                                "snapshot-buy-0002");
                        marketPriceService.updatePrice(symbol,
                                new BigDecimal("250.00"));
                    }).get(30, TimeUnit.SECONDS);
                }
                return invocation.callRealMethod();
            }).when(positionCalculator).calculate(any(), any());

            during = valuationService.getValuation(id);

        } finally {
            otherClient.shutdownNow();
        }

        assertTrue(interleaved.get(), "The concurrent commit did not run");
        assertEquals(Isolation.REPEATABLE_READ.value(), isolation.get());

        // Snapshot taken before the concurrent commit:
        // cash 10000 - 1000, 10 shares at the old price 200.00.
        PositionValuationResponse position = during.positions().get(0);
        assertAmount("9000.00", during.availableCash());
        assertAmount("10", position.quantity());
        assertAmount("200.00", position.marketPrice());
        assertAmount("2000.00", during.marketValue());
        assertAmount("11000.00", during.totalValue());

        // A later valuation sees both commits, proving they happened.
        PortfolioValuationResponse after = valuationService.getValuation(id);
        PositionValuationResponse latest = after.positions().get(0);
        assertAmount("8500.00", after.availableCash());
        assertAmount("15", latest.quantity());
        assertAmount("250.00", latest.marketPrice());
        assertAmount("3750.00", after.marketValue());
    }
}
