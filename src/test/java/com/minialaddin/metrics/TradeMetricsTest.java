
package com.minialaddin.metrics;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class TradeMetricsTest {

    private SimpleMeterRegistry registry;
    private TradeMetrics metrics;

    @BeforeEach
    void setUp() {
        registry = new SimpleMeterRegistry();
        metrics = new TradeMetrics(registry);
    }

    @AfterEach
    void tearDown() {
        registry.close();
    }

    @Test
    void shouldStartAllCountersAtZero() {

        assertEquals(0.0, count("mini_aladdin.trades.executed"));
        assertEquals(0.0, count("mini_aladdin.trades.rejected"));
        assertEquals(0.0, count("mini_aladdin.trades.replayed"));
    }

    @Test
    void shouldCountSuccessfulExecutions() {

        metrics.recordExecuted();
        metrics.recordExecuted();

        assertEquals(2.0, count("mini_aladdin.trades.executed"));
        assertEquals(0.0, count("mini_aladdin.trades.rejected"));
    }

    @Test
    void shouldCountRejectedTrades() {

        metrics.recordRejected();

        assertEquals(1.0, count("mini_aladdin.trades.rejected"));
        assertEquals(0.0, count("mini_aladdin.trades.executed"));
    }

    @Test
    void shouldCountIdempotentReplaysSeparately() {

        metrics.recordExecuted();
        metrics.recordReplayed();
        metrics.recordReplayed();

        assertEquals(1.0, count("mini_aladdin.trades.executed"));
        assertEquals(2.0, count("mini_aladdin.trades.replayed"));
    }

    private double count(String name) {
        return registry.get(name).counter().count();
    }
}
