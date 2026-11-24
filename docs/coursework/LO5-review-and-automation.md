# LO5 — Code Inspection and Test Automation

## 1. Review Scope and Criteria (LO5.1)

A checklist-based code inspection was selected to complement the existing automated tests. The review focuses on R3 (financial correctness) and R7 (performance measurement), following the Software Testing LO5 tutorial and Young and Pezzè Chapter 18.

The selected review criteria are correctness, consistency with requirements, unnecessary state, measurement validity, and maintainability.

### 1.1 Inspection Findings

| ID | Component | Finding | Severity and status |
|---|---|---|---|
| RV-01 | `performance/benchmark-concurrent.ps1` | `$allResults` was cleared inside the measurement loop, discarding collected observations before aggregate statistics were calculated. | Medium; fixed by retaining accumulated results and checking the expected request count. Verified with a 10-request execution. |
| RV-02 | `performance/benchmark-concurrent.ps1` | PowerShell `Start-Job` introduces process creation and scheduling overhead, limiting the validity of the resulting throughput measurement as a measure of server capacity. | Measurement limitation; documented |
| RV-03 | `PortfolioValuationCalculator.java` | `PositionState.realisedPnl` is updated but never used to calculate the returned valuation. This redundant state increases maintenance complexity. | Low; improvement proposed |

### 1.2 Evaluation

The inspection identified a defect affecting the integrity of a supplementary benchmark and a maintainability weakness in the financial calculation code.

RV-01 was corrected by removing the unintended reset of `$allResults` inside the measurement loop, explicitly initialising the result collection and adding a check that the number of collected observations matches the requested count.

A follow-up execution with 10 requests and a concurrency level of 2 verified that all observations were retained and aggregate latency statistics could be calculated successfully. The execution output was preserved as evidence in [`LO5-RV01-verification.txt`](LO5-RV01-verification.txt).

RV-02 remains a measurement limitation because PowerShell `Start-Job` introduces process creation and scheduling overhead. RV-03 remains a proposed maintainability improvement; the unused state could be removed and the existing regression suite rerun to check for unintended behavioural changes.

These findings demonstrate that code inspection can identify defects and maintainability concerns beyond those detected by automated tests. RV-01 does not affect the independently executed R7 sequential performance experiment, and neither RV-02 nor RV-03 establishes a functional production defect.

### 1.3 Follow-up Inspection: Trade Execution and Build (R1, R2, R5)

LO4 identified concurrency and trade-history behaviour as areas that testing cannot exhaust. A second checklist inspection therefore targeted `TradeService.executeTrade`, the two other accounting implementations, `GlobalExceptionHandler` and `pom.xml`.

| Checklist question | Applied to |
|---|---|
| C1 Is each business rule implemented in exactly one place? | Cash/position calculation |
| C2 Is the work done while holding the pessimistic lock bounded? | `executeTrade` critical section |
| C3 Is every validation reachable, and consistent with the API layer? | Service checks vs Bean Validation |
| C4 Are failures mapped to the correct HTTP status class (client vs server)? | `GlobalExceptionHandler` |
| C5 Is the build reproducible, with test-only code excluded from the artefact? | `pom.xml`, packaged JAR |
| C6 Does instrumentation count what it claims to count? | `TradeMetrics` |

| ID | Checklist | Finding                                                                                                                                                                                                                                                                                                                                                                                                                                                                          | Severity and status |
|---|---|----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|---|
| RV-04 | C1 | Cash and position accounting is implemented three times: the `TradeService` history loop (lines 226–263), `PortfolioCalculationService` and `PortfolioValuationCalculator`. Tests of one copy give no assurance about the others; the SELL-history gap found in the LO4 follow-up existed only in the `TradeService` copy.                                                                                                                                                       | Medium (correctness/maintainability). Proposal: a single ledger component used by all three. Open. |
| RV-05 | C2 | `executeTrade` loads the portfolio's **entire** trade history while holding the `PESSIMISTIC_WRITE` row lock (lines 162–263). Work inside the critical section grows linearly with history, so trade latency and same-portfolio throughput degrade over time. R7 measures valuation only (100 trades), so this attribute is untested.                                                                                                                                            | Medium (performance). Proposal: running balance/position projection, plus a trade-latency-versus-history benchmark. Open. |
| RV-06 | C5 | An earlier `pom.xml` declared `org.testng:testng` at dynamic version `RELEASE` in compile scope. Maven dependency resolution failed in [CI run 44336798829](https://github.com/without-paper/mini-aladdin/actions/runs/44336798829); an earlier packaged JAR also contained the unused TestNG library, which triggered a PIT classpath warning. The dependency was removed, and [CI run 44342089420](https://github.com/without-paper/mini-aladdin/actions/runs/44342089420) passed.                                                                                                                                           | Low–medium (build integrity). Fixed and verified: unused dependency removed; Maven verification and Docker image build passed. |
| RV-07 | C3, C4 | (a) Thirteen `TradeService` validation branches duplicate Bean Validation and cannot be reached through the API (see [LO4 follow-up](LO4-follow-up.md)). (b) `GlobalExceptionHandler` maps every `IllegalArgumentException` to HTTP 400, but `PortfolioValuationCalculator` and `PortfolioCalculationService` throw it for corrupt **persisted** data (for example, negative initial cash). A server-side data fault would therefore be reported to the client as a bad request. | Low. Proposal: a dedicated invalid-request exception for client errors; keep the service checks as defence in depth, tested at unit level. Open. |

**Earlier inspection (C6).** The pre-coursework [engineering review](../ENGINEERING_REVIEW.md) found that `TradeService` recorded the *replayed* metric on every new trade and never on an actual replay. The defect was in the instrumentation itself, not detected by any test, and fixed in commit `b66b0f5`. It motivated the LO2 check that compares counter increments with the persisted database state.

**Evaluation.** The follow-up inspection identified issues not exposed by the existing functional tests: duplicated rules (RV-04), unbounded work under a lock (RV-05), a non-reproducible dependency (RV-06) and server-side data faults reported as client errors (RV-07). RV-06 was subsequently fixed; RV-04, RV-05 and RV-07 remain open. Each finding is linked to a requirement and a proposed verification or design improvement.

## 2. CI Pipeline Design (LO5.2)

The existing GitHub Actions workflow (`.github/workflows/ci.yml`) runs on pushes to `master` and `feature/**`, pull requests targeting `master`, and manual dispatch.

Its first job checks out the source, configures Temurin Java 21, and executes `./mvnw -B -ntp clean verify`. This runs the automated Java test suite, including PostgreSQL Testcontainers integration tests.

The second job depends on Maven verification, builds and loads the Docker image, and verifies that it is present in the runner. It starts a fresh PostgreSQL 17 database and the packaged application using an isolated Docker Compose project. The HTTP smoke script then checks health, portfolio creation, BUY execution and idempotent replay, persisted trade count and financial reconciliation. A recovery script then stops and restarts PostgreSQL underneath the running application (§4.2). Failure of either script blocks the candidate CI run; logs are captured on failure and the temporary containers are always cleaned up. This is a pre-release gate, not an automated production deployment.

The workflow uses Maven dependency caching, read-only repository permissions, concurrency cancellation and test-report artifact upload.

## 3. Automated Testing (LO5.3)

Automated checks include JUnit unit tests, parameterised boundary and combinatorial tests, MockMvc API tests, PostgreSQL integration tests, regression tests and JaCoCo coverage collection.

The current 137-test suite provides automated regression feedback across the selected requirements, expanded from the original 69-test baseline through specification-based testing, regression testing and three LO4 follow-up stages; the third removed eight repeated tests and added partly valid, boundary and business-flow tests ([LO4 follow-up §8](LO4-follow-up.md#8-third-follow-up-requirement-driven-test-review-137-tests)). JaCoCo reports execution coverage during Maven verification. Separately, the automated deployment smoke test checks customer-critical operations through the HTTP interface of the built container, rather than testing only inside the JVM, and the recovery check exercises database failure and recovery against the same container.

PIT mutation testing and the R7 performance benchmark are separate experiments; they are not currently mandatory CI jobs.

## 4. CI Demonstration (LO5.4)

Actual GitHub Actions executions demonstrate both failure detection and successful verification.

**Failed CI run:** [43997022868](https://github.com/without-paper/mini-aladdin/actions/runs/43997022868).

The Maven job reported 97 tests, including two failures caused by the fractional-cent valuation reconciliation defect (D-02). The dependent Docker image build was skipped.

**Successful CI run:** [44002109303](https://github.com/without-paper/mini-aladdin/actions/runs/44002109303).

The Maven job reported 97 passing tests with zero failures and errors. JaCoCo generated its report, and the dependent Docker image build completed successfully.

These executions demonstrate that the pipeline detects test regressions and gates image construction on automated verification.

The workflow does not perform production deployment, dedicated security testing (scanning or penetration testing), performance acceptance testing or mandatory mutation testing. Its only security-related check is the limited R6 information-disclosure check in the recovery step (§4.2).

### 4.1 Post-build Smoke Gate and Controlled Recovery

An initial integration attempt failed because Buildx had produced the Docker image without loading it into the runner ([failed CI run](https://github.com/without-paper/mini-aladdin/actions/runs/44584149919)). Moving `load: true` to the build-push action fixed this pipeline integration error. The [successful smoke CI run](https://github.com/without-paper/mini-aladdin/actions/runs/44586139842) passed all 121 Java tests and the end-to-end checks. A [later successful run](https://github.com/without-paper/mini-aladdin/actions/runs/44589827668) confirmed the gate remained operational. [CI run #41](https://github.com/without-paper/mini-aladdin/actions/runs/45089342461) on commit `4ab1008` was the first to verify the 135-test suite, and [CI run #42](https://github.com/without-paper/mini-aladdin/actions/runs/45209278587) on `8b29ea8`, the last 135-test revision, repeated it. Both completed Maven verification, the Docker image build and the isolated smoke test. [CI run #44](https://github.com/without-paper/mini-aladdin/actions/runs/45266517282) on `910bffd` verified the 137-test suite and was the first run of the recovery check (§4.2); [CI runs #45](https://github.com/without-paper/mini-aladdin/actions/runs/45268324572) and [#46](https://github.com/without-paper/mini-aladdin/actions/runs/45312301206), on the documentation-only commits `da65df9` and `8258fdd`, repeated both. Later commits change documentation only.

The deployed-container smoke check exercises the real HTTP endpoint and a fresh database, which a passing unit suite or image build alone does not establish. A failed candidate CI run does not stop an existing deployment because this workflow performs no production deployment.

A separate [controlled database recovery experiment](LO5-recovery-experiment.md) used an isolated Docker Compose project. The operator reported that health detection and critical business operations recovered after stopping and restarting PostgreSQL. Raw terminal responses and recovery logs have not been committed, so that evidence is weaker than the independently inspectable CI logs; §4.2 replaces it with an automated check. Neither demonstrates zero-downtime availability, automated rollback or resilience under production traffic.

### 4.2 Automated Database Failure and Recovery Check

The Docker job's step *Database failure and recovery check* runs [`scripts/ci-recovery.sh`](../../scripts/ci-recovery.sh) after the smoke test. It stops the PostgreSQL container under the running application and requires: health 503; a trade request during the outage answered with a 5xx Problem Details body with the generic detail and no exception, package, SQL or driver text; health back to 200 after the restart without restarting the application; an empty trade history; and a retry with the same Idempotency-Key executed exactly once (201, then a 200 replay with the same ID). It covers R2, R5 and R6 under a real fault and runs on every push, so it is repeatable evidence rather than an operator report. It passed in [CI run #44](https://github.com/without-paper/mini-aladdin/actions/runs/45266517282) and in every later run ([#45](https://github.com/without-paper/mini-aladdin/actions/runs/45268324572), [#46](https://github.com/without-paper/mini-aladdin/actions/runs/45312301206)). It injects one fault (a clean database stop and restart) at one point; network partitions, slow or intermittent responses and a failure during commit are not tested, and the application has no retry, back-off or failover of its own.

## 5. Limitations and Follow-up

The code inspection was limited to selected components and does not constitute a comprehensive security, performance or financial audit.

RV-01 was corrected and verified through a targeted execution. However, this verification establishes only that the benchmark retains the expected number of measurements and calculates aggregate statistics for the tested configuration. It does not establish accurate server-side performance measurement under arbitrary concurrency levels.

RV-02 remains an inherent limitation of the current PowerShell benchmarking approach, while RV-03 is an identified maintainability improvement that has not yet been implemented. RV-04, RV-05 and RV-07 remain open design proposals.

The CI pipeline prevents Docker image construction when Maven tests fail and fails candidate validation when the deployment smoke or recovery check fails. These gates cannot guarantee the absence of undetected defects or ongoing production availability. Mutation testing, performance acceptance testing and dedicated security testing are not mandatory CI stages; the R6 information-disclosure check covers only one server-error response.

Further improvements should prioritize validating client/server correlation-ID timing, measuring trade latency as history grows (RV-05), and widening the recovery check to other faults (network loss, slow responses, failure during commit). These risk-based improvements are more useful than maximizing aggregate coverage percentages.

**References:** Software Testing Tutorial LO5; Young and Pezzè Chapters 18, 23 and 24; project source code and GitHub Actions execution history.