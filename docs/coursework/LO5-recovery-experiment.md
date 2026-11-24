# LO5 — Database Failure and Recovery Experiment

## Objective

Evaluate the ability of Mini Aladdin to detect database failure and resume critical operations after PostgreSQL recovers. This experiment assesses fault detection and recoverability, rather than zero-downtime availability.

## Environment and Method

The experiment used a dedicated Docker Compose project (`mini-aladdin-recovery`) with a Spring Boot application container and an isolated PostgreSQL 17 database.

The database was stopped and restarted without intentionally stopping the application container. Application health was queried before, during and after the database interruption. The existing HTTP smoke test was then used to verify critical business operations after recovery.

## Acceptance Criteria and Results

| Scenario | Expected behaviour | Result |
|---|---|---|
| Normal operation | Health UP, HTTP 200 | PASS (operator-reported) |
| Database stopped | Database failure detected, health unavailable | PASS (operator-reported) |
| Database restored | Health returns to UP | PASS (operator-reported) |
| Business recovery | Portfolio creation, trade replay and valuation checks pass | PASS (operator-reported) |

All four scenarios were reported as passing. The actual HTTP responses and container logs should be retained as execution evidence where available.

## Evaluation

The exercise provides evidence that the application can detect loss of its database dependency and resume business operations after the dependency is restored in the tested environment.

The result does not prove continuous availability: database-dependent operations may be unavailable during the interruption. The system uses a single application instance, without rolling deployment, traffic failover or automatic rollback.

The experiment was conducted in an isolated local environment and did not affect an existing production deployment.

## Evidence

- [Isolated Compose environment](../../compose.smoke.yaml)
- [HTTP smoke test script](../../scripts/ci-smoke.sh)
- Local fault-injection results: operator-reported; original HTTP responses and container logs are not currently committed
- [Successful post-build CI smoke test](https://github.com/without-paper/mini-aladdin/actions/runs/44586139842)

## Update: Automated Recovery Check (23 November 2026)

The stop/restart is now automated as [`scripts/ci-recovery.sh`](../../scripts/ci-recovery.sh), which CI runs after the smoke test on every push ([LO5 §4.2](LO5-review-and-automation.md#42-automated-database-failure-and-recovery-check)). It uses the CI Compose project and adds checks the manual run did not make: the response to a trade during the outage, that nothing was stored, and that a retry with the same Idempotency-Key executes exactly once. Its output is in the logs of [CI run #44](https://github.com/without-paper/mini-aladdin/actions/runs/45266517282), the first run, and of every later run. The operator-reported results above are kept as recorded and are not reclassified as CI evidence.
