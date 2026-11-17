# LO2 — Test and Analysis Plan

## 1. Priority and Pre-requisites

This plan develops the requirements defined in [LO1](LO1-requirements.md). The initial [baseline](baseline-test-run.txt) contains 69 passing tests, with no failures, errors or skipped tests. Existing tests will be retained and extended where necessary.

**Priorities.** R1, R2, R3 and R5 are P0 requirements because incorrect trades, financial calculations or transaction behaviour compromise core system correctness. R4, R6, R7 and R8 are P1 requirements supporting trustworthy valuation, API usability, performance and maintenance. Diagnostic facilities will be assessed early because they support testing of the P0 requirements.

The following two requirements receive detailed planning.

| Requirement | Analysis and testing activities | Pre-requisites and acceptance criteria |
|---|---|---|
| R2: Idempotency | Inspect the key, fingerprint, locking and persistence design; develop category-partition and state-based tests; test concurrent requests and HTTP responses. | Repository mocks, PostgreSQL Testcontainers, independent portfolio fixtures and controlled concurrent execution. A new request creates one trade; a matching replay returns the original trade without additional effects; conflicting reuse is rejected. |
| R7: Valuation performance | Prepare a synthetic workload, execute repeated end-to-end measurements and analyse latency distributions. | A running application, 100 historical trades, 10 open positions, fresh quotes and one sequential client. Three runs of 100 measured requests after 20 warm-up requests per run; all measured requests succeed and each run has P95 ≤ 200 ms. |

R2 requires complementary component, integration and API evidence because mocked tests cannot verify database locking and transaction behaviour. R7 requires an integrated system and a reproducible operational envelope. Its proposed performance target is intended for local interactive use, rather than a production SLA.

## 2. Scaffolding and Instrumentation

**Existing scaffolding.** Mockito repository mocks support isolated service testing. `TestDatabaseConfiguration` supplies a PostgreSQL 17 Testcontainer for integration testing. MockMvc supports API contract tests. `CountDownLatch` and `ExecutorService` coordinate concurrent requests, while `TransactionTemplate` supports rollback and isolation scenarios. These facilities are already exercised by the baseline suite.

**Existing instrumentation.** `CorrelationIdFilter` and MDC logging expose request context. `TradeMetrics` distinguishes executed, rejected and replayed trades. `TransactionalTradeMetrics` records new executions after transaction commit. `TransactionalTradeMetricsIntegrationTest` verifies that rolled-back transactions do not increment the executed counter. `PortfolioValuationService` checks accounting invariants.

For R2, the concurrency test was extended to compare executed and replayed counter increments with the actual persisted trade. The extended test passed and remains part of the 97-test regression suite; see [instrumentation evidence](LO2-instrumentation-test-run.txt). For R7, the existing benchmark will be extended to retain individual request latencies, response outcomes, workload details and environment information.

**Evaluation.** PostgreSQL scaffolding provides stronger evidence of actual transaction behaviour than repository mocks. However, a finite collection of concurrent schedules cannot establish correctness under all interleavings. Aggregate metrics support diagnosis but cannot independently establish the correctness of individual transactions. Database state and API results remain necessary oracles. Shared counters also require controlled test execution to avoid interference. Performance measurements must identify their workload and environment; aggregate latency values alone provide insufficient evidence of general performance.

## 3. Process and Risk

The coursework adopts the DevOps lifecycle described in Young and Pezzè Chapter 20. Requirement analysis and test design inform implementation; automated verification precedes Docker packaging; local execution and monitoring provide feedback for further planning.

| Stage | Planned activities | Dependencies and evidence |
|---|---|---|
| Plan | Review requirements, prioritise risks and derive test cases. | LO1 requirements and baseline; test specifications. |
| Create | Extend scaffolding and instrumentation; develop boundary, partition and state-based tests. | Defined test oracles; test code and commit history. |
| Verify | Execute unit, PostgreSQL integration and API tests; measure coverage and investigate failures. | Test environment; Maven reports and defect records. |
| Package / configure | Build the Docker image and prepare the controlled valuation workload. | Passing regression tests; container and workload configuration. |
| Monitor | Execute performance measurements and inspect diagnostic data. | Running application; raw latency data and metric observations. |
| Review and refine | Compare results with targets, review code and revise the plan. | Coverage, performance and CI evidence; documented limitations and actions. |

Changes addressing discovered defects will use test-first regression development where practicable. Existing tests will be rerun after changes, and the plan will be updated when new requirements or defects emerge.

**Plan evaluation.** Important risks include incomplete concurrent schedule coverage, ambiguous financial rounding rules, unrepresentative performance data, shared metric interference and delays caused by database or CI infrastructure. These will be mitigated through independent calculation oracles, controlled fixtures, multiple testing levels, early infrastructure checks and documented experiment conditions.

The plan prioritises core correctness and reuses established testing infrastructure to limit effort. Remaining limitations include incomplete coverage of concurrent behaviour and the absence of a real production workload. Therefore, the resulting assurance will be restricted to the selected requirements and tested conditions.

Progress will be monitored using requirement IDs, completed test tasks, Git commits, test execution reports and defect records. Any planned activity that cannot be completed will be identified in the final evaluation.

## 4. Plan Execution Status (17 November 2026)

The R2 instrumentation and concurrency checks were completed. Specification-based boundary tests, category-partition tests and an idempotency state-machine sequence were added during LO3-A–C. A fractional-cent valuation reconciliation defect (D-02) was reproduced at component and API levels, corrected through an explicit cost-allocation policy and verified by a 97-test passing regression suite in LO3-D.

LO3-E established a JaCoCo baseline of **574/615 covered lines (93.3%)** and **123/180 covered branches (68.3%)**. The selected `PortfolioValuationCalculator` mutation experiment killed **19/23 mutants (83% PIT-reported mutation coverage)**, with four surviving conditional-boundary mutants requiring further interpretation. See [LO3 results](LO3-test-results.md) and [LO3 test design](LO3-test-design.md).

The R7 performance experiment was completed using 100 historical trades, 10 open positions and three runs of 100 sequential measurements. All 300 requests succeeded, and the measured P95 values were 57.762 ms, 52.976 ms and 52.416 ms, satisfying the proposed 200 ms threshold. One 2064.904 ms outlier was observed in Run 3 and remains unexplained. 

## References

- Software Testing, [Tutorial LO2 and Sample Test Planning Document](https://opencourse.inf.ed.ac.uk/st/week-4-5).
- Young and Pezzè, Chapters 3–4, Chapter 17 (Scaffolding), Chapter 20 (Planning and Monitoring), and Chapter 21 (Integration).