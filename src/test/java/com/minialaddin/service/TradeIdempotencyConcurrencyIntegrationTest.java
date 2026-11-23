
package com.minialaddin.service;

import com.minialaddin.config.TestDatabaseConfiguration;
import com.minialaddin.dto.CreateTradeRequest;
import com.minialaddin.dto.TradeExecutionResult;
import com.minialaddin.model.Portfolio;
import com.minialaddin.model.TradeSide;
import com.minialaddin.repository.PortfolioRepository;
import com.minialaddin.repository.TradeRepository;
import com.minialaddin.support.PortfolioLockGate;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;

import java.math.BigDecimal;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import io.micrometer.core.instrument.MeterRegistry;

import static org.junit.jupiter.api.Assertions.*;

/**
 * R2/R5: two concurrent requests with the same Idempotency-Key must create
 * exactly one trade.
 *
 * The earlier version started both requests from a latch, which does not
 * guarantee that they overlap: if one finished before the other began, the
 * test passed through the ordinary replay path without exercising the row
 * lock. {@link PortfolioLockGate} now holds the portfolio lock until
 * PostgreSQL reports both requests blocked on it, so every run exercises
 * the contended schedule.
 */
@Tag("R2")
@Tag("R5")
@Tag("integration")
@SpringBootTest
@Import(TestDatabaseConfiguration.class)
class TradeIdempotencyConcurrencyIntegrationTest {

    @Autowired
    private PortfolioService portfolioService;

    @Autowired
    private TradeService tradeService;

    @Autowired
    private TradeRepository tradeRepository;

    @Autowired
    private PortfolioRepository portfolioRepository;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private MeterRegistry meterRegistry;

    private double counterValue(String metricName) {
        return meterRegistry.get(metricName)
                .counter()
                .count();
    }

    @Test
    @DisplayName("R5-C2: two concurrent requests with the same Idempotency-Key create one trade")
    void shouldExecuteSameIdempotencyKeyOnlyOnce() throws Exception {

        // 1. Create a fresh portfolio
        Portfolio portfolio = portfolioService.createPortfolio(
                "Idempotency Concurrency Fund",
                new BigDecimal("10000.00")
        );

        Long portfolioId = portfolio.getId();

        double executedBefore = counterValue(
                "mini_aladdin.trades.executed"
        );

        double replayedBefore = counterValue(
                "mini_aladdin.trades.replayed"
        );

        // 2. Create a trade request
        CreateTradeRequest request = new CreateTradeRequest(
                "AAPL",
                TradeSide.BUY,
                new BigDecimal("10"),
                new BigDecimal("200.00")
        );

        // Both threads must use the SAME idempotency key
        String requestKey = "concurrent-key-0001";

        Callable<TradeExecutionResult> task = () ->
                tradeService.executeTrade(
                        portfolioId,
                        request,
                        requestKey
                );

        ExecutorService executor = Executors.newFixedThreadPool(2);

        TradeExecutionResult result1;
        TradeExecutionResult result2;

        try (PortfolioLockGate gate = PortfolioLockGate.hold(
                transactionManager,
                portfolioRepository,
                jdbcTemplate,
                portfolioId
        )) {
            Future<TradeExecutionResult> first = executor.submit(task);
            Future<TradeExecutionResult> second = executor.submit(task);

            // 3. Both requests are queued on the same row lock
            gate.awaitBlockedSessions(2);

            // 4. Release the lock: the requests now run one at a time
            gate.release();

            result1 = first.get(30, TimeUnit.SECONDS);
            result2 = second.get(30, TimeUnit.SECONDS);

        } finally {
            executor.shutdownNow();

            assertTrue(
                    executor.awaitTermination(10, TimeUnit.SECONDS),
                    "Worker threads should terminate"
            );
        }

        // 5. Both requests should return the same Trade ID
        assertNotNull(result1.trade().id());
        assertEquals(
                result1.trade().id(),
                result2.trade().id()
        );

        // 6. Exactly one request should create the trade;
        // the other should replay the existing trade.
        assertNotEquals(
                result1.replayed(),
                result2.replayed()
        );

        // 7. Verify that only one trade was persisted
        var savedTrades = tradeRepository
                .findByPortfolioIdOrderByExecutedAtAscIdAsc(
                        portfolioId
                );

        assertEquals(
                1,
                savedTrades.size(),
                "Only one trade should exist in the database"
        );

        // 8. Verify the persisted trade data
        var savedTrade = savedTrades.get(0);

        assertEquals(
                "AAPL",
                savedTrade.getSymbol()
        );

        assertEquals(
                TradeSide.BUY,
                savedTrade.getSide()
        );

        assertEquals(
                0,
                new BigDecimal("10").compareTo(
                        savedTrade.getQuantity()
                )
        );

        assertEquals(
                0,
                new BigDecimal("200.00").compareTo(
                        savedTrade.getPrice()
                )
        );

        assertEquals(
                requestKey,
                savedTrade.getRequestKey()
        );

        // 9. Verify instrumentation
        assertEquals(
                executedBefore + 1,
                counterValue("mini_aladdin.trades.executed"),
                "Only one new trade should be recorded as executed"
        );

        assertEquals(
                replayedBefore + 1,
                counterValue("mini_aladdin.trades.replayed"),
                "Exactly one request should be recorded as replayed"
        );
    }
}
