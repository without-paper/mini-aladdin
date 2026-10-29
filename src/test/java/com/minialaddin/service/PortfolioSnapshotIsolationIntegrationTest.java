
package com.minialaddin.service;

import com.minialaddin.config.TestDatabaseConfiguration;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.util.UUID;
import java.util.concurrent.*;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@Import(TestDatabaseConfiguration.class)
class PortfolioSnapshotIsolationIntegrationTest {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @Autowired
    private MarketPriceService marketPriceService;

    private BigDecimal readPrice(String symbol) {

        return jdbcTemplate.queryForObject(
                """
                SELECT price
                FROM market_prices
                WHERE symbol = ?
                """,
                BigDecimal.class,
                symbol
        );
    }

    private void assertAmount(
            String expected,
            BigDecimal actual
    ) {
        assertNotNull(actual);

        assertEquals(
                0,
                new BigDecimal(expected).compareTo(actual)
        );
    }

    @Test
    void shouldReadConsistentSnapshotDuringConcurrentUpdate()
            throws Exception {

        String symbol = "T" + UUID.randomUUID()
                .toString()
                .replace("-", "")
                .substring(0, 12)
                .toUpperCase();

        // Initial quote
        marketPriceService.updatePrice(
                symbol,
                new BigDecimal("200.00")
        );

        TransactionTemplate transaction =
                new TransactionTemplate(transactionManager);

        transaction.setIsolationLevel(
                TransactionDefinition.ISOLATION_REPEATABLE_READ
        );

        transaction.setReadOnly(true);

        ExecutorService executor =
                Executors.newSingleThreadExecutor();

        try {
            transaction.executeWithoutResult(status -> {

                // First read establishes the PostgreSQL snapshot
                BigDecimal firstPrice = readPrice(symbol);

                assertAmount("200.00", firstPrice);

                // A different thread uses a separate transaction
                Future<?> update = executor.submit(() -> {

                    marketPriceService.updatePrice(
                            symbol,
                            new BigDecimal("250.00")
                    );

                });

                try {
                    update.get(15, TimeUnit.SECONDS);
                } catch (Exception ex) {
                    throw new RuntimeException(ex);
                }

                // Same read transaction should see the old price
                BigDecimal secondPrice = readPrice(symbol);

                assertAmount("200.00", secondPrice);
            });

            // After the read transaction completes,
            // a new transaction sees the updated price
            BigDecimal latestPrice = readPrice(symbol);

            assertAmount("250.00", latestPrice);

        } finally {
            executor.shutdownNow();

            assertTrue(
                    executor.awaitTermination(
                            10,
                            TimeUnit.SECONDS
                    )
            );
        }
    }
}
