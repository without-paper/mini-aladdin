# LO3 — Test Execution Results

## 1. Test Suite Evolution

| Stage | Techniques / evidence | Full-suite tests | Failures | Errors | Skipped |
|---|---|---:|---:|---:|---:|
| Baseline | Original unit and integration tests | 69 | 0 | 0 | 0 |
| LO3-A | Equivalence partitioning and boundary analysis | 81 | 0 | 0 | 0 |
| LO3-B | Category-partition and exhaustive combinations | 94 | 0 | 0 | 0 |
| LO3-C | Model-based idempotency state-machine testing | 95 | 0 | 0 | 0 |
| LO3-D, post-fix | Regression and PostgreSQL API integration tests | 97 | 0 | 0 | 0 |
| LO3-E | JaCoCo-instrumented full regression verification | 97 | 0 | 0 | 0 |
| LO4 follow-up | SELL-history, boundary, invariant and HTTP 500 contract tests ([details](LO4-follow-up.md)) | 121 | 0 | 0 | 0 |

LO3-D also produced **two intentionally recorded pre-fix failures**: a unit reconciliation test (`expected: <0> but was: <-1>`) and an API integration test (HTTP 500 instead of 200). These are the evidence of defect D-02, not successful runs. Both tests passed after the production fix, and the complete post-fix regression suite passed.

## 2. Specification- and Model-based Adequacy

| Criterion | Defined scope | Exercised | Coverage |
|---|---|---:|---:|
| Equivalence partition coverage | Eight defined R1/R4 partitions | 8/8 | 100% |
| Boundary coverage | Twelve specified R1/R4 cases | 12/12 | 100% |
| Normal-domain combination coverage | Twelve feasible R2 test frames | 12/12 | 100% |
| Error-choice execution | Invalid idempotency key | 1/1 | 100% |
| Finite-state-machine state coverage | U and B states | 2/2 | 100% |
| Finite-state-machine transition coverage | T1–T4 | 4/4 | 100% |

These completeness figures apply **only to the chosen abstractions**, not the whole input, state or concurrent-schedule space.

## 3. Structural Coverage (LO3-E)

JaCoCo 0.8.16 instrumented the full **97-test** suite:

- **Line coverage:** 574/615 = **93.3%** (41 missed lines).
- **Branch coverage:** 123/180 = **68.3%** (57 missed branches).
- **Instruction coverage:** 2,012/2,187; the JaCoCo HTML interface displays **91%** (175 missed instructions).

| Selected production class | Covered lines | Covered branches |
|---|---:|---:|
| `TradeService` | 95/110 (86.4%) | 34/52 (65.4%) |
| `PortfolioValuationCalculator` | 77/80 (96.3%) | 25/34 (73.5%) |
| `MarketPriceFreshnessPolicy` | 22/22 (100%) | 8/8 (100%) |

The unexecuted conditions, particularly in `TradeService`, limit the assurance provided by the high overall line coverage. JaCoCo counts bytecode-level branches primarily from `if` and `switch`, so branch coverage is not the same as all possible control-flow paths or exception outcomes.

**Evidence:** [LO3-E-jacoco-test-run.txt](LO3-E-jacoco-test-run.txt), [LO3-E-jacoco.csv](LO3-E-jacoco.csv).

## 4. Fault-based / Mutation Testing (LO3-E)

PIT 1.30.2 was restricted to **one production class**, `PortfolioValuationCalculator`, using three calculator-focused test classes.

| Mutator | Generated | Killed | Survived |
|---|---:|---:|---:|
| Conditional boundaries | 5 | 1 | 4 |
| Negate conditionals | 15 | 15 | 0 |
| Null return values | 3 | 3 | 0 |
| **Total** | **23** | **19** | **4** |

- **Mutation coverage:** 19/23 = **82.6%** (PIT reports 83%).
- **Test strength:** 19/23 = **82.6%** (PIT reports 83%).
- **No-coverage mutants:** 0; **time-outs:** 0.
- **PIT line coverage within selected class:** 77/80 = **96.3%** (PIT reports 96%).

All four surviving mutants came from boundary-operator mutations. Their exact locations and equivalence status have not yet been assessed; they are potential test-design gaps, **not confirmed production defects**. The PIT mutation score must not be described as project-wide because the experiment mutated one class.

**Evidence:** [LO3-E PIT baseline log](LO3-E-pitest-run.txt).
The detailed [PIT report](pit-reports/index.html) and
[`mutations.xml`](pit-reports/mutations.xml) have been committed.

The initial four surviving boundary mutants were subsequently
killed by targeted tests, increasing mutation coverage from
19/23 to 23/23 in the same calculator scope.
See [LO4 follow-up](LO4-follow-up.md).

## 5. Findings and Defect Yield

**D-01 — Test-suite weakness (corrected):** Existing rejected-trade tests asserted that `save()` was never invoked, while production code uses `saveAndFlush()`. The assertions were corrected. This is a test-quality finding, not a production defect.

**D-02 — Confirmed production defect (fixed and regression-tested):** When a partial sale allocated a fractional-cent weighted-average cost, independently rounded realised and unrealised P&L did not reconcile with total P&L. The unit regression test failed, and the API returned HTTP 500. The cost allocation was changed to round the removed cost basis to cents with `HALF_UP`, preserving the residual cost basis. Both tests passed after the fix, as did the complete 97-test regression suite.

**Defect count:** one distinct production defect confirmed and fixed during LO3, supported by two failing pre-fix tests. The four surviving PIT mutants are separate test-quality investigation candidates and are not counted as production defects. A production defect count is reported here rather than an unqualified defect-yield percentage, because a consistent detection-opportunity denominator has not been defined.

## 6. Evidence Index

- [Baseline test run](baseline-test-run.txt)
- [LO3-A functional tests](LO3-A-functional-test-run.txt)
- [LO3-B combinatorial tests](LO3-B-combinatorial-test-run.txt)
- [LO3-C model-based tests](LO3-C-model-based-test-run.txt)
- [LO3-D pre-fix unit failure](LO3-D-rounding-before-fix.txt)
- [LO3-D pre-fix integration failure](LO3-D-integration-before-fix.txt)
- [LO3-D post-fix 97-test regression](LO3-D-rounding-after-fix.txt)
- [LO3-E JaCoCo test run](LO3-E-jacoco-test-run.txt)
- [LO3-E raw coverage CSV](LO3-E-jacoco.csv)
- [LO3-E PIT log](LO3-E-pitest-run.txt)
- [LO3-F R7 benchmark summary](LO3-F-r7-run.txt)
- [LO3-F raw latency measurements](r7-F1F66185-raw.csv)
- [LO3-F per-run statistics](r7-F1F66185-summary.csv)
- [LO3-F environment metadata](r7-F1F66185-environment.json)
- [Detailed LO3 test design](LO3-test-design.md)
- [LO4 follow-up: branch classification, new tests, results](LO4-follow-up.md)
- [R7 statistical analysis](LO4-R7-statistics.txt) (script: `performance/r7_statistics.py`)
