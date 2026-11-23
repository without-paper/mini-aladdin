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
| Tests | JUnit 5 (requirement tags R1–R8), Mockito, MockMvc, Testcontainers, JaCoCo, PIT |
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
src/test/                         Unit and integration tests (JUnit tags R1–R8, integration)
docs/coursework/                  Software Testing coursework evidence (LO1–LO5)
performance/                      R7 benchmark scripts and statistics
scripts/ci-smoke.sh               HTTP smoke test run by CI
scripts/ci-recovery.sh            Database failure-and-recovery check run by CI
.github/workflows/ci.yml          CI workflow
Dockerfile                        Multi-stage Java application build
compose.yaml                      Local application + PostgreSQL stack
compose.smoke.yaml                Isolated application + PostgreSQL stack for the CI smoke and recovery checks
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

`clean verify` also writes a JaCoCo coverage report to `target/site/jacoco/`. Useful variants (on Windows use `.\mvnw.cmd` and quote the `-D` arguments):

```bash
./mvnw test -Dgroups=R5                      # tests for one requirement (tags R1–R8)
./mvnw test -DexcludedGroups=integration     # unit tests only; no Docker needed
./mvnw test-compile pitest:mutationCoverage  # PIT on PortfolioValuationCalculator; report in target/pit-reports/
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

`.github/workflows/ci.yml` runs on pushes to `master` and `feature/**`, on pull requests to `master` and on manual dispatch. It has two jobs:

1. **Java 21 · Maven Tests:** `./mvnw -B -ntp clean verify`, including the PostgreSQL Testcontainers integration tests and JaCoCo. Test reports are uploaded as an artifact.
2. **Docker Image Build:** runs only if the tests pass. It builds and loads the image, starts it with a fresh PostgreSQL 17 database in an isolated Compose project (`compose.smoke.yaml`) and runs `scripts/ci-smoke.sh`: health, portfolio creation, a BUY, an idempotent replay that must not create a duplicate trade, and a valuation that must reconcile. It then runs `scripts/ci-recovery.sh`, which stops PostgreSQL under the running application and checks that health reports 503, that a trade during the outage fails with a generic error body and stores nothing, and that after the restart health recovers and a retry with the same `Idempotency-Key` executes exactly once. Container logs are printed on failure, and the environment is always removed.

The workflow verifies a release candidate; it does not publish the image or deploy the application. PIT mutation testing and the R7 performance benchmark are run manually, not in CI. The backend has no authentication/authorisation, live market-data feed, exchange connectivity, fee/tax/corporate-action accounting, or production-grade deployment security. Read the [Engineering Review](docs/ENGINEERING_REVIEW.md) before considering any nonlocal deployment.

## Software Testing coursework

This branch also holds the evidence for a Software Testing coursework portfolio, in [`docs/coursework/`](docs/coursework/):

| Learning outcome | Documents |
|---|---|
| LO1 Requirements and test strategy | [LO1-requirements.md](docs/coursework/LO1-requirements.md) |
| LO2 Test plan and instrumentation | [LO2-test-plan.md](docs/coursework/LO2-test-plan.md) |
| LO3 Test design, techniques and results | [LO3-test-design.md](docs/coursework/LO3-test-design.md), [LO3-test-results.md](docs/coursework/LO3-test-results.md) |
| LO4 Limitations, targets and statistics | [LO4-evaluation.md](docs/coursework/LO4-evaluation.md) (97-test stage), [LO4-follow-up.md](docs/coursework/LO4-follow-up.md) |
| LO5 Reviews and automation | [LO5-review-and-automation.md](docs/coursework/LO5-review-and-automation.md), [LO5-recovery-experiment.md](docs/coursework/LO5-recovery-experiment.md) |

Current state: 137 passing tests (grown from a 69-test baseline; the latest review removed eight repeated tests and added edge-case, business-flow and recovery checks, see [LO4 follow-up §8](docs/coursework/LO4-follow-up.md#8-third-follow-up-requirement-driven-test-review-137-tests)); JaCoCo 591/615 lines (96.1%) and 136/180 branches (75.6%); PIT 23/23 mutants killed in `PortfolioValuationCalculator`, the only class mutated (last run at the 121-test stage; [LO4 follow-up §6](docs/coursework/LO4-follow-up.md#6-second-follow-up-test-code-review) explains why it still applies). Every log, report and CI run is listed in the [evidence index](docs/coursework/LO3-test-results.md#7-evidence-index).

## Documentation

- [Software Testing coursework evidence](docs/coursework/LO3-test-results.md#7-evidence-index)
- [Architecture & consistency model](docs/ARCHITECTURE.md)
- [API reference](docs/API.md)
- [Development, testing & deployment](docs/DEVELOPMENT.md)
- [Engineering review and release checklist](docs/ENGINEERING_REVIEW.md)
