# LO4 — Evaluation of Testing Limitations

> **Status:** written at the 97-test LO3-E stage (17 November 2026). Its proposals were acted on in the [LO4 follow-up](LO4-follow-up.md) (121 tests), the later test-code review (135 tests) and the [requirement-driven review](LO4-follow-up.md#8-third-follow-up-requirement-driven-test-review-137-tests) (137 tests). The figures below are kept unchanged as the record of that stage.

## 1. Evaluation Scope

This evaluation compares the requirements in [LO1](LO1-requirements.md), the plan in [LO2](LO2-test-plan.md), and the executed tests and measurements in [LO3](LO3-test-results.md). Two requirements receive detailed attention: R3 (financial valuation correctness) and R7 (valuation response time). These illustrate different limitations of functional and measurable-attribute testing. Other significant gaps are assessed using JaCoCo and PIT evidence.

## 2. LO4.1 — Gaps and Omissions

| Area | Evidence and remaining limitation | Potential remedy |
|---|---|---|
| R3: Financial correctness | A fractional-cent partial-sale history revealed one genuine reconciliation defect, despite the original passing tests. The regression now verifies that history, but other trade sequences, multiple symbols, long histories and alternative rounding conventions remain insufficiently explored. | Generate valid financial histories and check independent cash-flow and P&L invariants, including partial and complete sales. |
| Structural adequacy | JaCoCo reports 574/615 covered lines (93.3%) but only 123/180 covered branches (68.3%). `TradeService` has 34/52 covered branches (65.4%) and `PortfolioValuationCalculator` has 25/34 (73.5%). | Inspect uncovered branches and add risk-prioritised error and boundary cases with explicit oracles. |
| Fault detection | PIT killed 19/23 mutants (82.6%) in `PortfolioValuationCalculator`; four conditional-boundary mutants survived. Their source locations and equivalence status have not been assessed. | Inspect the four mutations individually, distinguish equivalent changes from missing boundary tests, and rerun the same PIT scope. |
| R2/R5: Concurrency | PostgreSQL integration tests check selected locking, replay and rollback scenarios. They do not exhaust possible thread interleavings or database contention levels. | Repeat controlled concurrent scenarios with varied schedules, clients and workload sizes. |
| R7: Response time | Three sequential runs satisfy P95 <= 200 ms, but all measurements come from one local environment and a single benchmark session. One measured request took 2064.904 ms. No concurrent-load, cross-machine or long-duration evaluation has been performed. | Repeat independent sessions, record system resource/GC activity and test controlled concurrent demand. |

Authentication, real brokerage connectivity and live-market-feed validation were expressly excluded from the prototype's test scope; they should not be presented as failed coverage commitments.

## 3. LO4.2 — Targets and Their Rationale

The original requirements and LO3 test specifications define the following *pre-existing* targets. Their denominators refer to explicitly defined models.

| Procedure | Target set before execution | Basis |
|---|---|---|
| R1/R4 functional partitions | 8/8 identified partitions exercised; associated tests pass | Covers specified valid/invalid input classes |
| R1/R4 boundary tests | 12/12 selected boundary cases exercised; associated tests pass | Focuses on threshold and adjacent values |
| R2 combination tests | 12/12 feasible normal-domain combinations exercised | Small model permits exhaustive combinations |
| R2 state model | 2/2 states and 4/4 transitions exercised | Covers every transition in the defined abstraction |
| R7 system performance | 100/100 measured HTTP 200 responses per run, and P95 <= 200 ms in each of three runs | LO1/LO2 workload-specific acceptance criterion; 20 warm-ups per run |

No numerical project-wide branch-coverage threshold or PIT mutation target was set before the JaCoCo/PIT experiment. Their measured values are therefore **baselines**, not passes against retrospective acceptance criteria. For subsequent improvement, provisional risk-based goals could be >=80% branch coverage for `TradeService` and `PortfolioValuationCalculator`, and >=90% mutation coverage in the same calculator/PIT scope, subject to investigation of equivalent mutants and test cost. These are proposed future goals, not previously agreed requirements.

## 4. LO4.3 — Achieved Results Compared with Targets

All 97 regression tests passed after the R3 fix. The functional partition, boundary, combination and state-model targets were achieved within their stated abstractions. The original financial test suite missed a real defect: for BUY 1 at $0.01, BUY 1 at $0.02 and SELL 1 at $0.02, independently rounded P&L components did not reconcile, and the valuation API returned HTTP 500. A cost-allocation rounding rule was specified and implemented; the two targeted regression tests and the full 97-test suite then passed. This supports the corrected case without proving every trading history.

For R7, 300/300 measured requests returned HTTP 200. Per-run P95 values were **57.762 ms**, **52.976 ms** and **52.416 ms**, all below the original 200 ms limit. The largest observation was **2064.904 ms** (Run 3, Iteration 27), making the third run's mean **69.423 ms** even though its P95 was only **52.416 ms**. Exactly 1/300 measured requests (0.33%) exceeded 200 ms. This is a descriptive observation, **not** an estimate of production incident probability: measurements were sequential, closely spaced and performed in one local session. In particular, passing a P95 threshold does not impose a maximum-latency bound.

JaCoCo's 93.3% line coverage and 68.3% branch coverage demonstrate that high execution coverage can coexist with untested conditional outcomes. PIT's 19/23 killed mutants (82.6%) provide complementary evidence of fault detection in one calculator class; the four survivors remain unresolved. The PIT figure cannot be generalised to the whole application. The testing process confirmed and fixed **one distinct production defect** (D-02) and corrected **one test-suite weakness** (D-01). A percentage defect yield is not reported because no consistent opportunity denominator was established.

## 5. LO4.4 — Improvements, Costs and Priorities

**Highest priority: Financial and trading invariants.** Add carefully chosen long and multi-symbol trading histories, partial-sale thresholds and assertions for cash, position cost basis and P&L reconciliation. Prioritise `TradeService` branches related to rejection and persistence. These cases directly reduce risk to R1/R3 and can reuse existing unit and PostgreSQL fixtures.

**Next: Mutation and branch gaps.** Inspect the four surviving boundary mutants at source-line level. Create tests only where a mutant represents observably different required behaviour, then compare coverage/mutation scores using identical configurations. Equivalent mutants should be documented rather than counted as confirmed product faults.

**Next: Performance representativeness.** Repeat R7 in several independent sessions, log JVM/host resource activity around slow requests and introduce controlled concurrency. Preserve raw latency distributions and compare per-run tails, not just the means. This requires additional execution time and environment control; the existing sequential R7 acceptance result should remain separately reported.

**Ongoing: Process and reproducibility.** Preserve requirement-to-test links, pre-fix and post-fix regression evidence, CI logs and versioned benchmark metadata. The environment record includes OS, CPU, memory and a repository commit, but does not establish the exact running Docker image digest or application JVM version. Adding these would improve reproducibility.

## 6. Overall Assessment

The testing process provides substantial evidence for the specified prototype behaviours: 97 passing regression tests, explicitly covered finite input/state models, a reproduced-and-fixed financial defect, measured structural/mutation adequacy and a passing local R7 experiment. Confidence is strongest for the scenarios with independent oracles and real PostgreSQL integration. Confidence is weaker for untested financial histories, uncommon branches, concurrent schedules and response-time tails outside the controlled local workload. The remaining gaps are identified and prioritised; a complete absence of defects cannot be inferred from these results.

The main limitations differ between R3 and R7. Financial correctness depends on the completeness of selected trading histories and the strength of independent test oracles, whereas performance evaluation depends heavily on workload representativeness and environmental variation. Regression checks, coverage measurements, mutation analysis and latency statistics can be automated. Selecting meaningful financial scenarios, interpreting equivalent mutants and explaining performance outliers still require human judgement.

## Evidence

- [LO1 requirements](LO1-requirements.md)
- [LO2 test plan](LO2-test-plan.md)
- [LO3 test design](LO3-test-design.md)
- [LO3 results and defect record](LO3-test-results.md)
- [JaCoCo class counters](LO3-E-jacoco.csv)
- [PIT mutation log](LO3-E-pitest-run.txt)
- [R7 raw client latencies](r7-F1F66185-raw.csv)
- [R7 summary](r7-F1F66185-summary.csv)
- [R7 environment](r7-F1F66185-environment.json)
- [R7 execution log](LO3-F-r7-run.txt)
- [Software Testing LO4 Tutorial](https://opencourse.inf.ed.ac.uk/st/week-8-9)
