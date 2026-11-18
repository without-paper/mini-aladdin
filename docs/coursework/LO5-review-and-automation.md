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

| ID | Checklist | Finding | Severity and status |
|---|---|---|---|
| RV-04 | C1 | Cash and position accounting is implemented three times: the `TradeService` history loop (lines 226–263), `PortfolioCalculationService` and `PortfolioValuationCalculator`. Tests of one copy give no assurance about the others; the SELL-history gap found in the LO4 follow-up existed only in the `TradeService` copy. | Medium (correctness/maintainability). Proposal: a single ledger component used by all three. Open. |
| RV-05 | C2 | `executeTrade` loads the portfolio's **entire** trade history while holding the `PESSIMISTIC_WRITE` row lock (lines 162–263). Work inside the critical section grows linearly with history, so trade latency and same-portfolio throughput degrade over time. R7 measures valuation only (100 trades), so this attribute is untested. | Medium (performance). Proposal: running balance/position projection, plus a trade-latency-versus-history benchmark. Open. |
| RV-06 | C5 | `pom.xml` declares `org.testng:testng` with version `RELEASE` in **compile** scope. Maven warns that `RELEASE` is deprecated, resolution is not reproducible, and `testng-7.12.0.jar` is packaged in `BOOT-INF/lib` of the application JAR. No source file uses TestNG; it also causes the PIT "TestNG is on the classpath" warning. | Low–medium (build integrity). Fix: remove the dependency and rerun `clean verify`. Fixed and verified. |
| RV-07 | C3, C4 | (a) Thirteen `TradeService` validation branches duplicate Bean Validation and cannot be reached through the API (see [LO4 follow-up](LO4-follow-up.md)). (b) `GlobalExceptionHandler` maps every `IllegalArgumentException` to HTTP 400, but `PortfolioValuationCalculator` and `PortfolioCalculationService` throw it for corrupt **persisted** data (for example, negative initial cash). A server-side data fault would therefore be reported to the client as a bad request. | Low. Proposal: a dedicated invalid-request exception for client errors; keep the service checks as defence in depth, tested at unit level. Open. |

**Earlier inspection (C6).** The pre-coursework [engineering review](../ENGINEERING_REVIEW.md) found that `TradeService` recorded the *replayed* metric on every new trade and never on an actual replay. The defect was in the instrumentation itself, not detected by any test, and fixed in commit `b66b0f5`. It motivated the LO2 check that compares counter increments with the persisted database state.

**Evaluation.** The follow-up inspection found issues of a kind that additional test cases would not reveal: duplicated rules (RV-04), unbounded work under a lock (RV-05) and build integrity (RV-06). Each finding is linked to a requirement and proposes either a test (RV-05) or a design change.

## 2. CI Pipeline Design (LO5.2)

The existing GitHub Actions workflow (`.github/workflows/ci.yml`) runs on pushes to `master` and `feature/**`, pull requests targeting `master`, and manual dispatch.

Its first job checks out the source, configures Temurin Java 21, and executes `./mvnw -B -ntp clean verify`. This runs the automated Java test suite, including PostgreSQL Testcontainers integration tests.

The second job builds the Docker image and depends on successful completion of the test job. This ordering prevents image construction when tests fail.

The workflow uses Maven dependency caching, read-only repository permissions, concurrency cancellation and test-report artifact upload.

## 3. Automated Testing (LO5.3)

Automated checks include JUnit unit tests, parameterised boundary and combinatorial tests, MockMvc API tests, PostgreSQL integration tests, regression tests and JaCoCo coverage collection.

The 97-test suite provides automated regression feedback across the selected requirements. JaCoCo reports execution coverage during Maven verification.

PIT mutation testing and the R7 performance benchmark are separate experiments; they are not currently mandatory CI jobs.

## 4. CI Demonstration (LO5.4)

Actual GitHub Actions executions demonstrate both failure detection and successful verification.

**Failed CI run:** [43997022868](https://github.com/without-paper/mini-aladdin/actions/runs/43997022868).

The Maven job reported 97 tests, including two failures caused by the fractional-cent valuation reconciliation defect (D-02). The dependent Docker image build was skipped.

**Successful CI run:** [44002109303](https://github.com/without-paper/mini-aladdin/actions/runs/44002109303).

The Maven job reported 97 passing tests with zero failures and errors. JaCoCo generated its report, and the dependent Docker image build completed successfully.

These executions demonstrate that the pipeline detects test regressions and gates image construction on automated verification.

The workflow does not currently perform production deployment, security testing, performance acceptance testing or mandatory mutation testing.

## 5. Limitations and Follow-up

The code inspection was limited to selected components and does not constitute a comprehensive security, performance or financial audit.

RV-01 was corrected and verified through a targeted execution. However, this verification establishes only that the benchmark retains the expected number of measurements and calculates aggregate statistics for the tested configuration. It does not establish accurate server-side performance measurement under arbitrary concurrency levels.

RV-02 remains an inherent limitation of the current PowerShell benchmarking approach, while RV-03 is an identified maintainability improvement that has not yet been implemented.

The CI pipeline provides automated regression feedback and prevents Docker image construction when Maven tests fail. However, it does not guarantee the absence of undetected defects. Mutation testing, performance acceptance testing and security testing are not currently mandatory CI stages.

Further improvements could include targeted static analysis, stronger review checklists, preservation of JaCoCo reports as CI artifacts and additional tests addressing uncovered branches and surviving mutants.

**References:** Software Testing Tutorial LO5; Young and Pezzè Chapters 18, 23 and 24; project source code and GitHub Actions execution history.