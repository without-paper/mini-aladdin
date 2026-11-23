# LO4 Follow-up — Acting on Coverage, Mutation and Performance Evidence

[LO4 evaluation](LO4-evaluation.md) identified uncovered branches, four unexplained PIT survivors and an unexplained R7 latency outlier, but left them as proposals. This document records the follow-up: the missed branches are classified (LO4.3), targets are set for the P0 classes (LO4.2), new tests close the genuine gaps (LO4.4), and the R7 data is analysed statistically.

## 1. Classification of the 57 missed branches (JaCoCo, 97-test suite)

Source: `target/site/jacoco/jacoco.xml` from the 97-test run ([summary CSV](LO3-E-jacoco.csv)). The P0 classes were classified line by line.

| Class | Missed | Genuinely untested behaviour | Defensive duplicate of API validation / DB constraints | Impossible |
|---|---:|---|---|---|
| `TradeService` | 18 | **3**: history containing a SELL (234, 238, 250) | **13**: null key (95), null request (103), null side (119), quantity checks (125–128), price checks (135–138), null/invalid symbol (365, 374) — all already rejected by Bean Validation or the controller | **2**: side neither BUY nor SELL (238, 288) |
| `PortfolioValuationCalculator` | 9 | **4**: boundaries never reached — initial cash (42), zero quantity/price in history (60 ×2), cash exactly zero (115) | **5**: null/blank persisted fields (57), guarded by `NOT NULL` columns | 0 |
| `PortfolioValuationService` | 3 | **3**: each accounting-invariant failure (117, 127, 136) | 0 | 0 |

Line-level gaps with no branch: `TradeService` line 165 (trade on a missing portfolio) and `GlobalExceptionHandler.handleUnexpected` lines 211–216 (the HTTP 500 contract required by R6). The remaining missed branches (`PortfolioCalculationService`, `GlobalExceptionHandler.createResponseEntity`, `TransactionalTradeMetrics`, `MarketPriceService`, `PortfolioService`, `CorrelationIdFilter`) are mainly defensive or framework paths and were not analysed in detail.

**Key finding.** The most important gap is in R1, a P0 requirement: no test executed a trade whose history contained a SELL. Lines 240 (cash credited by a sale) and 258 (holding reduced by a sale) never ran, so replacing `add` with `subtract` on line 240 would have passed all 97 tests. FT-01–FT-06 partitioned the current cash and holding, but not the *composition of the history*, so the category was missing from the functional model.

## 2. Targets for the P0 classes (set at the LO4 stage, after the baseline)

| Procedure | Target | Rationale |
|---|---|---|
| Branch coverage, P0 classes | Every *genuinely untested* branch covered (100% of the category above); remaining branches classified | A flat 80% would reward testing unreachable defensive code and still allow the SELL-history gap; the classification makes the residue explicit |
| Mutation, `PortfolioValuationCalculator` | ≥ 90% killed and every survivor classified (equivalent or test gap) | Financial calculation is P0; boundary errors cost money |
| R7 tail | Report P95 with a confidence interval and bound the >200 ms exceedance rate | A passing P95 says nothing about the tail (one 2,064.9 ms request) |

## 3. Actions: new tests

| IDs | Test class | Gap closed | Requirement |
|---|---|---|---|
| MB-01 – MB-03 (10 cases) | `PortfolioValuationCalculatorTest` | Boundaries at calculator lines 42, 60, 115 (the four PIT survivors) | R3 |
| FT-13 – FT-20 + missing portfolio (9 cases) | `TradeServiceHistoryTest` | SELL in history (lines 234–258); missing portfolio (165) | R1 |
| IV-1 – IV-4 (4 cases) | `PortfolioValuationServiceInvariantTest` | Each accounting-invariant check fires (117–139) | R3 (instrumentation) |
| 1 case | `UnexpectedErrorProblemDetailIntegrationTest` | HTTP 500 Problem Details, generic message, correlation ID (handler 211–216) | R6, R8 |

All new cases use one-cent / one-share boundaries, as FT-01–FT-12 did. FT-13–FT-15 sell at a different price from the purchase price, so crediting the cost basis instead of the proceeds would also be detected. FT-19–FT-20 include another symbol's trades so that ignoring the symbol filter changes the outcome. The calculator tests are in the existing calculator test class, so the PIT configuration is unchanged and the before/after mutation scores are comparable.

## 4. R7 statistical analysis

Reproduce with `python performance/r7_statistics.py docs/coursework/r7-F1F66185-raw.csv` ([output](LO4-R7-statistics.txt)).

| Group | n | P95 (ms) | 95% bootstrap CI for P95 | >200 ms | 95% upper bound (Clopper–Pearson) |
|---|---:|---:|---|---:|---:|
| Run 1 | 100 | 57.76 | 55.47 – 59.43 | 0 | 2.95% |
| Run 2 | 100 | 52.98 | 52.65 – 55.19 | 0 | 2.95% |
| Run 3 | 100 | 52.42 | 51.39 – 52.70 | 1 | 4.66% |
| Pooled | 300 | 55.47 | 54.32 – 56.72 | 1 | 1.57% |

* Every interval is far below 200 ms, so the R7 conclusion is not sensitive to sampling error.
* The Run 1 and Run 3 intervals do not overlap: variation **between** runs is larger than sampling error **within** a run. This quantifies why independent sessions are needed before generalising.
* Within this envelope, the probability that a request exceeds 200 ms is below about 1.6% (one-sided 95%). Consecutive sequential requests are not fully independent, so this is an indicative bound only.
* The outlier (Run 3, iteration 27) is about 2,015 ms above a typical request. Its roughly two-second duration alone cannot establish whether client/network or server-side work caused it. The updated [`benchmark-valuation-r7.ps1`](../../performance/benchmark-valuation-r7.ps1) records `X-Correlation-ID` on successful measurements (or null if a request fails before a response is available) so that future client timings can be matched to the server-side `durationMs` logs. A subsequent five-request validation confirmed successful correlation-ID capture, but client and server durations have not yet been matched. The historical 2,064.904 ms outlier remains unexplained.

## 5. Results after the actions

Evidence: [121-test `mvnw clean verify` run](LO4-follow-up-test-run.txt#L4995) (`BUILD SUCCESS`; JaCoCo from the same run) and the [PIT report](pit-reports/index.html) ([`mutations.xml`](pit-reports/mutations.xml)), produced with the unchanged PIT configuration.

| Measure | Before | After | Target (§2) met? |
|---|---|---|---|
| Tests (passed / total) | 97 / 97 | **121 / 121** | — |
| JaCoCo lines | 574/615 (93.3%) | **587/615 (95.4%)** | — |
| JaCoCo branches | 123/180 (68.3%) | **133/180 (73.9%)** | — (no flat target; see §2) |
| `TradeService` branches | 34/52 | **37/52**; the remaining 15 are 13 defensive duplicates (lines 95, 103, 119, 125–128, 135–138, 365, 374) and 2 impossible cases (238, 288) | **Yes**: no genuinely untested branch remains |
| `PortfolioValuationCalculator` branches | 25/34 | **29/34**; the remaining 5 are defensive persisted-data checks (line 57) | **Yes** |
| `PortfolioValuationService` branches | 3/6 | **6/6** | **Yes** |
| PIT, calculator | 19/23 (82.6%) | **23/23 (100%)** | **Yes** (≥ 90%, no survivor left to classify) |

**Which tests killed the former survivors** (from `mutations.xml`): line 42, MB-01 (initial cash 0.00); line 60, MB-03 (historical quantity 0, and historical price 0.00); line 115, MB-02 (initial cash exactly 2000.00, so cash ends at 0.00). Line 78 was already killed by `shouldPreserveRealisedPnlAfterPositionClosed`. Each survivor fell to a single boundary case, which confirms that all four were missing tests, not equivalent mutants.

**Conclusion.** The P0 branch and mutation targets set in §2 are now met, and every uncovered branch in the P0 classes is classified. The remaining R7 work includes testing the new correlation-ID capture against actual server logs, repeating independent sessions and introducing controlled concurrent load (LO4 §5).

## 6. Second follow-up: test-code review

A review of the test code itself, rather than of its coverage numbers, found gaps that coverage and mutation scores could not show: some tests could pass without exercising the behaviour they were named after, and some assertions were too weak to detect a wrong result.

| # | Gap found | Change | Requirement |
|---|---|---|---|
| G1 | The concurrency test started two threads from a latch, but nothing forced them to overlap; one request could finish before the other started, and the test would still pass through the ordinary replay path | New `PortfolioLockGate` holds the portfolio row lock until PostgreSQL (`pg_stat_activity`) reports both requests blocked on it; `TradeIdempotencyConcurrencyIntegrationTest` (R5-C2) now uses it | R2, R5 |
| G2 | No test raced two *different* trades that are each affordable but not together | `TradeConcurrentOverspendIntegrationTest` (R5-C1): 2 × 600.00 against 1,000.00 under the gate; exactly one executes, one is rejected, cash is 400.00 | R1, R5 |
| G3 | The snapshot test read `market_prices` through `JdbcTemplate`, so it checked PostgreSQL, not `PortfolioValuationService` | Javadoc now states that scope; new `PortfolioValuationConsistencyIntegrationTest` (R5-S1) commits a trade and a price *inside* a real valuation (via a spy) and checks that the result uses the old snapshot and REPEATABLE_READ | R3, R5 |
| G4 | The rollback test asserted only "some `IllegalStateException`", so a failure before the insert would also pass | It now checks the exception message, that the trade was visible inside the transaction, and that the after-commit `executed` metric did not move | R5, R8 |
| G5 | R4 freshness was tested only at unit level (`validateAt`) | Three API tests backdate or forward-date `updated_at`: 25 h old → 409, 23 h old → 200, 10 min ahead → 409 | R4 |
| G6 | Money in JSON was compared as `double` (`value(7160.00)`), which cannot see whether the API returned two decimals | `MoneyMatchers.money()` compares as `BigDecimal`; `assertMoneyField()` checks the raw JSON text (e.g. `7160.00`) | R3 |
| G7 | Valuation integration tests shared the global `AAPL`/`MSFT` quotes, so one test could overwrite another's price | Random symbols per test | R3, R4 |
| G8 | Three exception tests checked only the type, so the wrong guard could fire unnoticed | They now assert the exact message (`Negative position for AAPL`, `Calculated cash balance is negative`, `Insufficient historical position: AAPL`) | R1, R3 |
| G9 | Untested paths: `GET /trades` and its 404; the 0.00 / 0.01 price boundary; both guards in `TransactionalTradeMetrics`; a non-canonical (upper-case) correlation ID; correlation IDs in the application's log lines | `TradeControllerTest` (2), `MarketPriceServiceTest` MP-B1–MP-B3 (3), `TransactionalTradeMetricsTest` (2), `CorrelationIdFilterTest` (1), `CorrelationIdLoggingIntegrationTest` (1, `OutputCaptureExtension`) | R1, R4, R6, R8 |
| G10 | 409 tests checked only the status, not which conflict occurred | Combinatorial and state-machine tests also assert the conflict `detail` | R2 |
| G11 | No link from a test to its requirement or design ID | `@Tag` per requirement plus `integration`; parameterised cases named FT-xx / CP-xx (see [LO3 results §6](LO3-test-results.md#6-requirement-traceability-junit-tags)) | all |

**Result.** 14 new test cases (121 → 135); evidence: [135-test `mvnw clean verify` run](LO4-follow-up-2-test-run.txt). PIT was not re-run: its scope and configuration are unchanged, and the calculator tests only gained one message assertion, so the 23/23 result in §5 is not expected to change.

**Limits.** The lock gate makes the "both requests queued" schedule deterministic, but it is still only one interleaving; it does not explore the schedule space. Log assertions depend on the configured logstash console format. The JaCoCo figures in §5 are from the 121-test run and are not updated here.
