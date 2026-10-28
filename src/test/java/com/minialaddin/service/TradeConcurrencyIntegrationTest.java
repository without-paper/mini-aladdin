
package com.minialaddin.service;

import com.minialaddin.dto.CreateTradeRequest;
import com.minialaddin.exception.TradeRejectedException;
import com.minialaddin.model.Portfolio;
import com.minialaddin.model.TradeSide;
import com.minialaddin.repository.TradeRepository;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.math.BigDecimal;
import java.util.concurrent.*;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
class TradeConcurrencyIntegrationTest {

    @Autowired
    private PortfolioService portfolioService;

    @Autowired
    private TradeService tradeService;

    @Autowired
    private TradeRepository tradeRepository;

    @Test
    void shouldPreventConcurrentOverspending() throws Exception {

        // Create a fresh portfolio.
        Portfolio portfolio = portfolioService.createPortfolio(
                "Concurrency Test Fund",
                new BigDecimal("1000.00")
        );

        Long portfolioId = portfolio.getId();

        CreateTradeRequest request = new CreateTradeRequest(
                "AAPL",
                TradeSide.BUY,
                new BigDecimal("4"),
                new BigDecimal("200.00")
        );

        ExecutorService executor =
                Executors.newFixedThreadPool(2);

        CountDownLatch start = new CountDownLatch(1);

        Callable<Boolean> task = () -> {
            start.await();

            try {
                tradeService.executeTrade(portfolioId, request);
                return true;
            } catch (TradeRejectedException ex) {
                return false;
            }
        };

        try {
            Future<Boolean> first = executor.submit(task);
            Future<Boolean> second = executor.submit(task);

            // Release both worker threads together.
            start.countDown();

            boolean result1 = first.get(15, TimeUnit.SECONDS);
            boolean result2 = second.get(15, TimeUnit.SECONDS);

            int successes =
                    (result1 ? 1 : 0) + (result2 ? 1 : 0);

            assertEquals(1, successes);

            long savedTrades = tradeRepository
                    .findByPortfolioIdOrderByExecutedAtAscIdAsc(
                            portfolioId
                    )
                    .size();

            assertEquals(1, savedTrades);

        } finally {
            executor.shutdownNow();
        }
    }
}
