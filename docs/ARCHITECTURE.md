# Architecture and Consistency Model

## Purpose and scope

Mini Aladdin is a Java Spring Boot backend for simulated portfolio operations. Its design prioritises transparent accounting and repeatable tests over high-volume trading throughput. The class inventory and HTTP mappings below are grounded in the supplied source review; implementation details outside the supplied classes are identified as architectural context rather than verified line-by-line.

## Source layout

```text
com.minialaddin
├── config/CorrelationIdFilter.java
├── controller/
│   ├── PortfolioController.java
│   ├── PortfolioReportController.java
│   ├── TradeController.java
│   ├── MarketPriceController.java
│   └── PortfolioValuationController.java
├── dto/                 12 records (requests, responses, snapshots)
├── exception/           GlobalExceptionHandler + 6 domain exceptions
├── metrics/
│   ├── TradeMetrics.java
│   └── TransactionalTradeMetrics.java
├── model/               Portfolio, Trade, MarketPrice, TradeSide
├── repository/          PortfolioRepository, TradeRepository, MarketPriceRepository
└── service/
    ├── PortfolioService.java
    ├── PortfolioReportService.java
    ├── PortfolioCalculationService.java
    ├── TradeService.java
    ├── MarketPriceService.java
    ├── PortfolioValuationService.java
    ├── PortfolioValuationCalculator.java
    └── MarketPriceFreshnessPolicy.java
```

## Request and dependency flow

```text
HTTP client
   |
   v
CorrelationIdFilter  ----> MDC / X-Correlation-ID
   |
   v
Spring MVC Controllers ----> GlobalExceptionHandler (ProblemDetail)
   |
   +--> PortfolioController ---------> PortfolioService
   +--> PortfolioReportController ---> PortfolioReportService
   +--> TradeController -------------> TradeService
   +--> MarketPriceController -------> MarketPriceService
   +--> PortfolioValuationController -> PortfolioValuationService
                                        |
                                        +--> Calculation / freshness components
                                        +--> Spring Data repositories
                                                     |
                                              Hibernate / JPA
                                                     |
                                               PostgreSQL 17

Cross-cutting: TradeMetrics / TransactionalTradeMetrics / Actuator
```

This is a responsibility map, **not** a fully verified constructor-level dependency graph of every service.

## Domain model

- **Portfolio:** identifier, name, initial cash.
- **Trade:** portfolio reference, symbol, `BUY`/`SELL` side, quantity, price, execution time, idempotency key and canonical request fingerprint.
- **MarketPrice:** symbol, price, last-updated time.
- **TradeSide:** enum used by the trade request and response.

`PortfolioSnapshot` is an internal result containing available cash and a list of `PositionResponse`; it is not a separate public endpoint. `TradeExecutionResult` holds `TradeResponse` plus an internal `replayed` flag; the controller uses that flag to select HTTP `201` or `200`, but the public response body is `TradeResponse` only.

## Portfolio reporting

`PortfolioReportController` exposes `/cash` and `/positions` beneath `/api/portfolios/{portfolioId}`. `PortfolioReportService` is the reporting façade, while `PortfolioCalculationService` reconstructs available cash and position quantities from trade records. These reports differ from market valuation: they do not themselves need live/current quote prices.

## Trade execution and concurrency

The supplied `TradeService.executeTrade` runs under `@Transactional`:

1. Validate the required `Idempotency-Key` against `[A-Za-z0-9_-]{8,128}`.
2. Validate/normalise the symbol to uppercase, validate side, quantity and price, and compute a SHA-256 fingerprint from `symbol|side|quantity|price` using canonical decimal strings.
3. Acquire the portfolio row through `PortfolioRepository.findByIdForUpdate(portfolioId)` (a pessimistic database lock in the established design).
4. Look up the idempotency key **after acquiring the lock**. The same key plus matching fingerprint returns the stored result; the same key with a different fingerprint raises `IdempotencyConflictException`.
5. For a new request, load trade history ordered by execution time and ID, reconstruct available cash and the relevant symbol position.
6. Reject a BUY with insufficient cash or a SELL with insufficient shares.
7. Persist via `tradeRepository.saveAndFlush(...)`, register a transaction `afterCommit` callback for the executed metric, and return the new trade.

```text
POST trade
  -> validate / fingerprint
  -> lock Portfolio
  -> idempotency lookup
       | same key + body -> stored TradeResponse (HTTP 200)
       | same key + different body -> HTTP 409
       v new request
  -> replay historical trades
  -> BUY cash / SELL share check
  -> saveAndFlush
  -> transaction commit -> executed Counter
  -> HTTP 201
```

`saveAndFlush()` is **not** a commit. The `TransactionalTradeMetrics` callback is designed to count committed executions, not merely persisted but later rolled-back operations. A row lock serialises conflicting operations on one portfolio, but reduces write concurrency for that portfolio. The full-history calculation is also a scaling concern.

**Observed metrics defect:** The supplied source calls `tradeMetrics.recordReplayed()` just before returning a newly executed trade (`replayed=false`) and does not call it in the actual replay branch. It must be moved to the matching-replay branch; see [Engineering Review](ENGINEERING_REVIEW.md). Do not describe this counter as correct until fixed.

## Financial calculations

The service layer and calculators use `BigDecimal` for monetary arithmetic:

```text
Available cash = Initial cash - Σ(BUY quantity × price) + Σ(SELL quantity × price)
Position quantity = Σ(BUY quantity) - Σ(SELL quantity), per symbol
Market value = Σ(position quantity × quoted market price)
Total value = Available cash + Market value
Total P&L = Total value - Initial cash
Total P&L = Realised P&L + Unrealised P&L
```

Valuation uses a moving weighted-average cost basis and reports realised/unrealised P&L. `PortfolioValuationResponse` contains portfolio totals and nested `PositionValuationResponse` entries, each with average cost, cost basis, market price, price timestamp, market value and unrealised P&L. The actual calculator implementation should remain the source of truth for rounding semantics.

## Valuation consistency and freshness

`PortfolioValuationService` was developed using `@Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)` to ensure a consistent PostgreSQL snapshot across portfolio, trades and price queries. The supplied source review does not include this particular class body; confirm the isolation annotation before publishing a formal correctness claim.

The included `MarketPriceFreshnessPolicy` source **does** establish its exact rule: a required quote timestamp is rejected if null, older than 24 hours, or more than five minutes in the future. The comparison is inclusive at the bounds. The policy examines each position in the valuation report.

## Errors and observability

`GlobalExceptionHandler` extends Spring's `ResponseEntityExceptionHandler`. Its supplied mapping is:

| Handler group | HTTP status |
|---|---|
| `PortfolioNotFoundException`, `MarketPriceNotFoundException` | `404` |
| `TradeRejectedException`, `IllegalArgumentException` | `400` |
| `IdempotencyConflictException`, `MissingMarketPriceException`, `StaleMarketPriceException` | `409` |
| Bean Validation (invalid request DTO) | `400`, with an `errors` array |
| Unexpected exceptions | `500` with a generic message |

Error bodies use `application/problem+json` with `type`, `title`, `status`, `detail`, `instance`, and a conditional `correlationId` extension. `CorrelationIdFilter` supplies the per-request MDC value (per prior implementation); its source is not in the supplied review. Spring MVC exception handling does not automatically cover every failure in filters or the servlet container.

Actuator exposes local health and metrics endpoints. `TradeMetrics` and `TransactionalTradeMetrics` separate business-event counting from post-commit success accounting. Counters are process-local unless exported to persistent monitoring infrastructure.

## Database migrations

The supplied migration file inventory is:

```text
V1__create_portfolios.sql
V2__create_portfolios.sql
V3__add_trade_idempotency.sql
V4__create_market_prices.sql
```

**Historical naming exception:** V2 has the legacy filename `V2__create_portfolios.sql` despite containing the trade-table migration in the project's established history. Do **not** rename an already-applied migration or silently rewrite its contents: Flyway checks the recorded migration history.

## Deployment and trade-offs

The local stack runs the Java application and PostgreSQL 17 in Docker Compose with an internal network and a database health dependency. The current deployment was configured to reuse an **existing external named Docker volume**. The reviewed source text did not include the live `compose.yaml`; confirm its actual environment names and volume mounts before sharing a turnkey setup claim.

**Known trade-offs:** reconstructing balances from historical trades is easy to reason about but grows with history; pessimistic row locks protect consistency but limit same-portfolio throughput; in-memory counters aid debugging but are not durable accounting records; manually updated market prices are a simulation, not a production price feed.
