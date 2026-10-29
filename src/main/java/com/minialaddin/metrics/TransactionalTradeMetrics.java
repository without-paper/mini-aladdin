
package com.minialaddin.metrics;

import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

@Component
public class TransactionalTradeMetrics {

    private final TradeMetrics metrics;

    public TransactionalTradeMetrics(TradeMetrics metrics) {
        this.metrics = metrics;
    }

    public void recordExecutionAfterCommit() {

        if (!TransactionSynchronizationManager
                .isActualTransactionActive()) {
            throw new IllegalStateException(
                    "Trade execution metrics require an active transaction"
            );
        }

        if (!TransactionSynchronizationManager
                .isSynchronizationActive()) {
            throw new IllegalStateException(
                    "Transaction synchronization is not active"
            );
        }

        TransactionSynchronizationManager.registerSynchronization(
                new TransactionSynchronization() {

                    @Override
                    public void afterCommit() {
                        metrics.recordExecuted();
                    }
                }
        );
    }
}
