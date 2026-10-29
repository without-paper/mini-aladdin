
package com.minialaddin.service;

import com.minialaddin.config.TestDatabaseConfiguration;
import com.minialaddin.dto.CreateTradeRequest;
import com.minialaddin.model.Portfolio;
import com.minialaddin.model.TradeSide;
import com.minialaddin.repository.TradeRepository;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@Import(TestDatabaseConfiguration.class)
class TradeRollbackIntegrationTest {

    @Autowired
    private PortfolioService portfolioService;

    @Autowired
    private TradeService tradeService;

    @Autowired
    private TradeRepository tradeRepository;

    @Autowired
    private TransactionTemplate transactionTemplate;

    @Test
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

        assertThrows(
                IllegalStateException.class,
                () -> transactionTemplate.execute(status -> {

                    tradeService.executeTrade(
                            id, request, "rollback-test-0001"
                    );

                    // Simulate an unexpected failure
                    // after the trade has been saved.
                    throw new IllegalStateException(
                            "Simulated transaction failure"
                    );
                })
        );

        long count = tradeRepository
                .findByPortfolioIdOrderByExecutedAtAscIdAsc(id)
                .size();

        assertEquals(0, count);
    }
}
