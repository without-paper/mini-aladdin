
package com.minialaddin.service;

import com.minialaddin.config.TestDatabaseConfiguration;
import com.minialaddin.dto.CreateTradeRequest;
import com.minialaddin.dto.TradeExecutionResult;
import com.minialaddin.model.Portfolio;
import com.minialaddin.model.TradeSide;
import com.minialaddin.repository.TradeRepository;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

import java.math.BigDecimal;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@Import(TestDatabaseConfiguration.class)
class TradeIdempotencyConcurrencyIntegrationTest {

    @Autowired
    private PortfolioService portfolioService;

    @Autowired
    private TradeService tradeService;

    @Autowired
    private TradeRepository tradeRepository;

    @Test
    void shouldExecuteSameIdempotencyKeyOnlyOnce() throws Exception {

        // 1. Create a fresh portfolio
        Portfolio portfolio = portfolioService.createPortfolio(
                "Idempotency Concurrency Fund",
                new BigDecimal("10000.00")
        );

        Long portfolioId = portfolio.getId();

        // 2. Create a trade request
        CreateTradeRequest request = new CreateTradeRequest(
                "AAPL",
                TradeSide.BUY,
                new BigDecimal("10"),
                new BigDecimal("200.00")
        );

        // Both threads must use the SAME idempotency key
        String requestKey = "concurrent-key-0001";

        // 3. Prepare two concurrent tasks
        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);

        Callable<TradeExecutionResult> task = () -> {
            ready.countDown();

            if (!start.await(10, TimeUnit.SECONDS)) {
                throw new IllegalStateException(
                        "Timed out waiting for start signal"
                );
            }

            return tradeService.executeTrade(
                    portfolioId,
                    request,
                    requestKey
            );
        };

        try {
            Future<TradeExecutionResult> first = executor.submit(task);
            Future<TradeExecutionResult> second = executor.submit(task);

            // 4. Wait until both threads are ready
            assertTrue(
                    ready.await(10, TimeUnit.SECONDS),
                    "Both worker threads should be ready"
            );

            // 5. Start both requests concurrently
            start.countDown();

            TradeExecutionResult result1 =
                    first.get(30, TimeUnit.SECONDS);

            TradeExecutionResult result2 =
                    second.get(30, TimeUnit.SECONDS);

            // 6. Both requests should return the same Trade ID
            assertNotNull(result1.trade().id());
            assertEquals(
                    result1.trade().id(),
                    result2.trade().id()
            );

            // 7. Exactly one request should create the trade;
            // the other should replay the existing trade.
            assertNotEquals(
                    result1.replayed(),
                    result2.replayed()
            );

            // 8. Verify that only one trade was persisted
            var savedTrades = tradeRepository
                    .findByPortfolioIdOrderByExecutedAtAscIdAsc(
                            portfolioId
                    );

            assertEquals(
                    1,
                    savedTrades.size(),
                    "Only one trade should exist in the database"
            );

            // 9. Verify the persisted trade data
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

        } finally {
            start.countDown();
            executor.shutdownNow();

            assertTrue(
                    executor.awaitTermination(10, TimeUnit.SECONDS),
                    "Worker threads should terminate"
            );
        }
    }
}
