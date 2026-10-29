
package com.minialaddin.metrics;

import com.minialaddin.config.TestDatabaseConfiguration;

import io.micrometer.core.instrument.MeterRegistry;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@Import(TestDatabaseConfiguration.class)
class TransactionalTradeMetricsIntegrationTest {

    @Autowired
    private TransactionalTradeMetrics transactionalMetrics;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @Autowired
    private MeterRegistry meterRegistry;

    private double executedCount() {
        return meterRegistry
                .get("mini_aladdin.trades.executed")
                .counter()
                .count();
    }

    @Test
    void shouldCountOnlyCommittedTransactions() {

        TransactionTemplate template =
                new TransactionTemplate(transactionManager);

        double before = executedCount();

        template.executeWithoutResult(status -> {
            transactionalMetrics.recordExecutionAfterCommit();
        });

        assertEquals(before + 1, executedCount());

        template.executeWithoutResult(status -> {
            transactionalMetrics.recordExecutionAfterCommit();
            status.setRollbackOnly();
        });

        assertEquals(
                before + 1,
                executedCount(),
                "Rolled-back transaction must not increment the counter"
        );
    }
}
