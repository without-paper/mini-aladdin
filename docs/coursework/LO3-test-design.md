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

### 2.6 Test Evolution and Findings

Inspection of the baseline rejection tests identified a weak persistence assertion: the tests checked that `save()` was not called, whereas the current implementation uses `saveAndFlush()`.

The tests will be strengthened to verify the actual persistence operation. This finding concerns test quality; it does not establish a production defect.

Execution results, achieved coverage and any newly confirmed defects will be recorded after running the updated test suite.

## References

- [Software Testing Tutorial LO3 and Sample Testing Evidence](https://opencourse.inf.ed.ac.uk/st/week-6-7).
- Young and Pezzè, Chapter 10, Functional Testing, particularly slides 8, 11, 13 and 18.