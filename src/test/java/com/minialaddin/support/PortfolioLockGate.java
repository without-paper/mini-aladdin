
package com.minialaddin.support;

import com.minialaddin.repository.PortfolioRepository;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

/**
 * Scaffolding for deterministic contention on a portfolio row lock (R5).
 *
 * A latch alone only makes two requests start at roughly the same time;
 * if one finishes before the other begins, a concurrency test passes
 * without exercising the lock at all. This gate instead holds the same
 * PESSIMISTIC_WRITE lock that {@code TradeService} takes, waits until
 * PostgreSQL reports that the expected number of sessions are blocked
 * on it, and only then releases it. Every run therefore exercises the
 * "both requests queued on the lock" schedule.
 */
public final class PortfolioLockGate implements AutoCloseable {

    private static final String BLOCKED_SESSIONS = """
            SELECT count(*)
            FROM pg_stat_activity
            WHERE datname = current_database()
              AND wait_event_type = 'Lock'
            """;

    private final JdbcTemplate jdbcTemplate;
    private final ExecutorService holderThread =
            Executors.newSingleThreadExecutor();
    private final CountDownLatch lockHeld = new CountDownLatch(1);
    private final CountDownLatch release = new CountDownLatch(1);
    private Future<?> holder;

    private PortfolioLockGate(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    /** Takes the portfolio row lock in a separate transaction and keeps it. */
    public static PortfolioLockGate hold(
            PlatformTransactionManager transactionManager,
            PortfolioRepository portfolioRepository,
            JdbcTemplate jdbcTemplate,
            Long portfolioId
    ) throws InterruptedException {

        PortfolioLockGate gate = new PortfolioLockGate(jdbcTemplate);
        TransactionTemplate transaction =
                new TransactionTemplate(transactionManager);

        gate.holder = gate.holderThread.submit(() ->
                transaction.executeWithoutResult(status -> {

                    portfolioRepository
                            .findByIdForUpdate(portfolioId)
                            .orElseThrow();

                    gate.lockHeld.countDown();

                    try {
                        if (!gate.release.await(30, TimeUnit.SECONDS)) {
                            throw new IllegalStateException(
                                    "Lock gate was never released"
                            );
                        }
                    } catch (InterruptedException ex) {
                        Thread.currentThread().interrupt();
                        throw new IllegalStateException(ex);
                    }
                })
        );

        if (!gate.lockHeld.await(10, TimeUnit.SECONDS)) {
            gate.close();
            throw new IllegalStateException(
                    "Could not acquire the portfolio row lock"
            );
        }

        return gate;
    }

    /** Waits until at least {@code expected} sessions are blocked on a lock. */
    public void awaitBlockedSessions(int expected)
            throws InterruptedException {

        long deadline = System.nanoTime()
                + TimeUnit.SECONDS.toNanos(10);

        int blocked = 0;

        while (System.nanoTime() < deadline) {
            Integer count = jdbcTemplate.queryForObject(
                    BLOCKED_SESSIONS,
                    Integer.class
            );

            blocked = count == null ? 0 : count;

            if (blocked >= expected) {
                return;
            }

            Thread.sleep(20);
        }

        throw new AssertionError(
                "Expected " + expected
                        + " sessions blocked on the portfolio lock, saw "
                        + blocked
        );
    }

    /** Commits the holder transaction, letting the queued requests run. */
    public void release() throws Exception {
        release.countDown();
        holder.get(10, TimeUnit.SECONDS);
    }

    @Override
    public void close() {
        release.countDown();
        holderThread.shutdownNow();
    }
}
