# LO1 — Requirements and Test Strategy

## 1. System Under Test and Scope

Mini Aladdin is a Java 21 and Spring Boot backend for simulated portfolio management and trade execution, using PostgreSQL for persistence. It supports portfolio creation, BUY and SELL trades, cash and position reporting, market price updates, and portfolio valuation.

The testing scope focuses on trade correctness, financial accounting, idempotency, data consistency, API robustness, performance and diagnosability. Real brokerage integration, authentication, authorisation, live market data and production financial accounting are outside the current scope.

The test basis comprises the project README, [API specification](../API.md), [architecture documentation](../ARCHITECTURE.md), financial accounting rules and component contracts. Existing tests provide baseline evidence and inform the identification of testing gaps.

## 2. Stakeholders

- **Portfolio managers and simulated traders:** require correct trade execution, accurate account balances and trustworthy portfolio valuation.
- **API client developers:** require predictable HTTP responses, reliable request validation and safe retries.
- **System operators and maintainers:** require transactional consistency, acceptable response times and sufficient diagnostic information.

These stakeholders represent the intended users and maintainers of the educational prototype.

## 3. Requirements

| ID | Required behaviour | Type | Stakeholder |
|---|---|---|---|
| R1 | A valid BUY or SELL shall update cash and holdings correctly. Trades exceeding available cash or holdings shall be rejected without changing portfolio state. | Functional: correctness and safety | Trader |
| R2 | Reusing an idempotency key with the same canonical trade request shall return the original trade without duplication. Conflicting reuse within the same portfolio shall be rejected. | Functional: robustness | API developer |
| R3 | Portfolio valuation shall correctly calculate market value, weighted-average cost, realised and unrealised P&L, and satisfy accounting reconciliation rules. | Functional: correctness | Portfolio manager |
| R4 | Valuation shall require prices for all open positions. Prices older than 24 hours, more than five minutes in the future, or missing timestamps shall be rejected. | Functional: safety | Portfolio manager |
| R5 | Failed transactions shall leave no committed trade records. Concurrent transactions shall preserve portfolio consistency and prevent duplicate execution. | Functional: data integrity | Operator |
| R6 | Invalid requests shall produce the documented HTTP error status and structured Problem Details, with field-level information for validation errors. | Functional: robustness; qualitative clarity | API developer |
| R7 | Under the defined local benchmark workload, portfolio valuation shall achieve a P95 response time of at most 200 ms. | Measurable performance | Trader |
| R8 | Request identifiers, diagnostic logs and trade metrics shall enable maintainers to trace requests and distinguish committed, rejected and replayed trades. | Qualitative diagnosability | Operator |

R7 is a proposed coursework requirement, rather than an existing production service-level agreement. Its operational envelope comprises a local Docker deployment, PostgreSQL 17, 100 historical trades, 10 open positions, fresh quotes and one sequential client. The target will be evaluated using three runs of 100 measured requests, each following 20 warm-up requests.

## 4. Requirement Levels and Testing Approaches

| ID | Requirement levels and test strategy |
|---|---|
| R1 | Unit: validate cash and holding boundaries using equivalence partitioning and boundary values. Integration: verify persistence using PostgreSQL. System: compare API responses against expected account state. |
| R2 | Unit: check request key and fingerprint behaviour. Integration: exercise sequential and concurrent requests. System: verify the 201, 200 and 409 HTTP contract. Use category-partition and finite-state models to select scenarios. |
| R3 | Unit: compare calculation results against independently derived expected values. Integration: verify consistent retrieval of trades and quotes. System: test the valuation endpoint using representative trading histories. Use functional, model-based and regression testing. |
| R4 | Unit: exercise the freshness interval boundaries with a fixed reference time. Integration and system: verify that missing and invalid prices cause the expected valuation failure. |
| R5 | Integration: use real PostgreSQL transactions, controlled concurrent requests and simulated failures to verify committed records and rollback. System: examine the resulting cash and holdings. |
| R6 | Unit: assess input constraints. Integration: exercise request parsing, validation and exception mapping using MockMvc. System: verify the contract against a running application. |
| R7 | System: measure end-to-end valuation latency using a defined workload, repeated measurements and percentile statistics. Component and database measurements may help identify bottlenecks. |
| R8 | Unit: verify correlation identifiers and counters. Integration: verify their behaviour across requests and transaction outcomes. Inspection: assess whether diagnostic information is sufficient for representative failure scenarios. |

## 5. Appropriateness and Limitations

Specification-based partitioning provides systematic coverage of important input classes and financial boundaries for R1 and R4. Category-partition and model-based approaches complement this by considering interactions and sequences for R2 and R3. Structural coverage will subsequently identify implementation branches that these tests miss.

Mocked unit tests provide efficient and repeatable feedback, but cannot establish database transaction or locking behaviour. PostgreSQL integration tests are therefore necessary for R2 and R5. Even these tests cannot exhaust all concurrent execution schedules.

Financial calculations require independently derived expected values to reduce the risk of reproducing implementation mistakes in test oracles. A potential ambiguity concerns the independent rounding of P&L components and their reconciliation; the intended rounding policy must be established before interpreting the corresponding regression test.

The performance target applies only within the defined operational envelope. Local measurements cannot establish production performance, availability or reliability. Similarly, checking correlation identifiers and metrics supports diagnosability, but does not establish that every operational failure can be diagnosed.

The approach combines partitioning, visibility and complementary forms of evidence, following the course principles in Young and Pezzè Chapters 2–4. The selected testing techniques draw on Chapters 5, 10–12, 14, 21 and 22.

## 6. References

- Software Testing Tutorial LO1: Requirements and testing strategies.
- Young and Pezzè, Chapters 2–4: Framework, Principles and Process.
- Young and Pezzè, Chapters 5, 10–12, 14, 21 and 22: Testing techniques and system testing.
- Mini Aladdin: README, API specification, architecture documentation and existing test suite.