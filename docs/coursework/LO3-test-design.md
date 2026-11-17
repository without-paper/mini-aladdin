# LO3 — Test Design and Evaluation

## 1. MVP and Required Evidence

The Mini Aladdin MVP supports portfolio creation, simulated BUY/SELL execution, cash and position reporting, and portfolio valuation.

R1 (trade correctness) is a key requirement for the MVP. A prospective user needs evidence that valid trades are executed correctly and that insufficient cash or holdings cannot result in invalid portfolio state.

The initial suite contains 69 passing tests, including financial calculations, API behaviour, idempotency and transaction rollback. However, the existing tests do not systematically demonstrate acceptance and rejection at the financial boundaries. The testing strategy therefore extends the baseline using functional partitioning, boundary value analysis and complementary integration testing.

This design follows the preparatory activity in Software Testing Tutorial LO3 and the specification-based test selection process in Young and Pezzè Chapter 10.

## 2. Functional Testing

### 2.1 Test Basis and Feature Decomposition

The test basis is the requirements defined in [LO1](LO1-requirements.md) and the [API specification](../API.md).

R1 is decomposed into the BUY cash constraint and SELL holding constraint. R4 is decomposed into the lower and upper quote freshness boundaries and missing timestamp handling.

The following tests use representative values and boundaries derived from the intended behaviour. Repository mocks and a fixed reference time provide controlled test conditions.

### 2.2 Equivalence Partitions

| Feature | Identified partitions |
|---|---|
| BUY cash constraint | Insufficient cash; sufficient cash |
| SELL holding constraint | Insufficient holdings; sufficient holdings |
| Quote freshness | Expired; valid; excessively future-dated; missing timestamp |

Equality is a particularly important boundary within each valid trade partition. The intended behaviour permits spending exactly the available cash and selling exactly the available holdings.

### 2.3 Boundary Test Specifications

| ID | Requirement | Input and precondition | Expected result |
|---|---|---|---|
| FT-01 | R1 | BUY 10 shares at $100; cash $999.99 | Reject; no persistence call |
| FT-02 | R1 | BUY 10 shares at $100; cash $1,000.00 | Accept; one persistence call |
| FT-03 | R1 | BUY 10 shares at $100; cash $1,000.01 | Accept; one persistence call |
| FT-04 | R1 | Hold 10 shares; SELL 9 | Accept; one persistence call |
| FT-05 | R1 | Hold 10 shares; SELL 10 | Accept; one persistence call |
| FT-06 | R1 | Hold 10 shares; SELL 11 | Reject; no persistence call |
| FT-07 | R4 | Quote offset −86401 seconds | Reject |
| FT-08 | R4 | Quote offset −86400 seconds | Accept |
| FT-09 | R4 | Quote offset −86399 seconds | Accept |
| FT-10 | R4 | Quote offset +299 seconds | Accept |
| FT-11 | R4 | Quote offset +300 seconds | Accept |
| FT-12 | R4 | Quote offset +301 seconds | Reject |

The trade cases use one cent and one share as the respective boundary increments, consistent with the documented API constraints. The freshness cases use one-second increments and a fixed reference time. Subsecond behaviour is outside this particular boundary model.

### 2.4 Implementation and Oracles

FT-01–FT-06 are implemented as parameterised JUnit tests in `TradeServiceTest`, using Mockito to control portfolio state and observe persistence interactions.

FT-07–FT-12 are implemented in `MarketPriceFreshnessPolicyTest`, using `validateAt()` with a fixed reference time.

Existing tests for missing timestamps, empty portfolios and ordinary financial calculations are retained.

The unit-level trade oracle checks acceptance or rejection, returned trade details and the expected `saveAndFlush()` interaction. It does not independently verify committed database state. Subsequent integration tests will verify the observable financial effects using PostgreSQL.

### 2.5 Adequacy Criteria

The functional testing completion criteria are:

- All eight identified equivalence partitions are exercised.
- All twelve specified boundary cases are exercised.
- All associated tests pass.

Partition coverage is calculated as exercised partitions divided by identified partitions. Boundary coverage is calculated as exercised boundary cases divided by specified boundary cases.

These criteria provide evidence for the specified constraints, but they cannot establish correctness across all trade histories, input combinations or concurrent schedules. Additional structural, combinatorial and model-based testing will address complementary risks.

**2.6 Execution Results and Findings**

The functional testing suite was extended with twelve parameterised boundary cases. The full Maven verification completed successfully with 81 tests, zero failures, zero errors and zero skipped tests. All eight defined equivalence partitions and twelve boundary cases were exercised, achieving 100% coverage relative to these explicitly defined models.

Inspection of the baseline rejection tests identified an ineffective persistence assertion that checked `save()` instead of the actual `saveAndFlush()` operation. The assertions were corrected. This was a test-suite weakness, with no confirmed production defect identified in this stage.

Execution evidence: `LO3-A-functional-test-run.txt`.

## 3. Combinatorial Testing

### 3.1 Test Basis and Feature Selection

Combinatorial testing was designed for R2 (idempotent trade execution) using the Category-partition method described in Young and Pezzè Chapter 11.

The feature under test is the execution of a trade request in the presence of an existing trade using an idempotency key.

The test fixture contains two independent portfolios. Portfolio A initially contains a committed BUY trade with key `existing-key-0001`; Portfolio B contains no trades. Both portfolios have sufficient cash for all selected requests.

### 3.2 Categories and Choices

Three categories were identified:

| Category | Choices |
|---|---|
| C1: Target portfolio | Original portfolio A; independent portfolio B |
| C2: Idempotency key | Previously used key; fresh key |
| C3: Request payload | Identical; canonically equivalent; different |

The canonically equivalent payload changes the symbol's letter case and the decimal representation of the price without changing the underlying transaction. The different payload changes the quantity.

The full Cartesian product contains 2 × 2 × 3 = 12 combinations. All twelve combinations are feasible under the controlled test fixture. Therefore, exhaustive combination testing was selected instead of pairwise reduction.

### 3.3 Constraints and Test Frames

The original trade must already be committed before each test frame. The second portfolio must be independent, and all selected payloads must satisfy the application's validation and balance constraints.

The twelve normal-domain test frames specify the expected HTTP result and database state. Matching requests using an existing key in the original portfolio must replay the trade. A conflicting request in that portfolio must return HTTP 409. All requests using a new key within the target portfolio must create a new trade.

An additional error choice tests a malformed idempotency key. The existing tests for a missing key provide complementary input-validation evidence.

### 3.4 Implementation and Evaluation Criteria

The tests are implemented in `TradeIdempotencyCombinatorialTest` using JUnit parameterisation, Spring MockMvc and PostgreSQL Testcontainers.

Each test checks the HTTP result, persisted trade counts and the resulting cash balances. Replay cases also verify that the original trade identifier is returned.

The adequacy criteria are:

- All 12 feasible normal-domain combinations exercised.
- The malformed-key error choice exercised.
- All selected combinations produce the expected HTTP and database outcomes.

Combination coverage is the proportion of defined feasible combinations executed. Test pass rate is reported separately.

This testing provides systematic evidence about the interactions among the three selected categories. It does not establish correctness for all request fields, other business states or concurrent execution schedules.

### 3.5 Execution Results

The combinatorial tests were executed successfully using JUnit, Spring MockMvc and PostgreSQL Testcontainers.

- **Executed normal-domain combinations:** 12 / 12
- **Error-choice tests executed:** 1 / 1
- **New tests passed:** 13 / 13
- **Combination coverage:** 100% (12 / 12 feasible combinations)
- **Full regression suite:** 94 tests passed, 0 failures, 0 errors, 0 skipped
- **Production defects confirmed:** 0

All defined test frames produced the expected HTTP responses, persisted trade counts and portfolio cash balances. The results confirm the expected idempotency behaviour for the selected combinations of portfolio, request key and payload.

The 100% combination coverage applies only to the three defined categories. It does not guarantee correctness for other inputs, business states or concurrent executions.

**Evidence:** `LO3-B-combinatorial-test-run.txt`.

## 4. Model-based Testing

### 4.1 Test Basis and Model Selection

Model-based testing was applied to R2 (idempotent trade execution), following Young and Pezzè Chapters 5 and 14.

The model describes the lifecycle of a single idempotency key within a particular portfolio. It abstracts away unrelated portfolio details while retaining the information needed to distinguish new executions, successful replays, business-rule rejection and conflicts.

Two states were defined:

- **U (Unbound):** No committed trade is associated with the selected key in the portfolio.
- **B (Bound):** The key is associated with a committed trade and its canonical request fingerprint.

### 4.2 Finite State Machine

| Transition | Initial state | Event and condition | Output | Final state |
|---|---|---|---|---|
| T1 | U | Business-rule rejection (insufficient cash) | HTTP 400 | U |
| T2 | U | Valid executable trade | HTTP 201 | B |
| T3 | B | Same key and equivalent canonical request | HTTP 200; original trade | B |
| T4 | B | Same key and conflicting request | HTTP 409 | B |

The model is a Mealy-style state machine, where observable outputs depend on the current state and input event.

Business-rule rejection does not bind the key. Successful execution creates the binding, while subsequent replays and conflicts preserve it.

The model focuses on valid request keys and selected business outcomes. Invalid HTTP input, cross-portfolio interactions and concurrency are addressed by complementary tests.

### 4.3 Test Sequence

A portfolio with initial cash of $200 is created. Five requests are executed sequentially using the same idempotency key.

| Step | Event | Expected HTTP | Expected trade count | Expected cash |
|---|---|---|---|---|
| 1 | BUY 3 at $100 | 400 | 0 | $200 |
| 2 | BUY 2 at $100 | 201 | 1 | $0 |
| 3 | Equivalent replay of step 2 | 200 | 1 | $0 |
| 4 | Conflicting BUY 1 at $100 | 409 | 1 | $0 |
| 5 | Replay the original valid request | 200 | 1 | $0 |

The sequence traverses T1 → T2 → T3 → T4 → T3.

It verifies that a rejected trade does not reserve the key and that a subsequent conflict does not corrupt the original binding.

### 4.4 Test Implementation and Oracles

The test is implemented in `TradeIdempotencyStateMachineTest.java` using JUnit, MockMvc and PostgreSQL Testcontainers.

After each transition, the test checks the expected HTTP response, persisted trade count and available cash. Replay requests must return the original trade identifier. The final portfolio must contain exactly two AAPL shares.

The model and its expected observations are defined before execution.

### 4.5 Adequacy Criteria

The selected adequacy criteria are:

- **State coverage:** Both defined states are visited (target: 2/2).
- **Transition coverage:** All four defined transitions are exercised (target: 4/4).
- **Sequence correctness:** The specified five-event sequence produces all expected outcomes.

The test also exercises the transition pair T4 → T3, checking that an idempotency conflict does not prevent a later valid replay.

### 4.6 Limitations

The FSM is an abstraction of a single key within one portfolio. It does not represent every invalid input, multiple interacting keys, all financial states or concurrent request interleavings.

Full transition coverage therefore does not imply complete behavioural correctness. The model-based test complements the combinatorial and concurrency integration tests.

### 4.7 Execution Results

The model-based test completed successfully. The five-event sequence T1 → T2 → T3 → T4 → T3 checked expected HTTP responses, persisted trade counts, cash balances, the original trade ID on replay, and the final position.

- **States visited:** 2 / 2 (100% state coverage of the defined model).
- **Transitions exercised:** 4 / 4 (100% transition coverage of the defined model).
- **Model-based test:** 1 / 1 passed.
- **Full regression suite:** 95 tests passed; 0 failures, 0 errors, 0 skipped.
- **Production defects confirmed in LO3-C:** 0.

Coverage is relative to the selected two-state, four-transition model; other request histories and concurrent interleavings remain outside its scope.

**Evidence:** [LO3-C-model-based-test-run.txt](LO3-C-model-based-test-run.txt).

## 5. Regression and Integration Testing

### 5.1 Test Basis and Risk

Testing focused on R3 (financial valuation correctness). Source-code inspection identified a potential inconsistency when weighted-average cost allocation produces fractional cents and realised, unrealised and total P&L are rounded independently.

Existing valuation tests use transaction histories whose cost allocations do not expose this particular rounding interaction.

### 5.2 Test Specification

A portfolio starts with $100.00 and performs three transactions:

1. BUY 1 AAPL at $0.01.
2. BUY 1 AAPL at $0.02.
3. SELL 1 AAPL at $0.02.

The remaining share has a market price of $0.02.

An independent cash-flow oracle gives:

- Available cash: $99.99
- Market value: $0.02
- Total portfolio value: $100.01
- Total P&L: $0.01

The reported realised and unrealised P&L must sum to the reported total P&L.

### 5.3 Testing Techniques

**Unit regression testing:** `PortfolioValuationRoundingRegressionTest` checks the calculator's output against independently derived cash-flow values and the accounting reconciliation invariant.

**Integration testing:** `PortfolioValuationRoundingIntegrationTest` executes the trading history using PostgreSQL, updates a market quote and requests portfolio valuation through MockMvc. The expected response is HTTP 200 with reconciled financial values.

### 5.4 Adequacy and Evaluation

The tests target a previously untested fractional-cent cost-allocation scenario. Their purpose is to detect financial reconciliation errors and determine whether they propagate to the API.

The selected tests provide focused evidence for this rounding scenario. They do not establish correctness for every possible trading history or rounding policy.

### 5.5 Execution Results and Defect Record

Both targeted tests were executed against the pre-fix code and failed, providing complementary evidence of one production defect (D-02).

- **Unit regression test:** 1 test executed, 1 failed, 0 errors. `PortfolioValuationRoundingRegressionTest.shouldReconcilePnlAfterPartialSaleWithFractionalCost` failed at line 86: `expected: <0> but was: <-1>`. Its earlier independent cash-flow assertions passed: available cash $99.99, market value $0.02, total value $100.01 and total P&L $0.01. The failed assertion compares total P&L with the sum of reported realised and unrealised P&L.
- **Integration test:** 1 test executed, 1 failed, 0 errors. `PortfolioValuationRoundingIntegrationTest.shouldReturnReconciledValuationThroughApi` expected HTTP 200 but received 500 (line 84). Application logs identify `IllegalStateException: P&L components do not reconcile` in `PortfolioValuationService.validateAccountingInvariant()`.
- **Confirmed root cause:** `PortfolioValuationCalculator` independently rounds realised P&L, unrealised P&L and total P&L to two decimal places with `HALF_UP`. In the specified trade history, the two exact components are $0.005 each. Individually reported, they become $0.01 and $0.01, while rounded total P&L is $0.01. Their displayed sum therefore disagrees with the displayed total, triggering the service-level accounting invariant.
- **Corrective action:** Pending. A consistent rounding and allocation policy must be documented and implemented, with attention to both portfolio-level and position-level totals.
- **Post-fix regression result:** Pending; no successful post-fix report is available in the reviewed branch.
- **Confirmed production defects:** 1 (D-02). The two failures are observations of the same underlying defect, not two distinct defects.

The last verified successful full-suite run comprised 95 tests before these two regression tests were added. A new full-suite execution is required after correction. The targeted evidence establishes reproducibility for this scenario, but not the frequency of the problem in other portfolios.

**Evidence:**
- [LO3-D-rounding-before-fix.txt](LO3-D-rounding-before-fix.txt)
- [LO3-D-integration-before-fix.txt](LO3-D-integration-before-fix.txt)
- `LO3-D-rounding-after-fix.txt` (pending)

### References

- Young and Pezzè, Chapter 5: Finite Models, especially slides 17–20.
- Young and Pezzè, Chapter 14: Model-based Testing, especially slides 3–9.
- Mini Aladdin: `TradeService.java`, `TradeController.java` and `docs/API.md`.

## References

- [Software Testing Tutorial LO3 and Sample Testing Evidence](https://opencourse.inf.ed.ac.uk/st/week-6-7).
- Young and Pezzè, Chapter 10, Functional Testing, particularly slides 8, 11, 13 and 18.