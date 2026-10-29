# Engineering Review and Release Gate

## Review scope and evidence

This audit reflects the Mini Aladdin M1–M6 development history and the supplied source-review extract containing all five controller classes, the request/response DTO records, `GlobalExceptionHandler`, `TradeService`, `MarketPriceFreshnessPolicy`, and the Flyway **file inventory**. Other implementation bodies, live Docker Compose configuration, Maven POM and test reports were **not** part of that source extract; claims about them reflect completed project stages and still require final CI/release verification.

The project is an **educational prototype, not production-ready trading infrastructure**.

## Verified design strengths

| Area | Evidence / benefit | Boundary |
|---|---|---|
| API contracts | Five controllers with nine documented routes; record-based DTOs; explicit new/replay trade statuses | No formal OpenAPI spec or API versioning |
| Request validation | Bean Validation on portfolio, trade and price requests; service checks balances and key syntax | No authentication / tenant isolation |
| Transactional trade logic | `@Transactional`, portfolio pessimistic-lock repository method, historical cash/position checks, `saveAndFlush` | Same-portfolio lock contention; full-history recalculation |
| Idempotency | Portfolio-scoped key lookup after lock, canonical SHA-256 fingerprint, 200 replay / 409 conflict | No independent persistent result for a rejected request |
| Valuation policy | DTOs expose cost, market values, P&L; quote timestamps must satisfy 24h age / 5m future skew | Simplified accounting and simulated prices |
| Errors | RFC 9457 `ProblemDetail` with explicit `about:blank`, mapped 400/404/409/500 | Filter/container-level failures may bypass MVC advice |
| CI and delivery | Testcontainers, Java 21 Maven CI, Docker build, Compose deployments were exercised during development | Current Compose is designed around an existing external volume |

## Release blocker: incorrect replay counter

The supplied `TradeService.java` has an observable **business-metrics bug**:

- The matching-idempotent-replay branch (`existingTrade.isPresent()` with matching fingerprint) returns `TradeExecutionResult(..., true)` **without calling** `tradeMetrics.recordReplayed()`.
- The newly executed trade path calls `transactionalTradeMetrics.recordExecutionAfterCommit()` and then calls `tradeMetrics.recordReplayed()` **just before** returning `TradeExecutionResult(..., false)`.

As written, new trades are incorrectly counted as replays and actual replays are not counted. This does **not** show that a duplicate trade is inserted; it shows the replay metric is wrong.

**Required fix in `src/main/java/com/minialaddin/service/TradeService.java`:**

```java
// In the existing-trade, matching-fingerprint branch:
tradeMetrics.recordReplayed();
return new TradeExecutionResult(
        TradeResponse.from(existing),
        true
);
```

Remove the later call to `tradeMetrics.recordReplayed()` from the **new-trade** success branch. Keep `transactionalTradeMetrics.recordExecutionAfterCommit()` on the new-trade path. Add/extend tests covering both events, rerun `clean verify`, and ensure both GitHub Actions jobs pass before tagging `v1.0.0`.

## Additional observations

1. **MDC / async scope:** The correlation filter in earlier stages handles synchronous HTTP processing. Asynchronous execution may require explicit context propagation.
2. **Generic `IllegalArgumentException`:** `GlobalExceptionHandler` maps all `IllegalArgumentException` instances to HTTP 400. A programming error of that type may therefore be misclassified as client input; use a dedicated invalid-request exception in a future iteration.
3. **Monitoring security:** Actuator metrics can reveal operational internals. Restrict exposed endpoints and add authorisation or network isolation before nonlocal deployment.
4. **Metric persistence:** In-memory Micrometer counters reset when the application process restarts and are not a substitute for transaction records.
5. **Performance scaling:** `TradeService` loads all historical trades when executing a new request. This grows with history and occurs while the portfolio lock is held. Add load tests and evaluate materialised projections and indexed retrieval only after measuring.
6. **Price freshness:** The `MarketPriceFreshnessPolicy` explicitly rejects missing timestamps, ages greater than 24h, or timestamps more than five minutes ahead. It is not market-data provenance verification.
7. **Database schema changes:** The legacy V2 migration name must remain compatible with Flyway history. The supplied inventory confirms V1–V4 filenames, not their SQL contents.

## Production-readiness gaps

- Authentication, authorisation and tenant isolation.
- HTTPS termination, secure ingress, API rate limiting and threat modelling.
- Production secret management (rather than plain environment configuration).
- Centralised logs, durable metrics and alerting.
- Tested recovery point/recovery time objectives, backup restoration drills and data retention controls.
- Broader financial accounting: fees, taxes, settlement, dividends, corporate actions, FX and asset-specific conventions.
- Exchange/broker connectivity, real-time validated market data and compliance controls.
- Formal load, stress, failure-injection and disaster-recovery tests.
- First-time installation without a pre-existing PostgreSQL Docker volume.

## Acceptance tests for v1.0.0

- [ ] Fix and test the replay-counter defect identified above.
- [ ] Confirm actual `pom.xml` project version and decide whether to set `1.0.0` before tagging.
- [ ] Run the full Java 21 test suite (`clean verify`), including Testcontainers.
- [ ] Confirm GitHub Actions **Maven Tests** and **Docker Image Build** jobs pass on the release commit.
- [ ] Confirm Compose starts the app/database stack and `/actuator/health` returns `UP`.
- [ ] Compare portfolio/trade/market-price counts against known data before and after container restart; verify Flyway V1–V4 checksums.
- [ ] Review `README.md`, API examples, `.env.example`, actual `compose.yaml` and Dockerfile for consistency.
- [ ] Ensure `.env`, backups, authentication tokens and real passwords are absent from tracked files and Git history; rotate any exposed secret.
- [ ] Confirm the release explicitly states simulated-trading and non-production limitations.
- [ ] Only after every check passes, merge the documentation PR, create an annotated `v1.0.0` tag and publish a GitHub release.

## Potential next versions

- **v1.1:** correct monitoring semantics, OpenAPI documentation, first-time Compose setup, pagination.
- **v1.2:** stronger domain error types and focused concurrency/performance benchmarking.
- **Future production-oriented research:** access control, secrets, telemetry export, recovery validation and richer financial domain modelling.
