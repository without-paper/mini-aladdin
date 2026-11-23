
package com.minialaddin.metrics;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * R8: the after-commit execution metric must refuse to record when it
 * cannot be tied to a committing transaction, rather than counting a trade
 * that might later roll back. Both guard branches were previously untested.
 */
@Tag("R8")
class TransactionalTradeMetricsTest {

    private final TradeMetrics metrics = mock(TradeMetrics.class);

    private final TransactionalTradeMetrics transactionalMetrics =
            new TransactionalTradeMetrics(metrics);

    @AfterEach
    void resetTransactionState() {
        TransactionSynchronizationManager.setActualTransactionActive(false);
    }

    @Test
    @DisplayName("R8-M1: recording outside a transaction is rejected")
    void shouldRejectRecordingOutsideTransaction() {

        IllegalStateException ex = assertThrows(
                IllegalStateException.class,
                transactionalMetrics::recordExecutionAfterCommit
        );

        assertEquals(
                "Trade execution metrics require an active transaction",
                ex.getMessage()
        );
        verifyNoInteractions(metrics);
    }

    @Test
    @DisplayName("R8-M2: recording without transaction synchronisation is rejected")
    void shouldRejectRecordingWithoutSynchronization() {

        TransactionSynchronizationManager.setActualTransactionActive(true);

        IllegalStateException ex = assertThrows(
                IllegalStateException.class,
                transactionalMetrics::recordExecutionAfterCommit
        );

        assertEquals(
                "Transaction synchronization is not active",
                ex.getMessage()
        );
        verifyNoInteractions(metrics);
    }
}
