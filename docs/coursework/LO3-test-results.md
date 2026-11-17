# LO3 — Test Execution Results

## 1. Test Suite Evolution

| Stage | Techniques | Total tests | Failures | Errors |
|---|---|---:|---:|---:|
| Baseline | Existing unit and integration tests | 69 | 0 | 0 |
| LO3-A | Functional partitioning and boundary analysis | 81 | 0 | 0 |
| LO3-B | Category-partition and exhaustive combinations | 94 | 0 | 0 |
| LO3-C | Model-based state-machine testing | TBD | TBD | TBD |

All reported results refer to actual Maven test executions recorded in the corresponding test logs. LO3-C results will be added after execution.

## 2. Test Adequacy

| Criterion | Defined scope | Achieved |
|---|---|---|
| Equivalence partition coverage | 8 defined partitions in R1/R4 | 8/8 |
| Boundary coverage | 12 defined boundary cases | 12/12 |
| Combination coverage | 12 normal-domain R2 combinations | 12/12 |
| Error-choice execution | Invalid idempotency key | 1/1 |
| FSM state coverage | 2 defined states | TBD |
| FSM transition coverage | 4 defined transitions | TBD |

All coverage figures refer to explicitly defined test models rather than the entire application's input or state space.

## 3. Findings and Defect Yield

**D-01 — Weak persistence assertion:** Existing rejection tests checked `save()` rather than the actual `saveAndFlush()` operation. The assertions were strengthened. Classification: test-suite weakness.

**D-02 — Potential financial rounding inconsistency:** Independent rounding of realised, unrealised and total P&L may violate accounting reconciliation. Classification: suspected production defect, awaiting a targeted regression test.

No production defect has yet been confirmed by the new LO3-A or LO3-B tests.

Test yield and confirmed defect counts will be updated when additional faults are discovered and validated.

## 4. Evidence

- `baseline-test-run.txt`
- `LO3-A-functional-test-run.txt`
- `LO3-B-combinatorial-test-run.txt`
- `LO3-C-model-based-test-run.txt` (pending)
- `LO3-test-design.md`