# LO4 Follow-up — Acting on Coverage, Mutation and Performance Evidence

[LO4 evaluation](LO4-evaluation.md) identified uncovered branches, four unexplained PIT survivors and an unexplained R7 latency outlier, but left them as proposals. This document records the follow-up: the missed branches are classified (LO4.3), targets are set for the P0 classes (LO4.2), new tests close the genuine gaps (LO4.4), and the R7 data is analysed statistically. Sections 6 and 7 record a later review of the test code itself and the resulting 135-test coverage. Section 8 records a third review, which removed repeated tests and added requirement-driven edge-case, business-flow and failure-recovery tests (137 tests).

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

The JaCoCo CSV and XML of this run were not kept in the repository. The log shows that JaCoCo generated its report ([line 5006](LO4-follow-up-test-run.txt#L5006)), but the line and branch figures below cannot be re-derived from committed files. The next committed coverage reports are the 135-test CSV and XML (§7).

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
| G1 | The concurrency test started two threads from a latch, but nothing forced them to overlap; one request could finish before the other started, and the test would still pass through the ordinary replay path | New `PortfolioLockGate` holds the portfolio row lock in a separate transaction until `pg_stat_activity` shows two sessions waiting on a lock, then releases it (see *Limits* below); `TradeIdempotencyConcurrencyIntegrationTest` (R5-C2) now uses it | R2, R5 |
| G2 | No test raced two *different* trades that are each affordable but not together | `TradeConcurrentOverspendIntegrationTest` (R5-C1): 2 × 600.00 against 1,000.00 under the gate; exactly one executes, one is rejected, cash is 400.00 | R1, R5 |
| G3 | The snapshot test read `market_prices` through `JdbcTemplate`, so it checked PostgreSQL, not `PortfolioValuationService` | Javadoc now states that scope; new `PortfolioValuationConsistencyIntegrationTest` (R5-S1) commits a trade and a price *inside* a real valuation (via a spy) and checks that the result uses the old snapshot and REPEATABLE_READ | R3, R5 |
| G4 | The rollback test asserted only "some `IllegalStateException`", so a failure before the insert would also pass | It now checks the exception message, that the trade was visible inside the transaction, and that the after-commit `executed` metric did not move | R5, R8 |
| G5 | R4 freshness was tested only at unit level (`validateAt`) | Three API tests backdate or forward-date `updated_at`: 25 h old → 409, 23 h old → 200, 10 min ahead → 409 | R4 |
| G6 | Money in JSON was compared as `double` (`value(7160.00)`), which cannot see whether the API returned two decimals | `MoneyMatchers.money()` compares as `BigDecimal`; `assertMoneyField()` checks the raw JSON text (e.g. `7160.00`) | R3 |
| G7 | Valuation integration tests shared the global `AAPL`/`MSFT` quotes, so one test could overwrite another's price | Random symbols per test | R3, R4 |
| G8 | Three exception tests checked only the type, so the wrong guard could fire unnoticed | They now assert the exact message (`Negative position for AAPL`, `Calculated cash balance is negative`, `Insufficient historical position: AAPL`) | R1, R3 |
| G9 | Paths no Java test exercised: `GET /trades` (called only by the CI smoke script) and its 404; the 0.00 / 0.01 price boundary; both guards in `TransactionalTradeMetrics`; a non-canonical (upper-case) correlation ID; correlation IDs in the application's log lines | `TradeControllerTest` (2), `MarketPriceServiceTest` MP-B1–MP-B3 (3), `TransactionalTradeMetricsTest` (2), `CorrelationIdFilterTest` (1), `CorrelationIdLoggingIntegrationTest` (1, `OutputCaptureExtension`) | R1, R4, R6, R8 |
| G10 | 409 tests checked only the status, not which conflict occurred | Combinatorial and state-machine tests also assert the conflict `detail` | R2 |
| G11 | No link from a test to its requirement or design ID | `@Tag` per requirement plus `integration`; parameterised cases named FT-xx / CP-xx (see [LO3 results §6](LO3-test-results.md#6-requirement-traceability-junit-tags)) | all |

**Result.** 14 new test cases (121 → 135 tests) and stronger assertions in existing tests. Evidence: the [135-test `mvnw clean verify` run](LO4-follow-up-2-test-run.txt#L6095) and [CI run #41](https://github.com/without-paper/mini-aladdin/actions/runs/45089342461), both on commit `4ab1008`.

**PIT.** PIT was not re-run for this stage, because a re-run could not change the result. `PortfolioValuationCalculator` has not changed since the D-02 fix (`1a6d0c1`), which predates the 23/23 report in §5, so the same 23 mutants would be generated. The three PIT target test classes only gained `@Tag` annotations and one exception-message assertion, and the PIT configuration has no tag filter. The only `pom.xml` change since that report removed the unused TestNG dependency (RV-06); the PIT plugin configuration is unchanged. Adding an assertion to a test cannot make a previously killed mutant survive. The 23/23 result therefore remains valid historical evidence for this mutant set; it is not presented as a new execution.

**Limits.** `PortfolioLockGate` counts sessions whose `wait_event_type` is `Lock`; it does not identify which sessions they are. This is adequate here: the test database belongs to the test's Spring context, test classes run one at a time, and the only query that can wait during these tests is the portfolio row-lock query (`findByIdForUpdate`) issued by the two requests. If the requests never block, the gate times out and the test fails instead of passing silently. A stricter check with `pg_blocking_pids()` was considered but not adopted: PostgreSQL normally queues the second request behind the first (on the row's tuple lock) rather than directly behind the gate, so a correct check would need a recursive blocking-chain query, adding complexity for little gain in this isolated environment. The gate forces one schedule (both requests queued behind the lock); it does not explore other interleavings. Log assertions depend on the configured logstash console format.

## 7. Updated Structural Coverage (135-test Suite)

Following the second test-code review, the full 135-test suite was executed using JaCoCo 0.8.16.

All 135 tests passed with no failures, errors or skips. The new coverage report is preserved separately from the earlier 97-test baseline and 121-test follow-up.

| Measure | 97 tests (LO3-E) | 121 tests (§5) | 135 tests |
|---|---:|---:|---:|
| Line coverage | 574/615 (93.3%) | 587/615 (95.4%) | 591/615 (96.1%) |
| Branch coverage | 123/180 (68.3%) | 133/180 (73.9%) | 136/180 (75.6%) |

**Evidence:** [135-test execution log](LO4-follow-up-2-test-run.txt), [updated JaCoCo CSV](LO4-follow-up-2-jacoco.csv), and [updated JaCoCo XML](LO4-follow-up-2-jacoco.xml). The CSV and XML come from a repeat `mvnw clean verify` of the same commit (`4ab1008`; JaCoCo session started 23 November 2026, 01:01 UTC), which again ran 135 tests with no failures, errors or skips.

The additional tests primarily strengthen test oracles, concurrency control and integration-level behaviour. Any coverage change must be interpreted in that context rather than treated as the primary measure of test quality.

From 121 to 135 tests, coverage rose by only 4 lines and 3 branches. The 97-test and 135-test CSVs differ in seven classes. Four of them account for the whole 121-stage gain of 13 lines and 10 branches (§3 and §5: `TradeService` +5/+3, `PortfolioValuationCalculator` +3/+4, `PortfolioValuationService` +3/+3, and `GlobalExceptionHandler.handleUnexpected` +2 lines). The newly covered code at the 135-test stage is therefore both guards in `TransactionalTradeMetrics` (2 lines, 2 branches), the non-canonical-ID branch in `CorrelationIdFilter` (1 line, 1 branch) and the `GET /trades` path in `TradeController` (1 line). Most of the review's changes strengthened assertions on code that was already executed, which coverage cannot show.

The P0 classes are unchanged from §5, as the 135-test XML confirms line by line: `TradeService` 37/52 branches (the same 13 defensive duplicates and 2 impossible cases), `PortfolioValuationCalculator` 29/34 (the five persisted-data checks on line 57) and `PortfolioValuationService` 6/6.

## 8. Third follow-up: requirement-driven test review (137 tests)

The third review asked a different question: does each test check a distinct, requirement-level behaviour, or does it repeat one that another test already checks with a different input? It applied five principles. Every test needs a unique purpose. Partly valid input, and valid input that falls on a boundary, are worth more than further happy-path cases. Integration tests should follow complete business operations. Failure and recovery should be exercised, not assumed. Every test should trace to a requirement in [LO1](LO1-requirements.md).

**Removed: eight tests that repeated another test's behaviour.** Each removed test fell in the same partition as a retained boundary case, and the retained case sits closer to the boundary. All eight equivalence partitions and FT-01–FT-12 remain exercised ([LO3 design §2](LO3-test-design.md#22-equivalence-partitions)).

| Removed test | Input | Same behaviour already checked by |
|---|---|---|
| `MarketPriceFreshnessPolicyTest.shouldAcceptFreshQuote` | 60 s old | FT-09, FT-10 |
| `MarketPriceFreshnessPolicyTest.shouldRejectQuoteOlderThan24Hours` | 25 h old | FT-07 (24 h + 1 s) |
| `MarketPriceFreshnessPolicyTest.shouldAcceptQuoteAt24HourBoundary` | exactly 24 h | FT-08 (same input) |
| `MarketPriceFreshnessPolicyTest.shouldRejectFarFutureTimestamp` | 10 min ahead | FT-12 (5 min + 1 s) |
| `TradeServiceTest.shouldAllowBuyWithEnoughCash` | BUY 2,000.00 with 10,000.00 | FT-02, FT-03 |
| `TradeServiceTest.shouldRejectBuyWhenCashInsufficient` | BUY 2,000.00 with 1,000.00 | FT-01 |
| `TradeServiceTest.shouldRejectSellWhenPositionInsufficient` | SELL 10 of 5 held | FT-06 |
| `MarketPriceServiceTest.shouldRejectNegativePrice` | −10.00 | MP-B1 (−0.01) |

The removed rejection tests asserted only the exception type. FT-01 and FT-06 now also assert the message (`Insufficient cash`, `Insufficient position`), so the retained tests are stronger than the ones removed.

**Added: ten tests for behaviour no existing test checked.**

| ID | Principle | Scenario and oracle | Requirement |
|---|---|---|---|
| R6-E1 | Partly valid input | Valid symbol and side, but quantity 2.5 and price 100.005. Expect 400 listing exactly `quantity` and `price`, no trade stored, the `executed` counter unchanged; the corrected request with the *same* key then returns 201 (a rejected request must not consume the key) | R2, R6 |
| BV-K1–BV-K4 | Valid format on a boundary | Idempotency-Key of 7, 8, 128 and 129 allowed characters → 400, 201, 201, 400. The 128-character key must also fit the `request_key` column | R2, R6 |
| BV-S1–BV-S2 | Valid format on a boundary | Well-formed symbol of 20 and 21 characters → 201, 400; the 400 must come from validation (`symbol` only), not from the 20-character column as a server error | R6 |
| R4-V8 | Partly valid data | Two open positions, one quote fresh and one 25 h old. Expect 409 naming the stale symbol, not a valuation that silently uses the fresh quote alone; after the refresh, 200 with exact cash, market value and total | R3, R4 |
| R4-V9 | Unexpected behaviour | A closed position (BUY 3, SELL 3) without a quote next to an open one without a quote. Expect 409 naming only the open symbol; after pricing it, 200 with one position and the closed trade's realised P&L (60.00) kept | R3, R4 |
| R1-F1 | Complete business flow | BUY 6, BUY 4, SELL 10, with the symbol in different letter cases. Cash goes 10,000.00 → 9,000.00 → 10,000.00, the position closes, valuation returns zero P&L without needing a quote, and history holds three trades under one upper-case symbol | R1, R3, R4 |

R1-F1 follows the pattern of an account that is credited twice and then debited by the same total, which must return to its starting state. Three components (trade execution, position calculation and valuation) must agree for it to pass.

**Added: an automated failure-and-recovery check in CI.** The Docker job now runs [`scripts/ci-recovery.sh`](../../scripts/ci-recovery.sh) after the smoke test, against the same isolated Compose stack. It stops the PostgreSQL container while the application keeps running. It then checks that health turns to 503 and that a trade attempted during the outage fails with a 5xx Problem Details body whose detail is the generic `An unexpected error occurred`, with no `Exception`, `org.`, `java.`, `SQL`, `jdbc` or `postgres` text. After the database restarts, health must return to 200 without restarting the application, and the trade history must still be empty. The client then retries with the *same* Idempotency-Key and gets 201, and a second retry replays the same trade (200, same ID), leaving exactly one trade in history. This turns the earlier operator-reported experiment ([LO5](LO5-recovery-experiment.md)) into repeatable evidence, and it shows that R2 is what makes a client retry after a failure safe.

**Results.** All 137 tests passed with no failures, errors or skips ([137-test `mvnw clean verify` run](LO4-follow-up-3-test-run.txt#L7391)). CI [run #44](https://github.com/without-paper/mini-aladdin/actions/runs/45266517282) on commit `910bffd` passed both jobs, including the new step *Database failure and recovery check*.

| Measure | 135 tests (§7) | 137 tests |
|---|---:|---:|
| Line coverage | 591/615 (96.1%) | 591/615 (96.1%) |
| Branch coverage | 136/180 (75.6%) | 136/180 (75.6%) |
| Instructions | 2,087/2,187 | 2,087/2,187 |

Evidence: [137-test JaCoCo CSV](LO4-follow-up-3-jacoco.csv) and [XML](LO4-follow-up-3-jacoco.xml). Coverage did not change: the 137-test XML matches the 135-test XML line by line, including the branch counts on each line. The eight removed tests therefore covered nothing that the remaining suite does not. The ten new tests check outcomes on code that was already executed: which fields are reported, whether a key stays unused, which symbol a conflict names, and whether a round trip returns to its start. Coverage cannot measure any of these. PIT was not re-run, for the reasons in §6: the calculator and the three PIT target classes are unchanged by this review.

**Limits.** The combinatorial R2 table is kept although 9 of its 12 rows lead to the same outcome, because it is the documented category-partition record ([LO3 design](LO3-test-design.md)); it is the main remaining overlap. The CI check injects one fault type (the database stopping cleanly, then returning) at one point in the flow. It does not test network partitions, slow or intermittent responses, or a failure during commit. The application has no retry, back-off or failover of its own; recovery relies on the client retrying with the same key.
