# Development, Testing and Deployment

## 1. Requirements and environments

- Java 21 and the repository Maven Wrapper (`mvnw`, `mvnw.cmd`).
- Docker Engine / Docker Desktop with Compose support (required by Testcontainers and local PostgreSQL deployment).
- Git; optional IntelliJ IDEA.
- Windows 11 PowerShell 5.1 is a supported local development shell. CI uses a GitHub-hosted Linux runner.

The commands below assume the project root contains `pom.xml`, `Dockerfile`, `compose.yaml` and `.env.example`. These infrastructure files were established in M6.1–M6.3; **the supplied source review contains Java source and Flyway filenames, not the live contents of these infrastructure files**, so check the exact Compose variable names against your committed configuration.

## 2. Windows local development

```powershell
cd C:\Users\xuron\Desktop\mini-aladdin
java -version
docker version
.\mvnw.cmd -version
```

Two mutually exclusive ways to serve HTTP on `localhost:8080`:

**Host Java application, Docker PostgreSQL only** (the project's local `application.properties` must point to the host-mapped PostgreSQL port and correct credentials):

```powershell
docker compose up -d db
.\mvnw.cmd spring-boot:run
```

**Entire application stack in Docker Compose:**

```powershell
docker compose up --build -d
docker compose ps
```

Do **not** start both Java processes on the same host `8080` port at once. If Compose's app service already owns the port, stop it or choose a different host mapping before launching Spring Boot directly.

## 3. Environment file and existing database

Copy `.env.example` to `.env` and enter the **existing** database credentials. In the M6.2 configuration the database service is `db`, internal JDBC host is `db:5432`, and the PostgreSQL data is an **external named Docker volume**, configured by a variable such as `POSTGRES_DATA_VOLUME`.

```powershell
Copy-Item .env.example .env
docker volume ls
docker compose config --quiet
```

Before selecting a volume, verify its real identity and mount destination (`docker inspect <old-db-container> --format '{{json .Mounts}}'`). The `external: true` configuration deliberately fails if that volume does not exist; it is **not** a fresh-install setup. PostgreSQL environment variables do not automatically change the credentials inside an already initialised database directory.

Never run two database containers simultaneously against the same data directory. Do not delete an existing volume to resolve port conflicts or Flyway errors.

## 4. Test and build commands

```powershell
# Windows
.\mvnw.cmd clean verify

# Focused tests
.\mvnw.cmd '-Dtest=TradeServiceTest' test
```

```bash
# Linux or macOS
./mvnw clean verify
```

Testcontainers-based tests require Docker but **do not** require the application's local Compose database as a testing backend: they create isolated PostgreSQL containers. The CI workflow also uses Docker for Testcontainers, then builds the application image only after tests succeed.

## 5. Docker commands

```powershell
# Build/start
 docker compose up --build -d
# Status
 docker compose ps
# Recent logs
 docker compose logs --tail=100 db
 docker compose logs --tail=100 app
# Health
 Invoke-RestMethod 'http://localhost:8080/actuator/health'
# Stop containers, preserve volume
 docker compose stop
```

A browser or `curl.exe -i http://localhost:8080/actuator/health` works for HTTP debugging. In Windows PowerShell 5.1, `Invoke-WebRequest` does **not** support `-SkipHttpErrorCheck`; use `curl.exe` or a `try/catch` when deliberately testing HTTP 4xx/5xx.

## 6. Database verification

```powershell
docker compose exec db psql -U mini_user -d mini_aladdin
```

Within `psql`:

```sql
SELECT COUNT(*) FROM portfolios;
SELECT COUNT(*) FROM trades;
SELECT COUNT(*) FROM market_prices;
SELECT installed_rank, version, description, success
FROM flyway_schema_history
ORDER BY installed_rank;
```

`mini_user`/`mini_aladdin` are the established example credentials/database names; use the values in your real `.env` if different. For the supplied project, expected migration file names are:

```text
V1__create_portfolios.sql
V2__create_portfolios.sql
V3__add_trade_idempotency.sql
V4__create_market_prices.sql
```

Do not rename the historically applied V2 file, even though its filename is misleading.

## 7. Backup without corrupting binary data on PowerShell 5.1

PowerShell 5.1 pipelines can transform native command output; **do not pipe binary custom-format `pg_dump -Fc` directly through `Set-Content`**. Produce the dump inside the database container, then use `docker cp`:

```powershell
New-Item -ItemType Directory -Force .\backups | Out-Null

docker compose exec -T db `
    pg_dump -U mini_user -d mini_aladdin `
    -Fc -f /tmp/mini-aladdin.dump
if ($LASTEXITCODE -ne 0) { throw 'pg_dump failed' }

$containerId = (docker compose ps -q db).Trim()
if (-not $containerId) { throw 'Database container not found' }

docker cp "${containerId}:/tmp/mini-aladdin.dump" `
    '.\backups\mini-aladdin.dump'
if ($LASTEXITCODE -ne 0) { throw 'docker cp failed' }
```

Keep `backups/` in `.gitignore`. Copying a dump is not the same as verifying recoverability: periodically restore **to a separate test database**, compare record counts and test representative queries. Never experiment with restoration against your only live database.

## 8. Debugging guide

| Symptom | Check |
|---|---|
| `127.0.0.1:5432 port is already allocated` | `docker ps` for an older PostgreSQL container; stop the old container after verifying/backup of mounts |
| Application `restarting` / exit code 1 | `docker compose logs --tail=200 app`, first exception and cause |
| Database healthy but application unavailable | Look for app start/exit loop; confirm `Started MiniAladdinApplication` and run `docker compose ps` |
| JDBC connection error | Internal host `db`, port `5432`, actual database/credentials |
| Flyway validation mismatch | Compare already-applied migration filenames/checksums; preserve V1–V4 history |
| Testcontainers cannot find Docker | Start Docker Desktop/Engine and inspect test reports |
| HTTP 409 valuation | Verify required simulated prices exist and are within the freshness window |
| HTTP 400 trade | Check body validation, idempotency key, cash and holdings |

**Orphan container warning:** renaming an old Compose service (for example `postgres` to `db`) leaves the old container as an orphan. Avoid `--remove-orphans` until the replacement is confirmed healthy and the original data/volume are verified. Avoid `docker compose down -v` when preserving data.

## 9. GitHub Actions and contribution workflow

- Use feature branches from `master`.
- Add unit/integration regressions for behavioural changes.
- Verify `./mvnw -B -ntp clean verify` on Linux CI and the Docker build job.
- Open a PR targeting `master`; merge only after both CI jobs succeed.
- Keep `.env`, database backups and sensitive operational files out of Git history.
- Keep already executed Flyway migrations immutable.

## 10. Deployment boundaries

This environment is intended for **local, trusted development**. The application has no implemented user authentication/authorisation, and metrics are not secured for public access. Do not publish the local ports to the internet or treat the Compose configuration as a hardened production deployment. See [Engineering Review](ENGINEERING_REVIEW.md).
