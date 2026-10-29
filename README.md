# Mini Aladdin

**A Java 21 / Spring Boot portfolio-management and simulated trade-execution backend.**

Mini Aladdin is an independent educational project focused on transaction-safe trade execution, idempotent HTTP requests, portfolio accounting, valuation consistency, testing, and reproducible delivery. It supports **simulated** BUY/SELL trades and manually maintained market prices; it does **not** connect to a broker or execute real orders.

> **Independence:** This project is not affiliated with, endorsed by, or connected to BlackRock or its Aladdin platform. It is not investment advice or a production trading system.

## Highlights

- Create and retrieve portfolios; report cash balances and security positions reconstructed from trade history.
- Execute simulated BUY/SELL trades with Spring transactions, PostgreSQL pessimistic portfolio-row locking, cash/holding checks, and a portfolio-scoped `Idempotency-Key`.
- Detect conflicting reuse of idempotency keys via a SHA-256 fingerprint of canonicalised trade details.
- Update and retrieve simulated market prices; compute market value, moving weighted-average cost, realised/unrealised P&L, and portfolio total value.
- Use a repeatable-read valuation transaction and a quote freshness rule (24-hour maximum age; five-minute future timestamp tolerance).
- Expose structured JSON logs, `X-Correlation-ID`, RFC 9457 Problem Details, Actuator health and metrics, and trade counters.
- Validate with JUnit, Mockito, Spring MockMvc and PostgreSQL Testcontainers; build a Docker image, run a Docker Compose stack, and execute GitHub Actions CI.

## Technology

| Area | Stack |
|---|---|
| Runtime | Java 21, Spring Boot 4.1.x, Spring MVC |
| Persistence | PostgreSQL 17, Spring Data JPA, Hibernate, Flyway |
| Validation and errors | Jakarta Bean Validation, RFC 9457 `ProblemDetail` |
| Observability | SLF4J/MDC, structured JSON logging, Actuator, Micrometer |
| Tests | JUnit 5, Mockito, MockMvc, Testcontainers |
| Build / delivery | Maven Wrapper, Docker multi-stage build, Docker Compose, GitHub Actions |

## Project layout

```text
src/main/java/com/minialaddin/
├── MiniAladdinApplication.java
├── config/          CorrelationIdFilter
├── controller/      Portfolio, Trade, Report, Valuation, MarketPrice controllers
├── dto/             HTTP request/response records and internal snapshot/result records
├── exception/       Domain exceptions and GlobalExceptionHandler
├── metrics/         TradeMetrics and TransactionalTradeMetrics
├── model/           Portfolio, Trade, MarketPrice, TradeSide
├── repository/      PortfolioRepository, TradeRepository, MarketPriceRepository
└── service/         Portfolio, trade, reporting, market-price and valuation logic
src/main/resources/db/migration/   Flyway V1–V4
src/test/                         Unit and integration tests
.github/workflows/ci.yml          CI workflow
Dockerfile                        Multi-stage Java application build
compose.yaml                      Local application + PostgreSQL stack
```

The controller layer is intentionally thin; business rules reside in services. Financial calculations and freshness validation are separate service-layer components. See [Architecture](docs/ARCHITECTURE.md).

## Quick start: existing-database Compose setup

**Requirements:** Docker Engine/Desktop with Docker Compose. The current Compose configuration was designed to **reuse an existing PostgreSQL named volume**; it is not yet a zero-setup fresh installation.

1. Clone the repository:

   ```bash
   git clone https://github.com/without-paper/mini-aladdin.git
   cd mini-aladdin
   ```

2. Copy `.env.example` to `.env`. Set `POSTGRES_USER`, `POSTGRES_DB`, `POSTGRES_PASSWORD`, and **the exact existing Docker volume name** in `POSTGRES_DATA_VOLUME`. The existing volume must contain a compatible PostgreSQL 17 database. Use `docker volume ls` and inspect the prior database mount before changing containers. Do not commit `.env`.
3. Start the stack:

   ```bash
   docker compose config --quiet
   docker compose up --build -d
   docker compose ps
   ```

4. Verify the application:

   ```bash
   curl http://localhost:8080/actuator/health
   ```

   A healthy instance responds with `{"status":"UP"}`.
5. Stop without deleting storage:

   ```bash
   docker compose stop
   ```

**Do not use `docker compose down -v` to clean up a database whose data you intend to preserve.** Do not point two PostgreSQL processes at the same data directory. For detailed host/Windows instructions, see [Development & Deployment](docs/DEVELOPMENT.md).

## Run tests

Docker must be running for PostgreSQL Testcontainers tests.

```powershell
# Windows PowerShell
.\mvnw.cmd clean verify
```

```bash
# Linux/macOS
./mvnw clean verify
```

## REST API overview

| Method | Endpoint | Purpose |
|---|---|---|
| `POST` | `/api/portfolios` | Create a portfolio (`201`) |
| `GET` | `/api/portfolios/{id}` | Retrieve a portfolio (`200`) |
| `GET` | `/api/portfolios/{portfolioId}/cash` | Calculate available cash (`200`) |
| `GET` | `/api/portfolios/{portfolioId}/positions` | Calculate current positions (`200`) |
| `GET` | `/api/portfolios/{portfolioId}/trades` | List trades (`200`) |
| `POST` | `/api/portfolios/{portfolioId}/trades` | New trade (`201`) or idempotent replay (`200`) |
| `PUT` | `/api/market-prices/{symbol}` | Upsert a simulated price (`200`) |
| `GET` | `/api/market-prices/{symbol}` | Read simulated price (`200`) |
| `GET` | `/api/portfolios/{portfolioId}/valuation` | Cash, positions, valuation and P&L (`200`) |

For precise request constraints, response fields, error semantics and examples, see [API Reference](docs/API.md). No public API authentication is implemented.

## Observability

- `GET /actuator/health`: application health (including the enabled database health indicator).
- `GET /actuator/metrics`: available Micrometer metrics.
- `GET /actuator/metrics/http.server.requests`: HTTP request measurements.
- Custom counters: `mini_aladdin.trades.executed`, `mini_aladdin.trades.rejected`, `mini_aladdin.trades.replayed`.
- Request tracing: `X-Correlation-ID` response header; handled Problem Details responses also include `correlationId` in their JSON body.

Do not expose Actuator metrics to untrusted networks without protection.

## CI and limitations

`.github/workflows/ci.yml` runs the Maven test suite with Java 21 and validates the multi-stage Docker build. It does not publish the image or deploy the application. The backend has no authentication/authorisation, live market-data feed, exchange connectivity, fee/tax/corporate-action accounting, or production-grade deployment security. Read the [Engineering Review](docs/ENGINEERING_REVIEW.md) before considering any nonlocal deployment.

## Documentation

- [Architecture & consistency model](docs/ARCHITECTURE.md)
- [API reference](docs/API.md)
- [Development, testing & deployment](docs/DEVELOPMENT.md)
- [Engineering review and release checklist](docs/ENGINEERING_REVIEW.md)
