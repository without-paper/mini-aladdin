
package com.minialaddin.metrics;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;

import org.springframework.stereotype.Component;

@Component
public class TradeMetrics {

    private final Counter executed;
    private final Counter rejected;
    private final Counter replayed;

    public TradeMetrics(MeterRegistry registry) {

        this.executed = Counter.builder(
                        "mini_aladdin.trades.executed"
                )
                .description("Successfully committed new trades")
                .register(registry);

        this.rejected = Counter.builder(
                        "mini_aladdin.trades.rejected"
                )
                .description("Trades rejected by business rules")
                .register(registry);

        this.replayed = Counter.builder(
                        "mini_aladdin.trades.replayed"
                )
                .description("Idempotent trade request replays")
                .register(registry);
    }

    public void recordExecuted() {
        executed.increment();
    }

    public void recordRejected() {
        rejected.increment();
    }

    public void recordReplayed() {
        replayed.increment();
    }
}
