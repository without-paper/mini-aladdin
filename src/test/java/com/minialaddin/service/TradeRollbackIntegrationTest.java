
package com.minialaddin.service;

import com.minialaddin.config.TestDatabaseConfiguration;
import com.minialaddin.dto.CreateTradeRequest;
import com.minialaddin.dto.TradeExecutionResult;
import com.minialaddin.model.Portfolio;
import com.minialaddin.model.TradeSide;
import com.minialaddin.repository.TradeRepository;

import io.micrometer.core.instrument.MeterRegistry;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.*;

/**
 * R5/R8: a trade whose surrounding transaction fails must leave no trade
 * and no "executed" metric behind.
 *
 * The test checks that the failure is the simulated one and that the trade
 * really was written inside the transaction first. Without those checks,
 * any IllegalStateException thrown before the insert would also pass.
 */
@Tag("R5")
@Tag("R8")
@Tag("integration")
@SpringBootTest
@Import(TestDatabaseConfiguration.class)
class TradeRollbackIntegrationTest {

    private static final String SIMULATED_FAILURE =
            "Simulated transaction failure";

    @Autowired
    private PortfolioService portfolioService;

    @Autowired
    private TradeService tradeService;

    @Autowired
    private TradeRepository tradeRepository;

    @Autowired
    private TransactionTemplate transactionTemplate;

    @Autowired
    private MeterRegistry meterRegistry;

    private double executedCount() {
        return meterRegistry.get("mini_aladdin.trades.executed")
                .counter()
                .count();
    }

    @Test
    @DisplayName("R5-T1: a trade saved in a failing transaction is rolled back and not counted")
    void shouldRollbackTradeWhenTransactionFails() {

        Portfolio portfolio = portfolioService.createPortfolio(
                "Rollback Test Fund",
                new BigDecimal("10000.00")
        );

        Long id = portfolio.getId();

        CreateTradeRequest request = new CreateTradeRequest(
                "AAPL",
                TradeSide.BUY,
                new BigDecimal("10"),
                new BigDecimal("200.00")
        );

        double executedBefore = executedCount();

        IllegalStateException failure = assertThrows(
                IllegalStateException.class,
                () -> transactionTemplate.execute(status -> {

                    TradeExecutionResult result =
                            tradeService.executeTrade(
                                    id, request, "rollback-test-0001"
                            );

                    // The trade was inserted and is visible
                    // inside the still-open transaction.
                    assertFalse(result.replayed());
                    assertEquals(1, tradeRepository
                            .findByPortfolioIdOrderByExecutedAtAscIdAsc(id)
                            .size());

                    // Simulate an unexpected failure
                    // after the trade has been saved.
                    throw new IllegalStateException(SIMULATED_FAILURE);
                })
        );

        // The rollback was caused by the simulated failure,
        // not by an earlier error inside executeTrade.
        assertEquals(SIMULATED_FAILURE, failure.getMessage());

        long count = tradeRepository
                .findByPortfolioIdOrderByExecutedAtAscIdAsc(id)
                .size();

        assertEquals(0, count);

        // The after-commit metric must not count a rolled-back trade.
        assertEquals(executedBefore, executedCount());
    }
}
