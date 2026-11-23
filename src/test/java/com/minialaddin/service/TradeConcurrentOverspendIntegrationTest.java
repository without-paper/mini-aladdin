
package com.minialaddin.service;

import com.minialaddin.config.TestDatabaseConfiguration;
import com.minialaddin.dto.CreateTradeRequest;
import com.minialaddin.dto.PortfolioSnapshot;
import com.minialaddin.exception.TradeRejectedException;
import com.minialaddin.model.Portfolio;
import com.minialaddin.model.Trade;
import com.minialaddin.model.TradeSide;
import com.minialaddin.repository.PortfolioRepository;
import com.minialaddin.repository.TradeRepository;
import com.minialaddin.support.PortfolioLockGate;

import io.micrometer.core.instrument.MeterRegistry;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

/**
 * R5: concurrent transactions shall preserve portfolio consistency.
 *
 * Two BUYs with different idempotency keys each cost 600.00 against
 * 1,000.00 of cash: either is affordable alone, both together are not.
 * Without the PESSIMISTIC_WRITE portfolio lock, both could read the same
 * history, both pass the cash check and overspend the portfolio.
 * {@link PortfolioLockGate} keeps the lock until two sessions are waiting
 * on a lock, so both requests are queued before either proceeds (see its
 * Javadoc for the isolation assumption this relies on).
 */
@Tag("R1")
@Tag("R5")
@Tag("integration")
@SpringBootTest
@Import(TestDatabaseConfiguration.class)
class TradeConcurrentOverspendIntegrationTest {

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

    private double counter(String name) {
        return meterRegistry.get(name).counter().count();
    }

    private String attempt(
            Long portfolioId,
            CreateTradeRequest request,
            String requestKey
    ) {
        try {
            tradeService.executeTrade(portfolioId, request, requestKey);
            return "EXECUTED";
        } catch (TradeRejectedException ex) {
            return "REJECTED: " + ex.getMessage();
        }
    }

    @Test
    @DisplayName("R5-C1: two concurrent BUYs with different keys cannot overspend cash")
    void shouldNotOverspendWhenDifferentTradesRace() throws Exception {

        Portfolio portfolio = portfolioService.createPortfolio(
                "Concurrent Overspend Fund",
                new BigDecimal("1000.00")
        );

        Long id = portfolio.getId();

        CreateTradeRequest first = new CreateTradeRequest(
                "AAPL", TradeSide.BUY,
                new BigDecimal("6"), new BigDecimal("100.00")
        );

        CreateTradeRequest second = new CreateTradeRequest(
                "MSFT", TradeSide.BUY,
                new BigDecimal("6"), new BigDecimal("100.00")
        );

        double executedBefore = counter("mini_aladdin.trades.executed");
        double rejectedBefore = counter("mini_aladdin.trades.rejected");

        ExecutorService workers = Executors.newFixedThreadPool(2);
        List<String> outcomes;

        try (PortfolioLockGate gate = PortfolioLockGate.hold(
                transactionManager, portfolioRepository, jdbcTemplate, id
        )) {
            Future<String> a = workers.submit(
                    () -> attempt(id, first, "overspend-key-a001")
            );
            Future<String> b = workers.submit(
                    () -> attempt(id, second, "overspend-key-b002")
            );

            // Both requests are now waiting for the same row lock.
            gate.awaitBlockedSessions(2);
            gate.release();

            outcomes = List.of(
                    a.get(30, TimeUnit.SECONDS),
                    b.get(30, TimeUnit.SECONDS)
            );
        } finally {
            workers.shutdownNow();
            assertTrue(workers.awaitTermination(10, TimeUnit.SECONDS));
        }

        // Exactly one trade wins; the other sees the committed trade.
        assertEquals(1, Collections.frequency(outcomes, "EXECUTED"),
                outcomes::toString);
        assertEquals(1, Collections.frequency(
                        outcomes, "REJECTED: Insufficient cash"),
                outcomes::toString);

        List<Trade> trades = tradeRepository
                .findByPortfolioIdOrderByExecutedAtAscIdAsc(id);

        assertEquals(1, trades.size());

        // Independent oracle: 1000.00 - 600.00 = 400.00, never negative.
        PortfolioSnapshot snapshot = new PortfolioCalculationService()
                .calculate(portfolio.getInitialCash(), trades);

        assertEquals(0, new BigDecimal("400.00")
                .compareTo(snapshot.availableCash()));

        // Instrumentation distinguishes the two outcomes (R8).
        assertEquals(executedBefore + 1,
                counter("mini_aladdin.trades.executed"));
        assertEquals(rejectedBefore + 1,
                counter("mini_aladdin.trades.rejected"));
    }
}
